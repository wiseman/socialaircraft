(ns lemondronor.socialaircraft.social
  (:require
   [cljs-http.client :as http]
   [cljs.core.async :refer [chan <! >! put! go close!]]
   [goog.object :as gobject]
   [lemondronor.socialaircraft.db :as db]
   [lemondronor.socialaircraft.generation :as generation]
   [lemondronor.socialaircraft.util :as util]
   ["generate-password" :as genpassword]
   ["mastodon-api" :as mastodon]
   ["oauth" :as oauth]
   ["puppeteer" :as puppeteer]
   ["readline" :as readline])
  (:require-macros [lemondronor.socialaircraft.logging :as logging]))

(logging/deflog "social" logger)


;; Creates an account and returns the password.

(defn create-account& [config username email]
  (info "Creating new user %s %s" username email)
  (let [ch (chan)]
    (go
      (let [url (get-in config [:mastodon :tootctl-url])
            response (<! (http/post url {:json-params
                                         ["accounts"
                                          "create"
                                          username
                                          "--email" email
                                          "--confirmed"]
                                         }))]
        (if (= (:status response) 200)
          (let [password-match (re-find #"(?m)^New password: (.+)$"
                                        (get-in response [:body :stdout]))]
            (if password-match
              (let [password (second password-match)]
                (info "Created user %s with password %s" username password)
                (put! ch {:username username :email :email :password password}))
              (throw
               (js/Error. "Could not parse password from response:" response))))
          (throw (js/Error. (str "Bad response from tootctl API:" response))))))
    ch))


(defn get-profile-photo-other& [icao reg]
  (let [ch (chan)]
    (go
      (put! ch :kaboom))))


(def ^:private bio-templates
  (map generation/parse-template
       ["I am {Reg}, a {Mdl} operated by {Op} in {Cou}."
        "I am {Reg}, a {Mdl} operated by {Op}."
        "I am {Reg}, a {Mdl} in {Cou}"
        "I am {Reg}, in {Cou}"
        "I am {Reg}, a {Mdl}."
        "I am {Reg}."]))


(defn bio-text [vrs-record]
  (generation/generate bio-templates vrs-record))


(defn browser-for-mastodon-user& [config email password]
  (info "Getting browser for user %s with password %s" email password)
  (let [ch (chan)
        browser_ (atom nil)
        page_ (atom nil)]
    (go
      (-> (.launch puppeteer #js {:headless false})
          (.catch (fn [err] (error "puppeteer error: %s" err)))
          (.then
           (fn [browser]
             (debug "Got browser")
             (reset! browser_ browser)
             (.newPage browser)))
          (.then
           (fn [page]
             (reset! page_ page)
             (let [url (js/URL. "/auth/sign_in"
                                (get-in config [:mastodon :base-url]))]
               (info "navigating to %s" url)
               (.goto page url))))
          (.then
           (fn [_]
             (.all js/Promise
                   [(.waitForNavigation @page_)
                    (.$eval @page_ "#user_email"
                            (fn [el email]
                              (set! (.-value el) email)) email)
                    (.$eval @page_ "#user_password"
                            (fn [el password]
                              (set! (.-value el) password)) password)
                    (.$eval @page_ "#new_user"
                            (fn [form]
                              (.submit form)))])))
          (.then
           (fn [_]
             (put! ch {:email email :browser @browser_})))))
    ch))


(defn upload-file [page selector file-path]
  (let [rel-path (util/relative-path file-path)]
    (-> (.$ page selector)
        (.then (fn [el]
                 (.uploadFile el rel-path))))))


(defn pup-set-value [el value] (set! (.-value el) value))

(defn pup-set-checked [el checked?] (set! (.-checked el) checked?))

(defn pup-upload-file [page selector file-path]
  (let [rel-path (util/relative-path file-path)]
    (-> (.$ page selector)
        (.then (fn [el]
                 (.uploadFile el rel-path))))))

(defn set-current-profile& [config browser profile]
  (info "Setting profile for user %s" profile)
  (let [ch (chan)]
    (go
      (let [url (js/URL. "/settings/profile"
                         (get-in config [:mastodon :base-url]))
            page_ (atom nil)]
        (info "Navigating to %s" url)
        (-> (.newPage browser)
            (.catch (fn [err] (error "puppeteer error: %s" err)))
            (.then (fn [page]
                     (reset! page_ page)
                     (.goto page url)))
            (.then (fn [_]
                     (.all
                      js/Promise
                      (conj
                       (cond-> [(.waitForNavigation @page_)]
                         (:display-name profile)
                         (conj
                          (.$eval @page_ "#account_display_name"
                                  pup-set-value (:display-name profile)))
                         (:bio profile)
                         (conj
                          (.$eval @page_ "#account_note"
                                  pup-set-value (:bio profile)))
                         (contains? profile :bot?)
                         (conj
                          (.$eval @page_ "#account_bot"
                                  pup-set-checked (:bot? profile)))
                         (contains? profile :locked?)
                         (conj
                          (.$eval @page_ "#account_locked"
                                  pup-set-checked (:locked? profile)))
                         (contains? profile :discoverable?)
                         (conj
                          (.$eval @page_ "#account_discoverable"
                                  pup-set-checked (:discoverable? profile)))
                         (:avatar profile)
                         (conj
                          (pup-upload-file
                           @page_ "#account_avatar" (:avatar profile)))
                         (:header profile)
                         (conj
                          (pup-upload-file
                           @page_ "#account_header" (:header profile))))
                       (.$eval @page_ "form" (fn [form] (.submit form)))))))
            (.then (fn [_]
                     (put! ch true))))))
    ch))


(defn authorize-app& [config browser]
  (info "Authorizing app for user")
  (let [ch (chan)]
    (go
      (let [url (js/URL. "/settings/applications/new"
                         (get-in config [:mastodon :base-url]))
            page_ (atom nil)]
        (info "Navigating to %s" url)
        (-> (.newPage browser)
            (.catch (fn [err] (error "puppeteer error: %s" err)))
            (.then (fn [page]
                     (reset! page_ page)
                     (.goto page url)))
            ;; Enter app info.
            (.then
             (fn [_]
               (.all
                js/Promise
                [(.waitForNavigation @page_)
                 (.$eval @page_ "#doorkeeper_application_name"
                         pup-set-value (config :app-name))
                 (.$eval @page_ "#doorkeeper_application_scopes_read"
                         pup-set-checked true)
                 (.$eval @page_ "#doorkeeper_application_scopes_write"
                         pup-set-checked true)
                 (.$eval @page_ "#doorkeeper_application_scopes_follow"
                         pup-set-checked true)
                 (.$eval @page_ "#doorkeeper_application_scopes_push"
                         pup-set-checked true)
                 (.$eval @page_ "form" (fn [form] (.submit form)))])))
            ;; Get tokens.
            (.then
             (fn [_]
               (.all
                js/Promise
                [(.waitForNavigation @page_)
                 (.$eval @page_ "td a"
                         (fn [el] (.click el)))])))
            (.then
             (fn [_]
               (.$$eval
                @page_ "td"
                (fn [els]
                  ;; This code is executed in the browser context, and
                  ;; hardly any clojurescript works. This code
                  ;; basically compiles to straight javascript that
                  ;; doesn't require any cljs support.
                  (let [a (js/Array.)]
                    (.push a (.-innerText (aget els 0)))
                    (.push a (.-innerText (aget els 1)))
                    (.push a (.-innerText (aget els 2)))
                    a)))))
            (.then
             (fn [codes]
               (let [res {:client-key (nth codes 0)
                          :client-secret (nth codes 1)
                          :access-token (nth codes 2)}]
                 (info "authentication tokens: %s" res)
                 (put! ch res)))))))
    ch))

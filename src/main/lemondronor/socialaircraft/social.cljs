(ns lemondronor.socialaircraft.social
  (:require
   [cljs.core.async :as async]
   [com.stuartsierra.component :as component]
   [lemondronor.socialaircraft.db :as db]
   ["generate-password" :as genpassword]
   ["mastodon-api" :as mastodon]
   ["oauth" :as oauth]
   ["puppeteer" :as puppeteer]
   ["readline" :as readline]))

(def oauth2 (.-OAuth2 oauth))

(def base-url "http://ubuntuservervm.local:4000/")
(def app-name "socialaircraft")
(def permissions "read write follow")

;; This is a combination of code form
;; https://github.com/vanita5/mastodon-api/blob/master/examples/authorization.js
;; and
;; https://github.com/jhayley/node-mastodon/wiki/Getting-an-access_token-with-the-oauth-package

(def ^:private oauth-redirect-uri "urn:ietf:wg:oauth:2.0:oob")


(defn get-app-authorization& []
  (let [chan (async/chan)]
    (-> mastodon
        ;; Get client ID and client secret.
        (.createOAuthApp (str base-url "api/v1/apps") app-name permissions)
        (.catch (fn [err] (println "createOAuthApp error:" err)))
        (.then (fn [response]
                 (println "Got createOAuthApp response" response)
                 ;; Get authorization URL.
                 (let [res (js->clj response :keywordize-keys true)
                       client-id (:client_id res)
                       client-secret (:client_secret res)
                       redirect-uri (:redirect_uri res)
                       oauth (oauth2. client-id client-secret
                                      base-url nil "/oauth/token")]
                   (println "client_id:" client-id "client_secret" client-secret)
                   (let [url (.getAuthorizeUrl oauth #js {"redirect_uri" oauth-redirect-uri
                                                          "response_type" "code"
                                                          "scope" permissions})]
                     (println "Authorization URL:" url)
                     (async/put! chan {:oauth oauth :url url}))))))
    chan))


(def browser_ (atom nil))

(defn get-browser& []
  (let [chan (async/chan)]
    (if @browser_
      (async/put! chan @browser_)
      (-> (.launch puppeteer #js {:headless true})
          (.catch (fn [err] (println "PUPPETEER ERROR:" err)))
          (.then (fn [browser]
                   (reset! browser_ browser)
                   (async/put! chan browser)))))
    chan))


(defn get-oauth-token& [user password]
  (println "Getting oauth token for user" user)
  (let [chan (async/chan)]
    (async/go
      (let [{:keys [oauth url]} (async/<! (get-app-authorization&))
            page_ (atom nil)
            browser (async/<! (get-browser&))]
        (-> (.newPage browser)
            (.catch (fn [err]
                      (println "puppeteer error:" err)))
            (.then (fn [page]
                     (reset! page_ page)
                     (.goto page url)))
            (.then (fn [res] (.click @page_ "#authorization_name")))
            (.then (fn [res] (-> @page_ .-keyboard (.type user))))
            (.then (fn [res] (.click @page_ "#authorization_password")))
            (.then (fn [res] (-> @page_ .-keyboard (.type password))))
            (.then (fn [res] (.all js/Promise
                                   [(.waitForNavigation @page_)
                                    (.click @page_ "form > button")])))
            (.then (fn [[res]]
                     (if (.ok res)
                       (do
                         (println "SUCCESS")
                         (.$ @page_ "h2"))
                       (println "OOPS" (.statusText res) (.text res)))))
            (.then (fn [el]
                     (.$eval @page_ "h2" (fn [el] (.-innerText el)))))
            (.then (fn [text]
                     (.close @page_)
                     (let [token (second (re-find #"Token code is (.+)" text))]
                       (println "holy shit" token)
                       (async/put! chan token)))))))
    chan))


;; But see
;; https://git.pleroma.social/pleroma/pleroma/blob/1825118fd46883cb2a9132b039925c160ad7e57b/test/web/oauth/oauth_controller_test.exs

(defn make-admin-client [config]
  (-> config clj->js mastodon.))


(defn create-new-account& [username config]
  (let [chan (async/chan)
        password (.generate genpassword #js {:length 20 :numbers true})]
    (println "Creating account" username "with password" password)
    (-> (make-admin-client config)
        (.post "api/pleroma/admin/user"
               #js {:nickname username
                    :email (str "jjwiseman+" username "@gmail.com")
                    :password password})
        (.catch (fn [err] (println "ERROR" err)))
        (.then (fn [res] (println "SUCCESS" res)
                 (async/put! chan password))))
    chan))

;; Need to create the new account and get an oath token.

(defn get-social-access-token [ac config])


(defn get-and-save-social-access-token [ac config]
  (let [token (get-social-access-token ac config)]
    ;;(db/record-social-access-token (:Icao ac) token)
    token))


(defn post [post ac db config]
  ;; (let [icao (:icao post)
  ;;       social-access-token (or (-> db icao :social-access-token)
  ;;                               (get-and-save-social-access-token ac config))
  ;;       client (get-client social-access-token)]
  ;;   (println "Posting" post "with" social-access-token)
  ;;   )
  )


(defrecord Mastodon [host admin-username admin-password app-name headless?
                     database
                     browser]
  component/Lifecycle
  (start [this]
    (if (and browser @browser)
      this
      (do
        (-> (.launch puppeteer #js {:headless headless?})
            (.catch (fn [err] (println "ERROR in puppeteer:" err)))
            (.then (fn [b]
                     (reset! browser b))))
        this))
    this))

(defn new-mastodon [config]
  (let [mastodon-config (:mastodon config)]
    (component/using
     (map->Mastodon {:host (:host mastodon-config)
                     :admin-username (:admin-username mastodon-config)
                     :admin-password (:admin-password mastodon-config)
                     :app-name (get mastodon-config :app-name "socialaircraft")
                     :headless? (get mastodon-config :headless? false)
                     :browser (atom nil)})
     {:database :database})))

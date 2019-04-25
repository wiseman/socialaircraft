(ns lemondronor.socialaircraft.social
  (:require
   [cljs-http.client :as http]
   [cljs.core.async :refer [chan <! put! go]]
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
  (let [ch (chan)]
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
                     (put! ch {:oauth oauth :url url}))))))
    ch))


(def browser_ (atom nil))

(defn get-browser& []
  (let [ch (chan)]
    (-> (.launch puppeteer #js {:headless false})
        (.catch (fn [err] (println "PUPPETEER ERROR:" err)))
        (.then (fn [browser]
                 (reset! browser_ browser)
                 (put! ch browser))))
    ch))


(defn browser-test []
  (println "WOO1")
  (go
    (println "WOO2")
    (let [b1 (<! (get-browser&))
          b2 (<! (get-browser&))]
      (println b1)
      (println b2))))

(defn get-oauth-token& [user password]
  (println "Getting oauth token for user" user)
  (let [ch (chan)]
    (go
      (let [{:keys [oauth url]} (<! (get-app-authorization&))
            page_ (atom nil)
            browser (<! (get-browser&))]
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
                       (put! ch token)))))))
    ch))


;; But see
;; https://git.pleroma.social/pleroma/pleroma/blob/1825118fd46883cb2a9132b039925c160ad7e57b/test/web/oauth/oauth_controller_test.exs

(defn make-admin-client [config]
  (-> config clj->js mastodon.))


(defn pleroma-create-new-account& [username config]
  (let [ch (chan)
        password (.generate genpassword #js {:length 20 :numbers true})]
    (println "Creating account" username "with password" password)
    (-> (make-admin-client config)
        (.post "api/pleroma/admin/user"
               #js {:nickname username
                    :email (str "jjwiseman+" username "@gmail.com")
                    :password password})
        (.catch (fn [err] (println "ERROR" err)))
        (.then (fn [res] (println "SUCCESS" res)
                 (put! ch password))))
    ch))

;; Need to create the new account and get an oath token.

(defn get-social-access-token [ac config])


(defn get-and-save-social-access-token [ac config]
  (let [token (get-social-access-token ac config)]
    (db/record-social-access-token (:Icao ac) token)
    token))


(defn post [post ac db config]
  ;; (let [icao (:icao post)
  ;;       social-access-token (or (-> db icao :social-access-token)
  ;;                               (get-and-save-social-access-token ac config))
  ;;       client (get-client social-access-token)]
  ;;   (println "Posting" post "with" social-access-token)
  ;;   )
  )


(defn create-account& [config username email]
  (println "Creating new user" username email)
  (let [ch (chan)]
    (go
      (let [url (get-in config [:mastodon :tootctl-url])
            response (<! (http/post url {:json-params
                                         ["accounts"
                                          "create"
                                          username
                                          "--email" email
                                          "--confirmed"
                                          ]
                                         }))]
        (if (= (:status response) 200)
          (let [password-match (re-find #"(?m)^New password: (.+)$" (get-in response [:body :stdout]))]
            (if password-match
              (put! ch password-match)
              (throw (js/Error. "Could not parse password from response:" response))))
          (throw (js/Error. (str "Bad response from tootctl API:" response))))))
    ch))

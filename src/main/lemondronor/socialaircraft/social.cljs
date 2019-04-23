(ns lemondronor.socialaircraft.social
  (:require
   [lemondronor.socialaircraft.db :as db]
   ["generate-password" :as genpassword]
   ["mastodon-api" :as mastodon]
   ["oauth" :as oauth]
   ["readline" :as readline]))

(def oauth2 (.-OAuth2 oauth))

(def base-url "http://ubuntuservervm.local:4000/api/v1/apps")

;; Taken from
;; https://github.com/vanita5/mastodon-api/blob/master/examples/authorization.js
;; Unfortunately I couldn't get it to work--The authorization URL
;; doesn't give me an access token.

;; (defn authorize []
;;   (-> mastodon
;;       (.createOAuthApp base-url "socialaircraft" "read write")
;;       (.catch (fn [err] (println "Error:" err)))
;;       (.then (fn [res]
;;                (println "Got result" res)
;;                (let [res (js->clj res)
;;                      client-secret (:client_secret res)
;;                      client-id (:client_id res)
;;                      redirect-uri (:redirect_uri res)]
;;                  (.getAuthorizationUrl mastodon client-id client-secret base-url "read write" redirect-uri))))
;;       (.then (fn [url]
;;                (println "Authorization URL:" url)))))

;; This does work. Taken from
;; https://github.com/jhayley/node-mastodon/wiki/Getting-an-access_token-with-the-oauth-package
;; (I got the client ID and client secret from the code above.)

(defn authorize []
  (let [oauth (oauth2. "JUglOqlDS_fFDdfFPEiXWVsca_SecHNbwsG_TZEvGZ8"
                       "WzYsdz7YpsdBv-5bAnX1z3KTMboGOkxogOfyIBzWCDU"
                       "http://ubuntuservervm.local:4000"
                       nil
                       "/oauth/token")]
    (println (.getAuthorizeUrl oauth #js {"redirect_uri" "urn:ietf:wg:oauth:2.0:oob"
                                          "response_type" "code"
                                          "scope" "read write follow"}))
    (let [rl (.createInterface readline #js {:input (.-stdin js/process)
                                             :output (.-stdout js/process)})]
      (.question rl "What is the code? "
                 (fn [answer]
                   (.getOAuthAccessToken
                    oauth
                    answer
                    #js {"grant_type" "authorization_code"
                         "redirect_uri" "urn:ietf:wg:oauth:2.0:oob"}
                    (fn [err access-token refresh-token res]
                      (println "err" err)
                      (println "access" access-token)
                      (println "refresh" refresh-token)
                      (println "res" res))))))))

(defn fetch-access-token []
  ()
  )


;; But see
;; https://git.pleroma.social/pleroma/pleroma/blob/1825118fd46883cb2a9132b039925c160ad7e57b/test/web/oauth/oauth_controller_test.exs

(defn make-admin-client [config]
  (-> config clj->js mastodon.))


(defn create-new-account [config ac]
  (println "the config" config)
  (let [nickname (:Reg ac)
        password (.generate genpassword #js {:length 20 :numbers true})]
    (println "Creating account" nickname "with password" password)
    (println
     (-> (make-admin-client config)
         (.post "api/pleroma/admin/user"
                #js {:nickname nickname :email (str "jjwiseman+" nickname "@gmail.com") :password password})
         (.catch (fn [err] (println "ERROR" err)))
         (.then (fn [res] (println "SUCCESS" res)))))))

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

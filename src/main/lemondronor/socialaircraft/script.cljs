(ns lemondronor.socialaircraft.script
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [cljs-http.client :as http]
   [cljs.core.async :refer [<!]]
   [cljs.reader :as edn]
   [clojure.string :as string]
   [goog.string :as gstring]
   [goog.string.format]
   [lemondronor.socialaircraft.db :as db]
   [lemondronor.socialaircraft.posts :as posts]
   [lemondronor.socialaircraft.social :as social]
   [lemondronor.socialaircraft.util :as util]
   ["ansi-diff-stream" :as differ]
   ["fs" :as fs]
   ["mekanize" :as mechanize]
   ["sqlite3" :as sqlite3]
   ["xhr2" :as xhr2]))

;; See https://github.com/r0man/cljs-http/issues/94#issuecomment-426442569
(set! js/XMLHttpRequest xhr2)

;; Hide the the "Discarding entity body for GET requests" warning that
;; xhr2 prints when used from cljs-http. See
;; https://github.com/r0man/cljs-http/issues/94#issuecomment-482755066
(set! js/console.warn (fn [& args]))

(def data-url "https://vrs.heavymeta.org/VirtualRadar/AircraftList.json")
(def data-fetch-interval-ms 1000)

(defn ac-short-desc [ac]
  (let [icao (:Icao ac)
        reg (:Reg ac)]
    (if reg
      (str "<" icao " " reg ">")
      (str "<" icao ">"))))


(defn get-flying-aircraft& []
  (println "Fetching aircraft from" data-url)
  (go
    (let [response (<! (http/get data-url))
          all-aircraft (-> response :body :acList)]
      (println "Fetched" (count all-aircraft) "aircraft from server.")
      (util/index-by :Icao all-aircraft))))>


(defn build-post
  "Creates an activity post data structure for an aircraft."
  [ac hist]
  ;; FIXME: dummy.
  {:type :post :icao (:Icao ac) :data ac :text (posts/weighted-rand-post ac)})


(defn make-post
  "Submits a post."
  [post]
  ;; FIXME: dummy.
  (println (gstring/format "Posting about %s: %s" (:icao post) (:text post)))
  (db/record-post (:icao post)))


(def posting-interval-ms (* 60 1000))


(defn annotate-ac-for-post [ac]
  (cond-> ac
    (= (:Reg ac) (:Call ac)) (dissoc :Call)))


(defn process-current-aircraft [ac db]
  ;;o(println "Processing" (ac-short-desc ac))
  (let [last-post-time (:last-post-time db)]
    (when (or (nil? last-post-time)
              (> (- (js/Date.) last-post-time) posting-interval-ms))
      (let [post (build-post (annotate-ac-for-post ac) db)]
        (make-post post)))))

(defn process-flying-aircraft [flying db]
  (doseq [[icao ac] flying]
    (process-current-aircraft ac (db icao))))

(defn process-stale-aircraft [flying])

(defn run []
  (println "Processing aircraft")
  (go
    (let [flying-ac (<! (get-flying-aircraft&))
          flying-ac-db (<! (db/get-aircraft& (keys flying-ac)))]
      (process-flying-aircraft flying-ac flying-ac-db)
      (process-stale-aircraft flying-ac)))
  (js/setTimeout run 5000))


(defn config-path []
  (or (first *command-line-args*)
      (-> js/process .-env .-MASTODON_BOT_CONFIG)
      "config.edn"))


(def new-mechanize-agent (.-newAgent mechanize))

(defn mechanize-test []
  (let [agent (new-mechanize-agent)]
    (println agent)
    (-> agent
        (.get #js {:uri "http://mastodon.local:3000/auth/sign_in"})
        (.catch (fn [err]
                  (println "ERROR" err)))
        (.then (fn [page]
                 (println "WOOpage" page)
                 (let [form (.form page 0)]
                   (println "WOOform" form)
                   (println "fields" (.-fields form))
                   (doseq [field (seq (.-fields form))]
                     (println "WOOfield" (.-name field) (.-value field)))
                   (println "WOOfield1" (.field form "user[email]"))
                   (println "WOOfield1" (.field form "user[password]"))
                   (.setFieldValue form "user[email]" "admin@mastodon.local")
                   (.setFieldValue form "user[password]" "mastodonadmin")
                   (doseq [field (seq (.-fields form))]
                     (println "WOOfield" (.-name field) (.-value field)))
                   (println "WOOfield2" (.field form "user[email]"))
                   (println "WOOfield2" (.field form "user[password]"))
                   (println "WOOnewform" form)
                   (.submit form
                            #js {"requestOptions" {"simple" false
                                                   "followRedirect" true
                                                   "followAllRedirects" true}}))))
        (.then (fn [page]
                 (println "WOOsecond page" page))))))


(defn main [& args]
  (mechanize-test)
  ;; (let [config (-> (config-path) (fs/readFileSync #js {:encoding "UTF-8"}) edn/read-string)]
  ;;   (go

  ;;     (let [admin-access-token (<! (social/get-oauth-token& "wiseman" "wiseman"))
  ;;           pleroma-config (-> config :pleroma (assoc :access_token admin-access-token))]
  ;;       (dotimes [n 10]
  ;;         (let [username (str "TESTUSER00" n)
  ;;               password (<! (social/create-new-account& username (:pleroma config)))
  ;;               oauth-token (<! (social/get-oauth-token& username password))]
  ;;           (println "Created token for user" username ":" oauth-token))))))
  ;;(social/get-oauth-token "wiseman" "wiseman")
  ;; (let [config (-> (config-path) (fs/readFileSync #js {:encoding "UTF-8"}) edn/read-string)]
  ;;   (println "CONFIG" config)
  ;;   (social/create-new-account (:pleroma config) {:Reg "TEST2"}))
  ;;(social/authorize)
  ;;(run)
  ;; Why isn't my node process exiting? (shadow-cljs dev mode starts a REPL,
  ;; that's why.)
  ;;(println (._getActiveHandles js/process))
  ;;(println (._getActiveRequests js/process))
  )

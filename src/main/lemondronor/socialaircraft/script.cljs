(ns lemondronor.socialaircraft.script
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as string]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [lemondronor.socialaircraft.db :as db]
            [lemondronor.socialaircraft.posts :as posts]
            [lemondronor.socialaircraft.util :as util]
            [goog.string :as gstring]
            [goog.string.format]
            ["ansi-diff-stream" :as differ]
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


(defn process-current-aircraft [ac history]
  ;;o(println "Processing" (ac-short-desc ac))
  (let [last-post-time (:last-post-time history)]
    (when (or (nil? last-post-time)
              (> (- (js/Date.) last-post-time) posting-interval-ms))
      (let [post (build-post (annotate-ac-for-post ac) history)]
        (make-post post)))))

(defn process-flying-aircraft [flying history]
  (doseq [[icao ac] flying]
    (process-current-aircraft ac (history icao))))

(defn process-stale-aircraft [flying])

(defn run []
  (println "Processing aircraft")
  (go
    (let [flying-ac (<! (get-flying-aircraft&))
          flying-ac-history (<! (db/get-aircraft& (keys flying-ac)))]
      (process-flying-aircraft flying-ac flying-ac-history)
      (process-stale-aircraft flying-ac)))
  (js/setTimeout run 5000))


(defn main [& args]
  (run)
  ;; Why isn't my node process exiting? (shadow-cljs dev mode starts a REPL,
  ;; that's why.)
  ;;(println (._getActiveHandles js/process))
  ;;(println (._getActiveRequests js/process))
  )

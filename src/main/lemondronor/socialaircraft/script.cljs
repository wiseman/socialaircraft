(ns lemondronor.socialaircraft.script
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as string]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [lemondronor.socialaircraft.db :as db]
            [lemondronor.socialaircraft.util :as util]
            [goog.string :as gstring]
            [goog.string.format]
            ["ansi-diff-stream" :as differ]
            ["sqlite3" :as sqlite3]
            ["xhr2" :as xhr2]))

;; See https://github.com/r0man/cljs-http/issues/94#issuecomment-426442569
(set! js/XMLHttpRequest xhr2)
(set! js/console.warn (fn [& args]))

;;(def diff (differ))
;;(.pipe diff js/process.stdout)

(def data-url "https://vrs.heavymeta.org/VirtualRadar/AircraftList.json")
(def data-fetch-interval-ms 1000)

(defn ac-short-desc [ac]
  (let [icao (:Icao ac)
        reg (:Reg ac)]
    (if reg
      (str "<" icao " " reg ">")
      (str "<" icao ">"))))

(defn format-record [rec]
  (gstring/format
   "%6s %8s %6.1f %5d %5.1f %7.2f %7.2f"
   (rec :Icao)
   (rec :Reg "[unk]")
   (rec :Spd -1.0)
   (rec :Alt -1)
   (rec :Trak -1)
   (rec :Lat 0)
   (rec :Long 0)))

(defn format-field [row field-spec]
  (if (coll? field-spec)
    (let [[field fmt] field-spec]
      (if-let [val (row field)]
        (gstring/format fmt val)
        ""))
    (row field-spec "")))

(defn format-row [row template fields]
  (apply gstring/format template (map #(format-field row %) fields)))

(def table-template "%6s %8s %6s %5s %5s %7s %7s")

(defn format-aircraft-record [rec]
  (format-row
   rec
   table-template
   [:Icao :Reg [:Spd "%6.1f"] [:Alt "%5d"] [:Trak "%5.1f"] [:Lat "%7.2f"] [:Long "%7.2f"]]))

(defn table-header []
  (gstring/format table-template "ICAO" "REG" "SPD" "ALT" "HDG" "LAT" "LON"))

(defn fetch-and-process-data []
  (go (let [response (<! (http/get data-url))
            all-aircraft (-> response :body :acList)
            report-lines (->> all-aircraft
                              (take 50)
                              (map format-aircraft-record))
            report-text (string/join "\n" (conj report-lines (table-header)))]
        ;;(.write diff report-text)
        )))


(defn process-loop []
  (println "Process loop")
  (fetch-and-process-data)
  (js/setInterval process-loop data-fetch-interval-ms))


(defn get-flying-aircraft& []
  (println "Fetching aircraft from" data-url)
  (go
    (let [response (<! (http/get data-url))
          all-aircraft (-> response :body :acList)]
      (println "Fetched" (count all-aircraft) "aircraft from server.")
      (util/index-by :Icao all-aircraft))))>


(defn build-post [ac hist]
  {:type :post :icao (:Icao ac) :data ac})


(defn make-post [post]
  (println (gstring/format "Posting about %s" (:icao post)))
  (db/record-post (:icao post)))


(def posting-interval-ms (* 10 1000))

(defn process-current-aircraft [ac history]
  ;;o(println "Processing" (ac-short-desc ac))
  (let [last-post-time (:last-post-time history)]
    (when (or (nil? last-post-time)
              (> (- (js/Date.) last-post-time) posting-interval-ms))
      (let [post (build-post ac history)]
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
  (cond (and (= (count args) 1) (= (first args) "init-db"))
        (db/init-db)
        (and (= (count args) 1) (= (first args) "run"))
        (run)
        :else
        (println "Available commands: init-db"))
  ;;(println (._getActiveHandles js/process))
  ;;(println (._getActiveRequests js/process))
  )

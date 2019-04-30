(ns lemondronor.socialaircraft.util
  (:require
   [cljs-http.client :as http]
   [cljs.core.async :refer [chan <! >! put! close!] :as async]
   [goog.string :as gstring]
   [goog.string.format]
   ["fs" :as fs]
   ["path" :as path]
   ["request" :as request]
   ["tmp" :as tmp]
   ["winston" :as winston]))

(let [createLogger (.-createLogger winston)
      format (.-format winston)
      transports (.-transports winston)
      printf-fmt #(gstring/format "%s%-7s %-6s/%-18s| %s"
                                  (.-timestamp %)
                                  (.-ms %)
                                  (.-service %)
                                  (.-level %)
                                  (.-message %))]
  (def logger (createLogger
               #js {:level "debug"
                    :format (.combine
                             format
                             (.colorize format #js {:all true})
                             (.timestamp format #js {:format "YYYYMMDD HHmmss"})
                             (.errors format #js {:stack true})
                             (.splat format)
                             (.timestamp format)
                             (.label format)
                             (.ms format)
                             (.json format))
                    :defaultMeta #js {}}))
  (.add logger (new (.-Console transports)
                    #js {:format (.combine format
                                           (.printf format printf-fmt))})))


(defn get-logger [service]
  (.child logger #js {:service service}))


(defn index-by
  "Returns a map that indexes a given collection."
  [key-fn coll]
  (reduce (fn [m item]
            (assoc m (key-fn item) item))
          {}
          coll))


;; See
;; https://stackoverflow.com/questions/14464011/idiomatic-clojure-for-picking-between-random-weighted-choices

(defn weighted-rand
  "given a vector of slice sizes, returns the index of a slice given a
  random spin of a roulette wheel with compartments proportional to
  slices."
  [slices]
  (let [total (reduce + slices)
        r (rand total)]
    (loop [i 0 sum 0]
      (if (< r (+ (slices i) sum))
        i
        (recur (inc i) (+ (slices i) sum))))))


(defn weighted-rand-nth
  "given a vector of slice sizes, returns the index of a slice given a
  random spin of a roulette wheel with compartments proportional to
  slices."
  ([coll]
   (weighted-rand-nth coll identity))
  ([coll keyfn]
   (->> coll
        (map keyfn)
        weighted-rand
        (nth coll))))


(defn bearing-to [loc1 loc2]
  (let [radians (fn [deg] (* deg (/ Math/PI 180.0)))
        degrees (fn [rad] (* rad (/ 180.0 Math/PI)))]
    (let [lat1 (radians ((:lat loc1)))
          lon1 (radians (:long loc1))
          lat2 (radians (:lat loc2))
          lon2 (radians (:long loc2))]
      (degrees (mod (Math/atan (* (Math/sin (- lon2 lon1)) (Math/cos lat2))
                               (- (* (Math/cos lat1) (Math/sin lat2))
                                  (* (Math/sin lat1)
                                     (Math/cos lat2)
                                     (Math/cos (- lon2 lon1)))))
                    (* 2 Math/pi))))))


(def cardinal-directions
  [["N" 0]
   ["NE" 45]
   ["E" 90]
   ["SE" 135]
   ["S" 180]
   ["SW" 225]
   ["W" 270]
   ["NW" 315]
   ;; Need another "N" to capture e.g. 350.
   ["N" 360]])

(defn bearing->direction [bearing]
  (let [errors (mapv (fn [[dir brg]]
                       [(Math/abs (- bearing brg)) dir])
                     cardinal-directions)]
    (second (first (sort-by first errors)))))


(defn relative-path
  ([path]
   (relative-path (.cwd js/process) path))
  ([from to]
   (path/relative from to)))


;; cljs-http is kinda sucky. See https://github.com/r0man/cljs-http/issues/121
;; TODO: Replace cljs-http.

(defn http-get&
  ([url]
   (http-get& url {}))
  ([url options]
   (let [ch (chan)]
     (async/go
       (let [options (clj->js (merge {:uri url
                                      :simple false
                                      :resolveWithFullResponse true}
                                     options))]
         (.get request
               options
               (fn [err, response, body]
                 (put! ch body)))))
     ch)))


;; Downloads the contents of a URL to a temp file.

(defn download-url& [url]
  (let [ch (chan)]
    (async/go
      ;; Need :encoding nil to preserve binary data.
      (let [response (<! (http-get& url {:encoding nil}))]
        (let [tmpfile (tmp/fileSync)
              fd (.-fd tmpfile)]
          (fs/writeSync fd response)
          (fs/closeSync fd)
          (>! ch (.-name tmpfile)))))
    ch))

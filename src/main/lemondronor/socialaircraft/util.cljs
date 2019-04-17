(ns lemondronor.socialaircraft.util)


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


(defn bearing-to [loc1 loc2]
  (let [radians (fn [deg] (* deg (/ Math/PI 180.0)))
        degrees (fn [rad] (* rad (/ 180.0 Math/PI)))]
    (let [lat1 (radians ((:lat loc1)))
          lon1 (radians (:long loc1))
          lat2 (radians (:lat loc2))
          lon2 (radians (:long loc2))]
      (degrees (mod (Math/atan (* (Math/sin (- lon2 lon1)) (Math/cos lat2))
                               (- (* (Math/cos lat1) (Math/sin lat2))
                                  (* (Math/sin lat1) (Math/cos lat2) (Math/cos (- lon2 lon1)))))
                    (* 2 Math/pi))))))


(def cardinal-directions
  [["N"  0]
   ["NE" 45]
   ["E"  90]
   ["SE" 135]
   ["S"  180]
   ["SW" 225]
   ["W"  270]
   ["NW" 315]])

;; (defn bearing-to-direction [bearing]
;;   (let [errors (mapv (fn [cd]
;;                        [(Math/abs (- bearing (nth % 1))) (first cd)])
;;                      cardinal-directions)]
;;     (first (first (sort-by first errors)))))

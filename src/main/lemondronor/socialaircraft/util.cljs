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

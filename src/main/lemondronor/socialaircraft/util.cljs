(ns lemondronor.socialaircraft.util)


(defn index-by [key-fn coll]
  (reduce (fn [m item]
            (assoc m (key-fn item) item))
          {}
          coll))

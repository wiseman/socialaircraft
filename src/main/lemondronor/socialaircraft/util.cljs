(ns lemondronor.socialaircraft.util)


(defn index-by
  "Returns a map that indexes a given collection."
  [key-fn coll]
  (reduce (fn [m item]
            (assoc m (key-fn item) item))
          {}
          coll))

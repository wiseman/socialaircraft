(ns lemondronor.socialaircraft.logging)

(defmacro deflog [service name]
  `(do
     (def ~name (util/get-logger ~service))
     (let [log# (.bind (.-log ~name) ~name)]
       (defn ~'debug [& args#] (apply log# "debug" args#))
       (defn ~'warn [& args#] (apply log# "warn" args#))
       (defn ~'info [& args#] (apply log# "info" args#))
       (defn ~'error [& args#] (apply log# "error" args#)))))

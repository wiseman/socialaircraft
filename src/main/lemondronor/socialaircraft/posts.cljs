(ns lemondronor.socialaircraft.posts
  (:require
            [clojure.pprint :as pprint]
            [clojure.set :as set]
            [lemondronor.socialaircraft.generation :as generation]))


;; (defn score-template [template-vars data-fields]
;;   (let [expected (set/intersection (set template-vars) data-fields)
;;         missing (set/difference (set template-vars) data-fields)]
;;     (if (empty? missing)
;;       (count expected)
;;       -1000)))


;; (defn choose-template [data]
;;   (let [data-fields (set (keys data))
;;         scored-templates (reverse
;;                           (sort-by
;;                            first
;;                            (map (fn [[template-vars templates]]
;;                                   [(score-template template-vars data-fields) templates])
;;                                 all-templates)))]
;;     (doseq [t scored-templates]
;;       (println t))
;;     (let [best-template (rand-nth (second (first scored-templates)))]
;;       (template/render best-template data))))



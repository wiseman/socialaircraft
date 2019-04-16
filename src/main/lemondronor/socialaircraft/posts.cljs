(ns lemondronor.socialaircraft.posts
  (:require
            [clojure.pprint :as pprint]
            [clojure.set :as set]
            [clojure.string :as string]
            [lemondronor.socialaircraft.generation :as generation]
            [lemondronor.socialaircraft.util :as util]))


(def templates
  [(str "[{Reg} here.|{Reg} is on the move.|Looking for {Reg}?] "
        "?:[I'm on the way to {To} from {From}. ]"
        "?:[Just cruising along at {Spd} knots, {Alt} feet. |"
        "Doing {Spd} knots at {Alt} feet. |"
        "{Alt} feet, {Spd} knots. ]"
        "?:[Current course {Trak} degrees. |Heading {Trak} degrees. ]"
        "?:[Squawking {Sqk}. ]")
   "Aircraft with unknown registration, ICAO {Icao} is present."])

(def parsed-templates (map generation/parse-template templates))


(defn generate-all [data template]
  (let [scored-fragments (generation/score-fragments
                          (generation/generate-fragments
                           template
                           data))]
    (map (fn [[score frag]]
           {:score score :text (string/trim (:text frag))})
         scored-fragments)))

(defn weighted-rand-post [data]
  (let [scored-templates (apply concat (map #(generate-all data %) parsed-templates))
        winning-index (util/weighted-rand (mapv :score scored-templates))
        winning-text (:text (nth scored-templates winning-index))]
    (println "Chose template with score" (:score (nth scored-templates winning-index)))
    winning-text))

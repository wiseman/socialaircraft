(ns lemondronor.socialaircraft.posts
  (:require
            [clojure.pprint :as pprint]
            [clojure.set :as set]
            [clojure.string :as string]
            [lemondronor.socialaircraft.generation :as generation]
            [lemondronor.socialaircraft.util :as util]))


(def templates
  [(str "[{Reg} here.|{Reg} is on the move.|{Reg} is in the air.] "
        "?:[Callsign {Call}. |Callsign is {Call}. | Using callsign {Call}. ]"
        "?:[I'm on the way to {To} from {From}. ]"
        "?:[Just cruising along at {Spd} knots, {Alt} feet. |"
        "   Doing {Spd} knots at {Alt} feet. |"
        "   {Alt} feet, {Spd} knots. ]"
        "?:[Current course {Trak} degrees. |Heading {Trak} degrees. ]"
        "?:[Squawking {Sqk}. ]")
   "Aircraft with unknown registration, ICAO {Icao} is present."])

(def parsed-templates (map generation/parse-template templates))


(defn weighted-rand-post [data]
  (let [expansions (generation/generate-all parsed-templates data)]
    (:text (util/weighted-rand-nth expansions :score))))

(defn best-post [data]
  (:text (generation/generate parsed-templates data)))

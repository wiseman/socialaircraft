(ns lemondronor.socialaircraft.util-test
  (:require [cljs.test :refer (deftest is)]
            [lemondronor.socialaircraft.util :as util]))


(deftest bearing->direction
  (is (= "E"
         (util/bearing->direction 90)))
  (is (= "E"
         (util/bearing->direction 100)))
  (is (= "N"
         (util/bearing->direction 350)))
  (is (= "NW"
         (util/bearing->direction 320)))
  )

(deftest relative-path
  (is (= "../../real/b"
         (util/relative-path "/data/jjw/test/a" "/data/jjw/real/b"))))

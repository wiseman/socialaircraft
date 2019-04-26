(ns lemondronor.socialaircraft.util-test
  (:require [cljs.test :refer (deftest is)]
            [lemondronor.socialaircraft.util :as util]))


(deftest relative-path
  (is (= "../../real/b"
         (util/relative-path "/data/jjw/test/a" "/data/jjw/real/b"))))

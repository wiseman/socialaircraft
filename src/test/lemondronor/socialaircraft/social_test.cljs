(ns lemondronor.socialaircraft.social-test
  (:require [cljs.test :refer (deftest is)]
            [lemondronor.socialaircraft.social :as social]))


(deftest bio-text
  (is (= "I am N420LE."
         (social/bio-text {:Reg "N420LE"})))
    (is (= "I am N420LE, a MD 520."
           (social/bio-text {:Reg "N420LE" :Mdl "MD 520"})))
    (is (= "I am N420LE, a MD 520 operated by LAPD."
           (social/bio-text {:Reg "N420LE" :Mdl "MD 520" :Op "LAPD"})))
    (is (= "I am N420LE, a MD 520 operated by LAPD in U.S.."
         (social/bio-text {:Reg "N420LE" :Mdl "MD 520" :Op "LAPD" :Cou "U.S."} ))))

(ns lemondronor.socialaircraft.generation-test
  (:require [cljs.test :refer (deftest is)]
            [lemondronor.socialaircraft.generation :as generation]))

(deftest parse-template
  (is (= [:optional [:varref "woo"]]
         (generation/parse-template "?:{woo}")))
  (is (= [:sequence [:text "Hello "] [:varref "foo"] [:text "!"]]
         (generation/parse-template "Hello {foo}!")))
  (is (= [:sequence
          [:text "Hi "] [:varref "name"] [:text ", "]
          [:optional [:varref "woo"]]
          [:text " "]
          [:choice
           [:text "What up?"]
           [:text "Seeya"]]]
         (generation/parse-template "Hi {name}, ?:{woo} [What up?|Seeya]")))
  (is (= [:sequence
          [:text "Hi "] [:varref "name"] [:text ", "]
          [:optional
           [:sequence [:varref "woo"] [:text " "]]]
          [:choice [:text "What up?"] [:text "Seeya"]]]
         (generation/parse-template "Hi {name}, ?:[{woo} ][What up?|Seeya]"))))

(deftest generate
  (is (= ["Hello!"]
         (generation/generate-all
          [(generation/parse-template "Hello!")]
          {})))
    (is (= "Hello!"
         (generation/generate
          [(generation/parse-template "Hello!")]
          {}))))

(deftest generate-test
  (is (= [{:varrefs [], :text ""} {:varrefs [], :text "woo"}]
         (generation/expand
          (generation/parse-template "?:woo"))))
  (is (= [{:varrefs [], :text ""} {:varrefs [], :text "woo bar"}]
         (generation/expand
          (generation/parse-template "?:[woo bar]"))))
  (is (= [{:varrefs [], :text ""} {:varrefs [:woo], :text "WOO"}]
         (generation/expand
          (generation/parse-template "?:{woo}")
          {:woo "WOO"})))
  (is (= ["Hi Tim, What up?"
          "Hi Tim, Seeya"
          "Hi Tim, WOO! What up?"
          "Hi Tim, WOO! Seeya"]
         (map :text
              (generation/expand
               (generation/parse-template "Hi {name}, ?:[{woo} ][What up?|Seeya]")
               {:name "Tim" :woo "WOO!"})))))

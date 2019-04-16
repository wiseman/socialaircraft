(ns lemondronor.socialaircraft.generation
  (:require [clojure.math.combinatorics :as combo]
            [clojure.string :as string]
            [instaparse.core :as insta]))

(def %parse-template
  (insta/parser
   "<pattern> = term | implicit-sequence
    implicit-sequence = term (term)+
    <term> = sequence | optional | choice | varref | text
    <no-ws-term> = sequence | optional | choice | varref | no-ws-text
    sequence = <'['> term+ <']'>
    optional = <'⁇'> no-ws-term
    choice = <'['> pattern <'|'> pattern (<'|'> pattern)* <']'>
    varref = <'{'> #'\\w+' <'}'>
    text = #'[^\\{\\[\\]\\|⁇]+'
    no-ws-text = #'[^\\{\\[\\]\\|⁇\\s]+'
"))


(defn lex-template [template]
  (string/replace template "?:" "⁇"))

(defn parse-template [template]
  (let [result (-> template lex-template %parse-template)
        ;; Convert :implicit-sequence into :sequence and :no-ws-text
        ;; into :text.
        xformed-result (insta/transform
                        {:implicit-sequence (fn [& children]
                                              (into [:sequence] children))
                         :no-ws-text (fn [text]
                                       [:text text])}
                        result)]
    ;; Put in the implicit :sequence if necessary.
    (if (= (count xformed-result) 1)
      (first xformed-result)
      (into [:sequence] xformed-result))))


;; (pprint/pprint
;;  (parser
;;   (str "Hey it's {Reg} here. ✈ ✈️?:[Just flying to {To} from {From}.] "
;;        "?:[[Currently cruising at {Spd} knots at {Alt} feet.]|"
;;        "[Currently cruising at {Spd} knots.]|"
;;        "[Currently cruising at {Alt} feet.]]")))

(defmulti generate-fragments (fn [template data]
                               (first template)))

(defmethod generate-fragments :varref [template data]
  (let [var (keyword (second template))]
    (if (contains? data var)
      (list {:varrefs [var]
             :text (str (data var))})
      '())))

(defmethod generate-fragments :text [template data]
  (list {:varrefs []
         :text (second template)}))

(defmethod generate-fragments :optional [template data]
  (concat (list
           {:varrefs []
            :text ""})
          (generate-fragments (second template) data)))

(defmethod generate-fragments :choice [template data]
  (apply concat (map #(generate-fragments % data) (rest template))))

(defmethod generate-fragments :sequence [template data]
  (let [merge-expansions1 (fn
                            ([a]
                             a)
                            ([a b]
                             {:varrefs (concat (:varrefs a) (:varrefs b))
                              :text (str (:text a) (:text b))}))
        merge-expansions (fn [args]
                           (reduce merge-expansions1 args))
        things (map #(generate-fragments % data) (rest template))
        chains (apply combo/cartesian-product things)]
    (map merge-expansions chains)))


(defn generate [template data]
  (let [results (generate-fragments template data)
        best-result (first results)]
    best-result))

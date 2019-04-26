(ns lemondronor.socialaircraft.generation
  (:require [clojure.math.combinatorics :as combo]
            [clojure.string :as string]
            [instaparse.core :as insta]))

(def ^:private %parse-template
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


;; Because I don't know enough to write the correct negative-lookahead
;; regex so that "?:" doesn't get parsed into a `text` or
;; `no-ws-text`, I lex it into a unicode character that I'm sure no
;; one will ever use (right?).

(defn ^:private lex-template [template]
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


(defmulti expand (fn [template data] (first template)))

(defmethod expand :varref [template data]
  (let [var (keyword (second template))
        val (data var)]
    (if (or (nil? val) (= val ""))
      '()
      (list {:varrefs [var] :text (str (data var))}))))

(defmethod expand :text [template data]
  (list {:varrefs [] :text (second template)}))

(defmethod expand :optional [template data]
  (concat (list {:varrefs [] :text ""})
          (expand (second template) data)))

(defmethod expand :choice [template data]
  (apply concat (map #(expand % data) (rest template))))

(defmethod expand :sequence [template data]
  (let [merge-expansions1 (fn
                            ([a] a)
                            ([a b] {:varrefs (concat (:varrefs a) (:varrefs b))
                                    :text (str (:text a) (:text b))}))
        merge-expansions (fn [args]
                           (reduce merge-expansions1 args))
        things (map #(expand % data) (rest template))
        chains (apply combo/cartesian-product things)]
    (map merge-expansions chains)))


;; A simple expansion scorer that gives high scores to expansions that
;; used more variables.

(defn score-by-varref-count [expansion]
  (assoc expansion :score (count (:varrefs expansion))))


(defn generate-all
  ([templates data]
   (generate-all templates data {}))
  ([templates data options]
   (->> (apply concat (map #(expand % data) templates))
        (map (get options :scorer score-by-varref-count))
        (sort-by :score)
        reverse
        (map :text))))


(defn generate
  ([templates data]
   (generate templates data {}))
  ([templates data options]
   (-> (generate-all templates data options)
       first)))

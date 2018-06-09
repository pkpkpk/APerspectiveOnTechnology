(ns cube.core
  (:require [cljs.compiler :as comp]
            [cljs.env :as env]
            [cljs.analyzer :as ana]
            [markdown.core :refer [md-to-html-string]]))

(defn slurp-markdown [path] (md-to-html-string (slurp path)))

(defonce ak (delay (slurp-markdown "AlanKay.md")))
(defonce bv (delay (slurp-markdown "BretVictor.md")))
(defonce de (delay (slurp-markdown "DouglasEngelbart.md")))
(defonce jl (delay (slurp-markdown "JaronLanier.md")))
(defonce tn (delay (slurp-markdown "TedNelson.md")))
(defonce vb (delay (slurp-markdown "VannevarBush.md")))

; "EWDijkstra.md"

(defmacro data []
  [{:label "Brett Victor"
    :key :top
    :html @bv
    #_ #_ :styles {:backgroundColor "red"}}
   {:label "Douglas Engelbart"
    :key :bottom
    :html @de
    #_ #_ :styles {:backgroundColor "#ccc" :color "#777"}}
   {:label "Jaron Lanier"
    :key :left
    :html @jl
    #_ #_ :styles {:backgroundColor "#0cf" :color "#666"}}
   {:key :back
    :label "Ted Nelson"
    :html @tn
    #_ #_ :styles {:backgroundColor "#f0c" :color "#777"}}
   {:key :right
    :html @vb
    :label "Vannevar Bush"
    #_ #_ :styles {:backgroundColor "#c0f" :color "#777"}}
   {:key :front
    :label "Alan Kay"
    :html @ak
    #_ #_  :styles {:backgroundColor "#fc0" :color "#777"}}])

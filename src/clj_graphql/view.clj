(ns clj-graphql.view
  (:use seesaw.core)
  (:use seesaw.font)
  (:use clojure.java.browse)
  (:require [seesaw.table :as table]))

(defn subscription->table-row
  [subscription]
  {:id  (:id subscription)
   :url (:url subscription)})

(defn feed-item->table-row
  [feed-item]
  {:link  (str (get-in feed-item [:item :link]))
   :title (str (get-in feed-item [:item :title]))})

(defn to-table
  [{:keys [columns rows selection-fn]}]
  (let [table (table :model [:columns columns
                             :rows rows])]
    (listen table :selection #(when (.getValueIsAdjusting %)
                                (selection-fn (table/value-at table (selection table)))))
    (config! table :font (font :size 13))
    (scrollable table :column-header columns)))

(defn display
  [title content width height]
  (let [window (frame :title title
                      :width width
                      :height height
                      :content content)]
    (-> window
        show!)))

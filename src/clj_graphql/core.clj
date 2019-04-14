(ns clj-graphql.core
  (:use seesaw.core)
  (:use clojure.java.browse)
  (:require [clojure.java.io :as io]
            [com.walmartlabs.lacinia.util :as util]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia :as lacinia]
            [clj-http.client :as client]
            [clojure.edn :as edn]
            [cheshire.core :refer :all]
            [clojure.walk :as walk]
            [seesaw.table :as table])
  (:import (clojure.lang IPersistentMap)))

; === utils

(defn http-get-json
  [url query-params-map]
  (let [filtered-params (into {} (filter val query-params-map))]
    (-> (client/get url {:query-params filtered-params})
        :body
        (parse-string true))))

(defn paginate
  [{:keys [starting-after limit-requested get-result-fn default-limit]}]
  (->> (loop [result []
              starting-after-iteration starting-after]
         (let [{has-more :has_more
                data     :data} (get-result-fn starting-after-iteration default-limit)]
           (let [result-iteration (into result data)]
             (if (and has-more (< (count result-iteration) limit-requested))
               (recur result-iteration (get (last data) :id))
               result-iteration))))
       (take limit-requested)))

(defn simplify
  [m]
  (walk/postwalk
    (fn [node]
      (cond
        (instance? IPersistentMap node)
        (into {} node)

        (seq? node)
        (vec node)

        :else
        node))
    m))

; === resolvers

(defn by-subscription-id
  [context args value]
  (let [id (:id args)
        limit (:limit args)
        starting-after (:starting_after args)]
    (paginate {:starting-after  starting-after
               :limit-requested limit
               :get-result-fn   (fn [starting-after limit]
                                  (http-get-json
                                    (str "http://localhost:3000/subscriptions/" id "/feed_items")
                                    {"starting_after" starting-after "limit" limit}))
               :default-limit   20})))

(defn subscription-by-id
  [context args feed-item]
  (http-get-json
    (str "http://localhost:3000/subscriptions/" (:subscription_id feed-item)) {}))

(defn resolvers
  []
  {:FeedItem/subscription    (fn [context args feed-item]
                               (subscription-by-id context args feed-item))
   :query/by-subscription-id (fn [context args value]
                               (by-subscription-id context args value))})

; === init

(defn load-schema
  []
  (-> (io/resource "rss.edn")
      slurp
      edn/read-string
      (util/attach-resolvers (resolvers))
      (schema/compile)))

(def schema (load-schema))

(defn q
  [query-string]
  (-> (lacinia/execute schema query-string nil nil)
      simplify))

; === view

(defn to-table
  [content]
  (let [columns [:url :link :title :author]
        rows (->> content
                  (map (fn [feed-item]
                         {:url    (str (get-in feed-item [:subscription :url]))
                          :link   (str (get-in feed-item [:item :link]))
                          :title  (str (get-in feed-item [:item :title]))
                          :author (str (get-in feed-item [:item :author]))})))
        table (table :model [:columns columns
                             :rows rows])]
    (listen table :selection (fn [e]
                               (when (.getValueIsAdjusting e)
                                 (browse-url (:link (table/value-at table (selection table {:multi? false})))))))
    (scrollable table :column-header columns)))

(defn display
  [title content width height]
  (let [window (frame :title title
                      :width width
                      :height height
                      :content content)]
    (-> window
        show!)))

(defn view-q
  [query-string]
  (let [data (get-in (q query-string) [:data :by_subscription_id])
        table (to-table data)]
    (display "rss-feed" table 1920 500)))
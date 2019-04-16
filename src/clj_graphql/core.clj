(ns clj-graphql.core
  (:use seesaw.core)
  (:use clojure.java.browse)
  (:require [clojure.java.io :as io]
            [com.walmartlabs.lacinia.util :as util]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia :as lacinia]
            [clojure.edn :as edn]
            [clojure.walk :as walk]
            [clj-graphql.resolvers :as resolvers]
            [clj-graphql.view :as view])
  (:import (clojure.lang IPersistentMap)))

; === utils

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

(defn resolvers
  []
  {:FeedItem/subscription               (fn [context args feed-item]
                                          (resolvers/subscription-by-id context args feed-item))
   :query/feed_items_by_subscription_id (fn [context args value]
                                          (resolvers/by-subscription-id context args value))
   :query/all_subscriptions             (fn [context args value]
                                          (resolvers/all-subscriptions context args value))})

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

(defn feed-items-by-subscription-id
  [subscription-id]
  (let [query (str "{
                      feed_items_by_subscription_id(limit: 500 id: \"" subscription-id "\") {
                        insert_date
                        subscription {
                          url
                          insert_date
                          enabled
                        }
                        item {
                          author
                          title
                          link
                        }
                    }}")
        data (get-in (q query) [:data :feed_items_by_subscription_id])
        columns [:link :title]
        table (view/to-table {:columns      columns
                              :rows         (->> data (map view/feed-item->table-row))
                              :selection-fn #(browse-url (:link %))})]
    (view/display "rss-feed-items" table 1920 500)))

(defn subscriptions
  []
  (let [query "{
                 all_subscriptions {
                   id
                   url
                 }
               }"
        data (get-in (q query) [:data :all_subscriptions])
        columns [:id :url]
        table (view/to-table {:columns      columns
                              :rows         (->> data (map view/subscription->table-row))
                              :selection-fn #(feed-items-by-subscription-id (:id %))})]
    (view/display "subscriptions" table 500 500)))
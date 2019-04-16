(ns clj-graphql.resolvers
  (:require [clj-graphql.http :as http]))

(defn by-subscription-id
  [context args value]
  (let [id (:id args)
        limit (:limit args)
        starting-after (:starting_after args)]
    (http/paginate {:starting-after  starting-after
                    :limit-requested limit
                    :get-result-fn   (fn [starting-after limit]
                                       (http/http-get-json
                                         (str "http://localhost:3000/subscriptions/" id "/feed_items")
                                         {"starting_after" starting-after "limit" limit}))
                    :default-limit   20})))

(defn subscription-by-id
  [_ _ feed-item]
  (let [url (str "http://localhost:3000/subscriptions/" (:subscription_id feed-item))
        query-params {}]
    (http/http-get-json url query-params)))

(defn all-subscriptions
  [_ _ _]
  (let [url "http://localhost:3000/subscriptions"
        query-params {}]
    (http/http-get-json url query-params)))

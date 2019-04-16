(ns clj-graphql.http
  (:require [clj-http.client :as client]
            [cheshire.core :refer :all]))

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

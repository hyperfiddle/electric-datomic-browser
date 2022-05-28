(ns app.queries
  (:require [datomic.client.api :as d])
  #?(:cljs (:require-macros app.queries)))

; Observation: when I change something here, the app doesn't get reloaded

(defn conn []
  (-> (d/client {:server-type :dev-local
                 :system      "datomic-samples"})
      (d/connect {:db-name "mbrainz-subset"})))

(defn db []
  (-> (conn)
      (d/db)))

(defn last-transactions [n]
  (->> (d/q '[:find (pull ?tx [*])
              :where [?tx :db/txInstant]] (db))
       (map first)
       (sort-by :db/txInstant)
       (reverse)
       (take n)))

(comment
  (last-transactions 10))

(defn prepare-attributes [q-result]
  (->> q-result
       (map first)
       (map #(update % :db/valueType :db/ident))
       (map #(update % :db/cardinality :db/ident))
       (map #(update % :db/unique :db/ident))))

(defn identifying-attributes []
  (prepare-attributes
    (d/q '[:find (pull ?e [*])
           :where
           [?e :db/valueType _]
           [?e :db/cardinality _]
           [?e :db/unique :db.unique/identity]]
         (db))))

(comment
  (identifying-attributes))

(defn normal-attributes []
  (prepare-attributes
    (d/q '[:find (pull ?e [*])
           :where
           [?e :db/valueType _]
           [?e :db/cardinality _]
           (not [?e :db/unique :db.unique/identity])]
         (db))))

(comment
  (normal-attributes))
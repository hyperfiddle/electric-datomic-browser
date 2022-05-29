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

(defn process-pull [q-result]
  (->> q-result
       (map first)
       (map (fn [m]
              (update-vals
                m
                (fn [v]
                  (cond
                    (and (map? v) (contains? v :db/ident)) (:db/ident v)
                    (and (map? v) (contains? v :db/id)) (:db/id v)
                    :else v)))))))

(defn identifying-attributes []
  (process-pull
    (d/q '[:find (pull ?e [*])
           :where
           [?e :db/valueType _]
           [?e :db/cardinality _]
           [?e :db/unique :db.unique/identity]]
         (db))))

(comment
  (identifying-attributes))

(defn normal-attributes []
  (process-pull
    (d/q '[:find (pull ?e [*])
           :where
           [?e :db/valueType _]
           [?e :db/cardinality _]
           (not [?e :db/unique :db.unique/identity])]
         (db))))

(comment
  (normal-attributes))

(defn entity-details [e]
  (process-pull [[(d/pull (db) '[*] e)]]))

(comment
  (entity-details 1))

(defn tx-overview [tx]
  (let [tx-datoms (-> (conn)
                      (d/tx-range {:start tx
                                   :end   (inc tx)})
                      (first)
                      (:data))
        ref-attr? (->> (db)
                       (d/q '[:find ?e :where [?e :db/valueType :db.type/ref]])
                       (map first)
                       (set))
        tx-id?    (set (concat (map #(nth % 0) tx-datoms)
                               (map #(nth % 1) tx-datoms)
                               (->> tx-datoms
                                    (filter #(ref-attr? (nth % 1)))
                                    (map #(nth % 2)))))
        id->ident (into {} (d/q '[:find ?e ?v
                                  :in $ ?tx-id?
                                  :where
                                  [?e :db/ident ?v]
                                  [(contains? ?tx-id? ?e)]]
                                (db)
                                tx-id?))]
    (->> tx-datoms
         (map (fn [[e a v]]
                (-> {:e        (get id->ident e e)
                     :a        (get id->ident a a)
                     :v-ref    (when (ref-attr? a) (get id->ident v v))
                     :v-scalar (when (not (ref-attr? a)) v)}))))))

(comment
  (time (tx-overview 13194139534022)))
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

(defn ident->id [db ident]
  (-> (d/datoms db {:index :avet :components [:db/ident ident]})
      (first)
      (nth 0)))

(comment
  (time (ident->id (db) :db/ident)))

(defn entity-details [eid-or-eident]
  (process-pull [[(d/pull (db) '[*] eid-or-eident)]]))

(comment
  (entity-details 1)
  (entity-details :db/ident))

(defn ref-attr-set [db]
  (->> db
       (d/q '[:find ?e :where [?e :db/valueType :db.type/ref]])
       (map first)
       (set)))

(defn id->ident-map [db datoms]
  (let [ref-attr? (ref-attr-set db)
        tx-id?    (set (concat (map #(nth % 0) datoms)
                               (map #(nth % 1) datoms)
                               (->> datoms
                                    (filter #(ref-attr? (nth % 1)))
                                    (map #(nth % 2)))))]
    (into {} (d/q '[:find ?e ?v
                    :in $ ?tx-id?
                    :where
                    [?e :db/ident ?v]
                    [(contains? ?tx-id? ?e)]]
                  db
                  tx-id?))))

(defn tx-overview [txid n]
  (let [conn      (conn)
        db        (d/db conn)
        tx-datoms (-> conn
                      (d/tx-range {:start txid
                                   :end   (inc txid)})
                      (first)
                      (:data))
        ref-attr? (ref-attr-set db)
        id->ident (id->ident-map db tx-datoms)]
    (->> tx-datoms
         (sort-by #(nth % 0))
         (take n)
         (map (fn [[e a v t]]
                {:e        (get id->ident e e)
                 :a        (get id->ident a a)
                 :v-ref    (when (ref-attr? a) (get id->ident v v))
                 :v-scalar (when (not (ref-attr? a)) v)
                 :tx        t})))))

(comment
  (time (tx-overview 13194139534022 200)))

(defn a-overview [aid-or-aident n]
  (let [db        (db)
        aid       (if (keyword? aid-or-aident)
                    (ident->id db aid-or-aident)
                    aid-or-aident)
        ref-attr? (contains? (ref-attr-set db) aid)
        a-datoms  (take n (d/datoms db {:index      :aevt
                                        :components [aid]}))
        id->ident (id->ident-map db a-datoms)]
    (->> a-datoms
         (sort-by #(nth % 0))
         (map (fn [[e a v t]]
                {:e                              (get id->ident e e)
                 :a                              (get id->ident a a)
                 (if ref-attr? :v-ref :v-scalar) (if ref-attr? (get id->ident v v) v)
                 :tx                              t})))))

(comment
  (time (a-overview 10 200))
  (time (a-overview :db/ident 200)))
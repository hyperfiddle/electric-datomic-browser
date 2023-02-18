(ns app.queries
  (:import [hyperfiddle.electric Failure Pending])
  (:require [datomic.client.api.async :as d]
            [hyperfiddle.electric :as e]
            [hyperfiddle.rcf :refer [tests ! % with]]
            [missionary.core :as m]))

(defn query
  "Return a task running a datomic query asynchronously, and completing
  when all streamed results have been collected into a vector."
  [query & args]
  (e/chan->task (d/q {:query query, :args (vec args)})))

(comment (m/? (query '[:find (pull ?tx [:db/id :db/txInstant])
                       :where [?tx :db/txInstant]]
                     user/db)))

(comment
  (def it ((e/task->cp (query '[:find (pull ?tx [:db/id :db/txInstant])
                              :where [?tx :db/txInstant]]
                            user/db))
           #(prn ::ready) #(prn ::done)))
  @it := Pending
  @it := [...]
  (it))

(defn paginate [limit page xs] (->> xs (drop (* limit page)) (take limit)))

(defn transactions [db pull-pattern limit page]
  (m/sp (->> (m/? (query '[:find (pull ?tx pattern)
                           :in $ pattern
                           :where [?tx :db/txInstant]]
                         db pull-pattern))
             (map first)
             (sort-by :db/txInstant)
             reverse
             (paginate limit page))))

(comment (time (m/? (transactions user/db [:db/id] 3 0))))

(defn attributes [db pull-pattern limit page]
  (m/sp (->> (m/? (query '[:find (pull ?e pattern)
                           :in $ pattern
                           :where [?e :db/valueType _]]
                         db pull-pattern))
             (map first)
             (sort-by :db/ident)
             (paginate limit page))))

(comment (time (m/? (attributes user/db [:db/ident] 3 0))))

(defn tx-datoms [conn txid limit page]
  ; https://docs.datomic.com/client-api/datomic.client.api.async.html#var-tx-range
  (m/sp (->> (e/chan->ap (d/tx-range conn {:start txid, :end (inc txid)}))
             (m/eduction (take 1)) ; terminate flow after 1 tx
             (m/eduction (map :data))
             (m/reduce into [])
             m/?
             (paginate limit page))))

(comment (time (m/? (tx-datoms user/datomic-conn 13194139534022 3 0))))

(defn render-datoms [db !datoms]
  (m/sp (let [datoms (m/? !datoms) ; ?
              ref-attr? (->> (m/? (query '[:find ?e :where [?e :db/valueType :db.type/ref]] db))
                             (map first)
                             (set))
              datoms-id? (set (concat (map #(nth % 0) datoms)
                                      (map #(nth % 1) datoms)
                                      (->> datoms
                                           (filter #(ref-attr? (nth % 1)))
                                           (map #(nth % 2)))))
              id->ident (into {} (m/? (query '[:find ?e ?v
                                               :in $ ?datoms-id?
                                               :where
                                               [?e :db/ident ?v]
                                               [(contains? ?datoms-id? ?e)]]
                                             db
                                             datoms-id?)))]
          (->> datoms (map (fn [[e a v t]]
                             {:e (get id->ident e e)
                              :a (get id->ident a a)
                              :v (get id->ident v v)
                              :tx t}))))))

(comment
  (def !datoms (tx-datoms user/datomic-conn 13194139534022 3 0))
  (time (m/? (render-datoms user/db !datoms))))

(defn tx-overview [conn txid limit page]
  (tx-datoms conn txid limit page))

(comment
  (time (m/? (tx-overview user/datomic-conn 13194139534018 3 0)))
  (time (m/? (render-datoms user/db (tx-overview user/datomic-conn 13194139534018 3 0)))))

(defn entity-details [db e limit page]
  (m/sp (->> (m/? (e/chan->task (d/datoms db {:index :eavt, :components [e]})))
             (paginate limit page))))

(comment
  (m/? (entity-details user/db 1 3 0))
  (m/? (entity-details user/db :db/ident 3 0))
  (m/? (render-datoms user/db (entity-details user/db :db/ident 3 0))))

(defn a-overview [db a limit page]
  (m/sp (->> (m/? (e/chan->task (d/datoms db {:index :aevt, :components [a]})))
             (paginate limit page))))

(comment
  (m/? (a-overview user/db :db/ident 3 0))
  (m/? (render-datoms user/db (a-overview user/db :db/ident 3 0))))

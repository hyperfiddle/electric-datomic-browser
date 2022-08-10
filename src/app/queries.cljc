(ns app.queries
  (:require #?(:clj [datomic.client.api.async :as d])
            [missionary.core :as m]
            [hyperfiddle.photon :as p]
            [hyperfiddle.rcf :refer [tests ! % with]]))

; Hack - we use CLJC here only to provide stubs for Photon compiler, see https://github.com/hyperfiddle/photon/issues/13

(defn query
  "Return a task running a datomic query asynchronously, and completing
  when all streamed results have been collected into a vector."
  [query & args]
  #?(:clj (m/reduce into [] (p/chan->flow (d/q {:query query, :args (vec args)})))))

(def tx-attrs [:db/id :db/txInstant])
(def a-attrs [:db/id :db/ident :db/cardinality :db/unique :db/fulltext :db/isComponent
              :db/tupleType :db/tupleTypes :db/tupleAttrs :db/valueType :db/doc])

(defn nav-tx-overview [txid] {:route :tx-overview :param txid})
(defn nav-a-overview [aid] {:route :a-overview :param aid})
(defn nav-e-details [eid] {:route :e-details :param eid})
(defn possibly-nav-ref [v]
  (if (map? v)
    (cond
      (:db/ident v) (nav-e-details (:db/ident v))
      (:db/id v) (nav-e-details (:db/id v))
      :else v)
    v))

(defn transactions [db limit page]
  ;; We produce a flow which will run a query, transform its result, emit it and terminate.
  #?(:clj
     (->> (m/ap                                             ; return a discrete flow
            (->> (query                                     ; build a task running the query
                   '[:find (pull ?tx pattern)
                     :in $ pattern
                     :where [?tx :db/txInstant]]
                   db tx-attrs)
                 (m/?)                                      ; run it, wait for it to succeed and produce a result
                 ; transform result
                 (map first)
                 (sort-by :db/txInstant)
                 (map #(update % :db/id nav-tx-overview))
                 (reverse)
                 (drop (* limit page))
                 (take limit)))
          ;; A discrete flow does not have an initial value. It is undefined until it produces a first value.
          ;; A UI is continuous, meaning it's always defined. There is no such thing as an "in between two states" UI.
          ;; Either it's visible on screen, or it's not a UI.
          ;; We must give this discreet flow an initial value for the UI to render something. We choose `nil`.
          ;; {} is pronounced "discard", given two arguments, it ignores the left one and return the right one.
          ;; Our UI will therefore display nil then the query result will arrive, replace nil, and the UI will rerender.
          (m/reductions {} nil))))

(comment
  (m/? (m/reduce {} nil (transactions (d/db user/datomic-conn) 5 0))))

(defn attributes [db limit page]
  #?(:clj
     (->> (m/ap (->> (m/? (query '[:find (pull ?e pattern)
                                   :in $ pattern
                                   :where
                                   [?e :db/valueType _]]
                                 db
                                 a-attrs))
                     (map first)
                     (sort-by :db/ident)
                     (map #(update % :db/id nav-a-overview))
                     (map #(update-vals % possibly-nav-ref))
                     (drop (* limit page))
                     (take limit)))
          (m/reductions {} nil))))

(comment
  (time (m/? (m/reduce {} nil (attributes (d/db user/datomic-conn) 10 0))))
  )

(defn resolve-datoms [db datoms limit page]
  #?(:clj
     (m/sp (let [ref-attr? (->> (m/? (query '[:find ?e :where [?e :db/valueType :db.type/ref]] db))
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
             (->> datoms
                  (drop (* limit page))
                  (take limit)
                  (map (fn [[e a v t]]
                         {:e  (nav-e-details (get id->ident e e))
                          :a  (nav-a-overview (get id->ident a a))
                          :v  (if (ref-attr? a)
                                (possibly-nav-ref {:db/id v :db/ident (get id->ident v v)})
                                v)
                          :tx (nav-tx-overview t)})))))))

(defn tx-overview [conn db txid limit page]
  #?(:clj
     (->> (m/ap (let [tx-datoms (->> (d/tx-range conn {:start txid, :end (inc txid)})
                                     (p/chan->flow)
                                     (m/eduction (take 1))  ; flow will terminate after 1 tx is received
                                     (m/?<)                 ; park until flow produces a value
                                     (:data))]
                  (m/? (resolve-datoms db tx-datoms limit page))))
          (m/reductions {} nil))))                          ; UI need an initial value to display until query produces its first result

(comment
  (time (m/? (m/reduce {} nil (tx-overview user/datomic-conn (d/db user/datomic-conn) 13194139534022 200 0)))))

(defn entity-details [db eid-or-eident limit page]
  #?(:clj
     (->> (m/ap (let [datoms (m/? (->> (d/datoms db {:index      :eavt
                                                     :components [eid-or-eident]})
                                       (p/chan->flow)
                                       (m/reduce into [])))]
                  (m/? (resolve-datoms db datoms limit page))))
          (m/reductions {} nil))))

(comment
  (m/? (m/reduce {} nil (entity-details (d/db user/datomic-conn) 1 100 0)))
  (m/? (m/reduce {} nil (entity-details (d/db user/datomic-conn) :db/ident 100 0))))

(defn a-overview [db aid-or-ident limit page]
  #?(:clj
     (->> (m/ap (let [a-datoms (m/? (->> (d/datoms db {:index      :aevt
                                                       :components [aid-or-ident]})
                                         (p/chan->flow)
                                         (m/reduce into [])))]
                  (m/? (resolve-datoms db a-datoms limit page))))
          (m/reductions {} nil))))                          ; UI need an initial value to display until query produces its first result

(comment
  (time (m/? (m/reduce {} nil (a-overview (d/db user/datomic-conn) 10 200 0))))
  (time (m/? (m/reduce {} nil (a-overview (d/db user/datomic-conn) :db/ident 200 0)))))

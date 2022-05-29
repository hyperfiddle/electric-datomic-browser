(ns app.core
  (:require #?(:clj [datomic.client.api :as d])
            [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [hyperfiddle.zero :as z]
            [missionary.core :as m])
  (:import (hyperfiddle.photon Pending)
           #?(:clj (hyperfiddle.photon_impl.runtime Failure)))
  #?(:cljs (:require-macros app.core)))                     ; forces shadow hot reload to also reload JVM at the same time

(defn wrap
  "run slow blocking fn on a threadpool"
  [f & args]
  #?(:clj (->> (m/ap (m/? (m/via m/cpu (time (apply f args)))))
               (m/reductions {} (Failure. (Pending.))))))

(def tx-count 5)
(def data-rows 100)

(def nav-home {:route :home :param tx-count})
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

(def !nav-state #?(:cljs (atom nav-home)))
(p/def nav-state (p/watch !nav-state))

(def tx-attrs [:db/id :db/txInstant])
(def a-attrs [:db/id :db/ident :db/cardinality :db/unique :db/fulltext :db/isComponent
              :db/tupleType :db/tupleTypes :db/tupleAttrs :db/valueType :db/doc])
(def conn #?(:clj (-> (d/client {:server-type :dev-local
                                 :system      "datomic-samples"})
                      (d/connect {:db-name "mbrainz-subset"}))))

(p/defn Button [label watch F]
  (let [event (dom/button
                (dom/text label)
                (dom/attribute "type" "button")
                (->> (dom/events dom/parent "click")
                     (z/impulse watch)))]
    (when event
      (F. event))))

(p/defn Link [label nav-data]
  (Button.
    label
    nav-state
    (p/fn [_]
      (reset! !nav-state nav-data))))

(p/defn Cell [v]
  (dom/td
    (if (and (map? v) (contains? v :route) (contains? v :param))
      (Link. (:param v) v)
      (dom/text v))))

(p/defn DataViewer [title keys maps]
  (let [ks keys]
    (dom/h1 (dom/text title))
    (dom/table
      (dom/thead
        (dom/for [k ks]
          (dom/td
            (dom/text k))))
      (dom/tbody
        (dom/for [m maps]
          (dom/tr
            (dom/for [k ks]
              (Cell. (m k)))))))))

(defn transactions [n]
  #?(:clj (->> (d/q '[:find (pull ?tx pattern)
                      :in $ pattern
                      :where [?tx :db/txInstant]]
                    (d/db conn)
                    tx-attrs)
               (map first)
               (sort-by :db/txInstant)
               (map #(update % :db/id nav-tx-overview))
               (reverse)
               (take n))))

(comment
  (transactions 5))

(defn attributes [n]
  #?(:clj (->> (d/q '[:find (pull ?e pattern)
                      :in $ pattern
                      :where
                      [?e :db/valueType _]]
                    (d/db conn)
                    a-attrs)
               (map first)
               (sort-by :db/ident)
               (map #(update % :db/id nav-a-overview))
               (map #(update-vals % possibly-nav-ref))
               (take n))))

(p/defn HomeScreen [tx-count]
  (DataViewer.
    "Transactions"
    tx-attrs
    ~@(new (wrap transactions tx-count)))
  (DataViewer.
    "Attributes"
    a-attrs
    ~@(new (wrap attributes 20))))

(defn ref-attr-set [db]
  #?(:clj (->> db
               (d/q '[:find ?e :where [?e :db/valueType :db.type/ref]])
               (map first)
               (set))))

(defn id->ident-map [db datoms]
  #?(:clj (let [ref-attr?  (ref-attr-set db)
                datoms-id? (set (concat (map #(nth % 0) datoms)
                                        (map #(nth % 1) datoms)
                                        (->> datoms
                                             (filter #(ref-attr? (nth % 1)))
                                             (map #(nth % 2)))))]
            (into {} (d/q '[:find ?e ?v
                            :in $ ?datoms-id?
                            :where
                            [?e :db/ident ?v]
                            [(contains? ?datoms-id? ?e)]]
                          db
                          datoms-id?)))))

(defn resolve-datoms [db datoms n]
  #?(:clj (let [ref-attr? (ref-attr-set db)
                id->ident (id->ident-map db datoms)]
            (->> datoms
                 (take n)
                 (map (fn [[e a v t]]
                        {:e  (nav-e-details (get id->ident e e))
                         :a  (nav-a-overview (get id->ident a a))
                         :v  (if (ref-attr? a)
                               (possibly-nav-ref {:db/id v :db/ident (get id->ident v v)})
                               v)
                         :tx (nav-tx-overview t)}))))))

(defn entity-details [eid-or-eident n]
  #?(:clj (let [db     (d/db conn)
                datoms (d/datoms db {:index      :eavt
                                     :components [eid-or-eident]})]
            (resolve-datoms db datoms n))))

(comment
  (entity-details 1 100)
  (entity-details :db/ident 100))

(p/defn EntityDetailsScreen [eid]
  (DataViewer.
    (str "Entity Details: " eid)
    [:e :a :v :tx]
    ~@(new (wrap entity-details eid data-rows))))

(defn tx-overview [txid n]
  #?(:clj (let [db        (d/db conn)
                tx-datoms (:data (first (d/tx-range conn {:start txid
                                                          :end   (inc txid)})))]
            (resolve-datoms db tx-datoms n))))

(comment
  (time (tx-overview 13194139534022 200)))

(p/defn TransactionOverviewScreen [txid]
  (DataViewer.
    (str "Transaction Overview: " txid)
    [:e :a :v :tx]
    ~@(new (wrap tx-overview txid data-rows))))

(defn a-overview [aid-or-ident n]
  #?(:clj (let [db       (d/db conn)
                a-datoms (d/datoms db {:index      :aevt
                                       :components [aid-or-ident]})]
            (resolve-datoms db a-datoms n))))

(comment
  (time (a-overview 10 200))
  (time (a-overview :db/ident 200)))

(p/defn AttributeOverviewScreen [aid]
  (DataViewer.
    (str "Attribute Overview: " aid)
    [:e :a :v :tx]
    ~@(new (wrap a-overview aid data-rows))))

(p/defn App []
  (let [{:keys [route param]} nav-state]
    (dom/div
      (when (not (= route :home))
        (Link. "Home" nav-home))
      (condp = route
        :home (HomeScreen. param)
        :e-details (EntityDetailsScreen. param)
        :tx-overview (TransactionOverviewScreen. param)
        :a-overview (AttributeOverviewScreen. param)))))

(def app
  #?(:cljs
     (p/client
       (p/main
         (binding [dom/parent (dom/by-id "root")]
           (try
             (App.)
             (catch Pending _)))))))

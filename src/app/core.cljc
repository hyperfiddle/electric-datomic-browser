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

(def initial-nav-state {:route ::home :params tx-count})

(def !nav-state #?(:cljs (atom initial-nav-state)))
(p/def nav-state (p/watch !nav-state))

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

(p/defn DataViewer [title keys maps key-routes]
  (let [ks keys]
    (dom/h1 (dom/text title))
    (dom/table
      (dom/thead
        (dom/for [k ks]
          (dom/td (dom/text k))))
      (dom/tbody
        (dom/for [m maps]
          (dom/tr
            (dom/for [k ks]
              (dom/td
                (if (and (contains? key-routes k) (m k))
                  (Link. (m k) {:route  (key-routes k)
                                :params (m k)})
                  (dom/text (m k)))))))))))

(defn transactions [n]
  #?(:clj (->> (d/q '[:find (pull ?tx [*])
                      :where [?tx :db/txInstant]]
                    (d/db conn))
               (map first)
               (sort-by :db/txInstant)
               (reverse)
               (take n))))

(defn attributes [attrs]
  #?(:clj (->> (d/q '[:find (pull ?e pattern)
                      :in $ pattern
                      :where
                      [?e :db/valueType _]
                      [?e :db/cardinality _]]
                    (d/db conn)
                    attrs)
               (map first)
               (map (fn [m]
                      (update-vals
                        m
                        (fn [v]
                          (cond
                            (and (map? v) (contains? v :db/ident)) (:db/ident v)
                            (and (map? v) (contains? v :db/id)) (:db/id v)
                            :else v))))))))

(p/defn HomeScreen [tx-count]
  (DataViewer.
    "Transactions"
    [:db/id :db/txInstant]
    ~@(new (wrap transactions tx-count))
    {:db/id ::tx-overview})
  (let [attrs [:db/id :db/ident :db/cardinality :db/unique :db/fulltext :db/isComponent
               :db/tupleType :db/tupleTypes :db/tupleAttrs :db/valueType :db/doc]]
    (DataViewer.
      "Attributes"
      attrs
      ~@(new (wrap attributes attrs))
      {:db/id ::a-overview}
      Link)))

(defn entity-details [eid-or-eident]
  #?(:clj (->> (d/pull (d/db conn) '[*] eid-or-eident)
               (map (fn [[k v]] {:a k :v v})))))

(comment
  (entity-details 1)
  (entity-details :db/ident))

(p/defn EntityDetailsScreen [eid]
  (DataViewer.
    (str "Entity Details: " eid)
    [:a :v]
    ~@(new (wrap entity-details eid))
    {:a ::a-overview}))

(defn ref-attr-set [db]
  #?(:clj (->> db
               (d/q '[:find ?e :where [?e :db/valueType :db.type/ref]])
               (map first)
               (set))))

(defn id->ident-map [db datoms]
  #?(:clj (let [ref-attr? (ref-attr-set db)
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
                          tx-id?)))))

(defn tx-overview [txid n]
  #?(:clj (let [db        (d/db conn)
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
                         :tx       t}))))))

(comment
  (time (tx-overview 13194139534022 200)))

(p/defn TransactionOverviewScreen [txid]
  (DataViewer.
    (str "Transaction Overview: " txid)
    [:e :a :v-ref :v-scalar :tx]
    ~@(new (wrap tx-overview txid data-rows))
    {:e     ::e-details
     :a     ::a-overview
     :v-ref ::e-details}))

(defn ident->id [db ident]
  #?(:clj (-> (d/datoms db {:index :avet :components [:db/ident ident]})
              (first)
              (nth 0))))

(comment
  (time (ident->id (d/db conn) :db/ident)))

(defn a-overview [aid-or-aident n]
  #?(:clj (let [db        (d/db conn)
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
                         :tx                             t}))))))

(comment
  (time (a-overview 10 200))
  (time (a-overview :db/ident 200)))

(p/defn AttributeOverviewScreen [aid]
  (DataViewer.
    (str "Attribute Overview: " aid)
    [:e :a :v-ref :v-scalar :tx]
    ~@(new (wrap a-overview aid data-rows))
    {:e     ::e-details
     :v-ref ::e-details
     :tx    ::tx-overview}))

(p/defn App []
  (let [{:keys [route params]} nav-state]
    (dom/div
      (when (not (= route ::home))
        (Link. "Home" initial-nav-state))
      (condp = route
        ::home (HomeScreen. params)
        ::e-details (EntityDetailsScreen. params)
        ::tx-overview (TransactionOverviewScreen. params)
        ::a-overview (AttributeOverviewScreen. params)))))

(def app
  #?(:cljs
     (p/client
       (p/main
         (binding [dom/parent (dom/by-id "root")]
           (try
             (App.)
             (catch Pending _)))))))

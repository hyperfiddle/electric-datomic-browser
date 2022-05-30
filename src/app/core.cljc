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

(def tx-count 10)
(def as-count 20)
(def data-rows 100)

(def nav-home {:route :home :page 0})
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

(def !history #?(:cljs (atom (list nav-home))))
(p/def history (p/watch !history))

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
    history
    (p/fn [_]
      (swap! !history conj (assoc nav-data :page 0)))))

(p/defn BackButton []
  (Button.
    "Back"
    history
    (p/fn [_]
      (swap! !history rest))))

(defn paging [[f & r] amount]
  (conj r (update f :page + amount)))

(p/defn Pagination [label amount]
  (Button.
    label
    history
    (p/fn [_]
      (swap! !history paging amount))))

(p/defn Cell [v]
  (dom/td
    (if (and (map? v) (contains? v :route) (contains? v :param))
      (Link. (:param v) v)
      (dom/text v))))

(p/defn DataViewer [title keys maps]
  (dom/div
    (dom/h1 (dom/text title))
    (Pagination. "previous page" -1)
    (dom/text (str " page " (:page (first history)) " "))
    (Pagination. "next page" 1)
    (dom/table
      (dom/thead
        (dom/for [k keys]
          (dom/td
            (dom/text k))))
      (dom/tbody
        (dom/for [m maps]
          (dom/tr
            (dom/for [k keys]
              (Cell. (m k)))))))))

(defn transactions [n p]
  #?(:clj (->> (d/q '[:find (pull ?tx pattern)
                      :in $ pattern
                      :where [?tx :db/txInstant]]
                    (d/db conn)
                    tx-attrs)
               (map first)
               (sort-by :db/txInstant)
               (map #(update % :db/id nav-tx-overview))
               (reverse)
               (drop (* n p))
               (take n))))

(comment
  (transactions 5 0))

(defn attributes [n p]
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
               (drop (* n p))
               (take n))))

(p/defn HomeScreen [page]
  (dom/div
    (DataViewer.
      "Transactions"
      tx-attrs
      ~@(new (wrap transactions tx-count page)))
    (DataViewer.
      "Attributes"
      a-attrs
      ~@(new (wrap attributes as-count page)))))


(defn resolve-datoms [db datoms n p]
  #?(:clj (let [ref-attr?  (->> (d/q '[:find ?e :where [?e :db/valueType :db.type/ref]] db)
                                (map first)
                                (set))
                datoms-id? (set (concat (map #(nth % 0) datoms)
                                        (map #(nth % 1) datoms)
                                        (->> datoms
                                             (filter #(ref-attr? (nth % 1)))
                                             (map #(nth % 2)))))
                id->ident  (into {} (d/q '[:find ?e ?v
                                           :in $ ?datoms-id?
                                           :where
                                           [?e :db/ident ?v]
                                           [(contains? ?datoms-id? ?e)]]
                                         db
                                         datoms-id?))]
            (->> datoms
                 (drop (* n p))
                 (take n)
                 (map (fn [[e a v t]]
                        {:e  (nav-e-details (get id->ident e e))
                         :a  (nav-a-overview (get id->ident a a))
                         :v  (if (ref-attr? a)
                               (possibly-nav-ref {:db/id v :db/ident (get id->ident v v)})
                               v)
                         :tx (nav-tx-overview t)}))))))


(defn entity-details [eid-or-eident n p]
  #?(:clj (let [db     (d/db conn)
                datoms (d/datoms db {:index      :eavt
                                     :components [eid-or-eident]})]
            (resolve-datoms db datoms n p))))

(comment
  (entity-details 1 100 0)
  (entity-details :db/ident 100 0))

(p/defn EntityDetailsScreen [eid page]
  (DataViewer.
    (str "Entity Details: " eid)
    [:e :a :v :tx]
    ~@(new (wrap entity-details eid data-rows page))))


(defn tx-overview [txid n p]
  #?(:clj (let [db        (d/db conn)
                tx-datoms (:data (first (d/tx-range conn {:start txid
                                                          :end   (inc txid)})))]
            (resolve-datoms db tx-datoms n p))))

(comment
  (time (tx-overview 13194139534022 200 0)))

(p/defn TransactionOverviewScreen [txid page]
  (DataViewer.
    (str "Transaction Overview: " txid)
    [:e :a :v :tx]
    ~@(new (wrap tx-overview txid data-rows page))))


(defn a-overview [aid-or-ident n p]
  #?(:clj (let [db       (d/db conn)
                a-datoms (d/datoms db {:index      :aevt
                                       :components [aid-or-ident]})]
            (resolve-datoms db a-datoms n p))))

(comment
  (time (a-overview 10 200 0))
  (time (a-overview :db/ident 200 0)))

(p/defn AttributeOverviewScreen [aid page]
  (DataViewer.
    (str "Attribute Overview: " aid)
    [:e :a :v :tx]
    ~@(new (wrap a-overview aid data-rows page))))


(p/defn App []
  (let [{:keys [route param page]} (first history)]
    (dom/div
      (when (not (= route :home))
        (Link. "Home" nav-home))
      (when (> (count history) 1)
        (BackButton.))
      (condp = route
        :home (HomeScreen. page)
        :e-details (EntityDetailsScreen. param page)
        :tx-overview (TransactionOverviewScreen. param page)
        :a-overview (AttributeOverviewScreen. param page)))))

(def app
  #?(:cljs
     (p/client
       (p/main
         (binding [dom/parent (dom/by-id "root")]
           (try
             (App.)
             (catch Pending _)))))))

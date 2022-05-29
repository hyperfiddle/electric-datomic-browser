(ns app.core
  (:require [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [hyperfiddle.zero :as z]
            [clojure.set :as set]
            #?(:clj [app.queries :as q])
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

(p/defn HomeScreen [tx-count]
  (DataViewer.
    "Transactions"
    [:db/id :db/txInstant]
    ~@(new (wrap q/transactions tx-count))
    {:db/id ::tx-overview})
  (let [attrs [:db/id :db/ident :db/cardinality :db/unique :db/fulltext :db/isComponent
               :db/tupleType :db/tupleTypes :db/tupleAttrs :db/valueType :db/doc]]
    (DataViewer.
      "Attributes"
      attrs
      ~@(new (wrap q/attributes attrs))
      {:db/id ::a-overview}
      Link)))

(p/defn EntityDetailsScreen [eid]
  (DataViewer.
    (str "Entity Details: " eid)
    [:db/id]
    ~@(new (wrap q/entity-details eid))
    {}))

(p/defn TransactionOverviewScreen [txid]
  (DataViewer.
    (str "Transaction Overview: " txid)
    [:e :a :v-ref :v-scalar :tx]
    ~@(new (wrap q/tx-overview txid data-rows))
    {:e     ::e-details
     :a     ::a-overview
     :v-ref ::e-details}))

(p/defn AttributeOverviewScreen [aid]
  (DataViewer.
    (str "Attribute Overview: " aid)
    [:e :a :v-ref :v-scalar :tx]
    ~@(new (wrap q/a-overview aid data-rows))
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

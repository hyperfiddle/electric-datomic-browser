(ns app.core
  (:require [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [app.components :as c]
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

(def max-last-tx-count 5)
(def max-display-size 150)

(def initial-nav-state {:route ::home :params max-last-tx-count})
(def !nav-state #?(:cljs (atom initial-nav-state)))
(p/def nav-state (p/watch !nav-state))

(p/defn Link [label nav-data]
  (c/Button.
    label
    nav-state
    (p/fn [_]
      (reset! !nav-state nav-data))))

(p/defn HomeScreen [params]
  (c/DataViewer.
    "Last Transactions"
    ~@(new (wrap (partial q/last-transactions params)))
    {:db/id ::tx-overview}
    Link)
  (c/DataViewer.
    "Identifying Attributes"
    ~@(new (wrap q/identifying-attributes))
    {:db/id ::a-overview}
    Link)
  (c/DataViewer.
    "Normal Attributes"
    ~@(new (wrap q/normal-attributes))
    {:db/id ::a-overview}
    Link))

(p/defn EntityDetailsScreen [eid]
  (Link. "Home" initial-nav-state)
  (c/DataViewer.
    (str "Entity Details: " eid)
    ~@(new (wrap (partial q/entity-details eid)))
    {}
    Link))

(p/defn TransactionOverviewScreen [txid]
  (Link. "Home" initial-nav-state)
  (c/DataViewer.
    (str "Transaction Overview: " txid)
    ~@(new (wrap (partial q/tx-overview txid max-display-size)))
    {:e     ::e-details
     :a     ::a-overview
     :v-ref ::e-details}
    Link))

(p/defn AttributeOverviewScreen [aid]
  (Link. "Home" initial-nav-state)
  (c/DataViewer.
    (str "Attribute Overview: " aid)
    ~@(new (wrap (partial q/a-overview aid max-display-size)))
    {:e     ::e-details
     :v-ref ::e-details
     :tx     ::tx-overview}
    Link))

(p/defn App []
  (dom/div
    (let [{:keys [route params]} nav-state]
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

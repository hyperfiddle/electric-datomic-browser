(ns app.core
  (:require [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [app.components :as c]
            #?(:clj [app.queries :as q])
            [missionary.core :as m])
  (:import (hyperfiddle.photon Pending)
           #?(:clj (hyperfiddle.photon_impl.runtime Failure)))
  #?(:cljs (:require-macros app.core)))                     ; forces shadow hot reload to also reload JVM at the same time


(defn wrap "run slow blocking fn on a threadpool"
  [f & args]
  #?(:clj (->> (m/ap (m/? (m/via m/cpu (time (apply f args)))))
               (m/reductions {} (Failure. (Pending.))))))

(def !nav-state #?(:cljs (atom {:route  ::home
                                :params 5})))
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
    {:db/id        ::e-details
     :db/txInstant ::tx-overview}
    Link)
  (c/DataViewer.
    "Identifying Attributes"
    ~@(new (wrap q/identifying-attributes))
    {:db/id    ::e-details
     :db/ident ::a-overview}
    Link)
  (c/DataViewer.
    "Normal Attributes"
    ~@(new (wrap q/normal-attributes))
    {:db/id    ::e-details
     :db/ident ::a-overview}
    Link))

(p/defn EntityDetailsScreen [params]
  (Link. "Home" {:route ::home :params 5})
  (dom/h1 (dom/text (str "Entity Details: " params))))

(p/defn TransactionOverviewScreen [params]
  (Link. "Home" {:route ::home :params 5})
  (dom/h1 (dom/text (str "Transaction Overview")))
  (dom/p (dom/text params)))

(p/defn AttributeOverviewScreen [params]
  (Link. "Home" {:route ::home :params 5})
  (dom/h1 (dom/text "Attribute Overview"))
  (dom/p (dom/text params)))

(p/defn App []
  (dom/div
    (condp = (:route nav-state)
      ::home (HomeScreen. (:params nav-state))
      ::e-details (EntityDetailsScreen. (:params nav-state))
      ::tx-overview (TransactionOverviewScreen. (:params nav-state))
      ::a-overview (AttributeOverviewScreen. (:params nav-state)))

    (c/TimerTest.)
    (c/ClickMeTest.)))

(def app
  #?(:cljs
     (p/client
       (p/main
         (binding [dom/parent (dom/by-id "root")]
           (try
             (App.)
             (catch Pending _)))))))

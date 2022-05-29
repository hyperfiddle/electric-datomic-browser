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

(def max-last-tx-count 5)
(def max-display-size 150)

(def initial-nav-state {:route ::home :params max-last-tx-count})
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

(defn all-keys [ms]
  (->> ms
       (reduce (fn [s v] (set/union s (set (keys v)))) #{})
       ; hacky column sorting
       (sort-by (fn [v] (condp = v
                          :db/id "0"
                          :db/ident "1"
                          :db/doc "zzz"
                          :e "0"
                          :a "1"
                          :v-ref "2"
                          :v-scalar "3"
                          :tx "4"
                          (str (namespace v) "/" (name v)))))))

(p/defn DataViewer [title maps key-routes Link]
  (let [ks (all-keys maps)]
    (dom/h1 (dom/text title))
    (dom/table
      (dom/thead
        (dom/for [k ks]
          (dom/td (dom/style {"min-width" "5em"}) (dom/text k))))
      (dom/tbody
        (dom/for [m maps]
          (dom/tr
            (dom/for [k ks]
              (dom/td
                (if (and (contains? key-routes k) (m k))
                  (Link. (m k) {:route  (key-routes k)
                                :params (m k)})
                  (dom/text (m k)))))))))))

(p/defn HomeScreen [params]
  (DataViewer.
    "Last Transactions"
    ~@(new (wrap (partial q/last-transactions params)))
    {:db/id ::tx-overview}
    Link)
  (DataViewer.
    "Identifying Attributes"
    ~@(new (wrap q/identifying-attributes))
    {:db/id ::a-overview}
    Link)
  (DataViewer.
    "Normal Attributes"
    ~@(new (wrap q/normal-attributes))
    {:db/id ::a-overview}
    Link))

(p/defn EntityDetailsScreen [eid]
  (Link. "Home" initial-nav-state)
  (DataViewer.
    (str "Entity Details: " eid)
    ~@(new (wrap (partial q/entity-details eid)))
    {}
    Link))

(p/defn TransactionOverviewScreen [txid]
  (Link. "Home" initial-nav-state)
  (DataViewer.
    (str "Transaction Overview: " txid)
    ~@(new (wrap (partial q/tx-overview txid max-display-size)))
    {:e     ::e-details
     :a     ::a-overview
     :v-ref ::e-details}
    Link))

(p/defn AttributeOverviewScreen [aid]
  (Link. "Home" initial-nav-state)
  (DataViewer.
    (str "Attribute Overview: " aid)
    ~@(new (wrap (partial q/a-overview aid max-display-size)))
    {:e     ::e-details
     :v-ref ::e-details
     :tx    ::tx-overview}
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

(ns app.core
  (:require [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [hyperfiddle.photon-ui :as ui]
            [app.queries :refer [transactions attributes entity-details
                                 tx-overview a-overview]])
  #?(:cljs (:require-macros app.core)))

(p/def db)                                                  ; server
(p/def conn)                                                ; server
(p/def Navigate!)                                           ; client
(p/def Navigate-back!)                                      ; client
(p/def history)
(def limit 20)
(p/def initial-page 0)
(p/def !page nil)

(p/defn Cell [v]
  (if (and (map? v) (contains? v :route) (contains? v :param))
    (ui/button {::ui/click-event (p/fn [_] (Navigate!. v))} (:param v)) ; ?
    (str v)))

(p/defn Data-viewer [cols Query-fn]
  (p/client
    (let [!page (atom 0)
          page (p/watch !page)
          more? true]
      (dom/table (dom/thead (p/for [k cols] (dom/td k)))
                 (dom/tbody
                   (p/server
                     (p/for [m (Query-fn. cols page)]       ; are there more pages?
                       (p/client (dom/tr (p/for [k cols] (dom/td (Cell. (m k))))))))))
      (dom/div
        {::dom/class "controls"}
        (ui/button {::dom/disabled   (= 0 page)
                    ::ui/click-event (p/fn [_] (swap! !page dec))} "previous")
        (str " page " page " ")
        (ui/button {::dom/disabled   (not more?)
                    ::ui/click-event (p/fn [_] (swap! !page inc))} "next")))))

(p/defn HomeScreen []
  (dom/h1 "Transactions")
  (p/server (Data-viewer. [:db/id :db/txInstant] (p/fn [shape page]
                                                   (new (transactions db shape 10 page)))))
  (dom/h1 "Attributes")
  (p/server (Data-viewer.
              [:db/id :db/ident :db/cardinality :db/unique :db/fulltext :db/isComponent
               :db/tupleType :db/tupleTypes :db/tupleAttrs :db/valueType :db/doc]
              (p/fn [shape page] (new (attributes db shape 20 page))))))

(p/defn EntityDetailsScreen [eid]
  (dom/h1 (str "Entity Details: " eid))
  (p/server (Data-viewer. [:e :a :v :tx] (p/fn [_shape page] (new (entity-details db eid limit page))))))

(p/defn TransactionOverviewScreen [txid]
  (dom/h1 (str "Transaction Overview: " txid))
  (p/server (Data-viewer. [:e :a :v :tx] (p/fn [_shape page] (new (tx-overview conn db txid limit page))))))

(p/defn AttributeOverviewScreen [aid]
  (dom/h1 (str "Attribute Overview: " aid))
  (p/server (Data-viewer. [:e :a :v :tx] (p/fn [_shape page] (new (a-overview db aid limit page))))))

(p/defn App []
  (let [{:keys [route param]} (first history)
        route (or route :home)]
    (ui/button {::dom/disabled   (= route :home)
                ::ui/click-event (p/fn [_] (Navigate!. {:route :home}))} "Home")
    (ui/button {::dom/disabled   (= (count history) 0)
                ::ui/click-event (p/fn [_] (Navigate-back!.))} "Back")
    (condp = route
      :home (HomeScreen.)
      :e-details (EntityDetailsScreen. param)
      :tx-overview (TransactionOverviewScreen. param)
      :a-overview (AttributeOverviewScreen. param))))

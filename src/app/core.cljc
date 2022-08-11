(ns app.core
  (:require [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [hyperfiddle.photon-ui :as ui]
            [app.queries :refer [tx-attrs a-attrs
                                 transactions attributes entity-details
                                 tx-overview a-overview]])
  #?(:cljs (:require-macros app.core)))

(p/def db)                                                  ; server
(p/def conn)                                                ; server
(p/def Navigate!)                                           ; client
(p/def Navigate-back!)                                      ; client
(p/def history)
(def limit 100)
(p/def initial-page 0)
(p/def !page nil)

(p/defn Cell [v]
  (dom/td
    (if (and (map? v) (contains? v :route) (contains? v :param))
      (ui/button {::ui/click-event (p/fn [_] (Navigate!. v))} (:param v))
      (str v))))

(p/defn Data-viewer [cols Query-fn]
  (let [!page (atom 0)
        page (p/watch !page)
        more? true]
    (dom/table
      (dom/thead
        (p/for [k cols]
          (dom/td k)))
      (dom/tbody
        (p/server
          ; Query-fn is thunked to prevent over-eager transfer, see https://github.com/hyperfiddle/photon/issues/12
          (p/for [m (Query-fn.)]                            ; are there more pages?
            (p/client
              (dom/tr
                (p/for [k cols]
                  (Cell. (m k)))))))))
    (dom/div
      {::dom/class "controls"}
      (ui/button {::ui/disabled    (= 0 page)
                  ::ui/click-event (p/fn [_] (swap! !page dec))} "previous")
      (str " page " page " ")
      (ui/button {::ui/disabled    (not more?)
                  ::ui/click-event (p/fn [_] (swap! !page inc))} "next"))))

(p/defn HomeScreen []
  (dom/h1 "Transactions")
  (Data-viewer. tx-attrs (p/fn [page] (p/server (new (transactions db 10 page)))))
  (dom/h1 "Attributes")
  (Data-viewer. a-attrs (p/fn [page] (p/server (new (attributes db 20 page))))))

(p/defn EntityDetailsScreen [eid]
  (dom/h1 (str "Entity Details: " eid))
  (Data-viewer. [:e :a :v :tx] (p/fn [page] (p/server (new (entity-details db eid limit page))))))

(p/defn TransactionOverviewScreen [txid]
  (dom/h1 (str "Transaction Overview: " txid))
  (Data-viewer. [:e :a :v :tx] (p/fn [page] (p/server (new (tx-overview conn db txid limit page))))))

(p/defn AttributeOverviewScreen [aid]
  (dom/h1 (str "Attribute Overview: " aid))
  (Data-viewer. [:e :a :v :tx] (p/fn [page] (p/server (new (a-overview db aid limit page))))))

(p/defn App []
  (let [{:keys [route x]} (first history)
        route (or route :home)]
    (ui/button {::ui/disabled    (= route :home)
                ::ui/click-event (p/fn [_] (Navigate!. :home))} "Home")
    (ui/button {::ui/disabled    (= (count history) 0)
                ::ui/click-event (p/fn [_] (Navigate-back!.))} "Back")
    (condp = route
      :home (HomeScreen.)
      :e-details (EntityDetailsScreen. x)
      :tx-overview (TransactionOverviewScreen. x)
      :a-overview (AttributeOverviewScreen. x))))

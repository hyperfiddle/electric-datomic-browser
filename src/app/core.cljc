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

(def tx-count 10)
(def as-count 20)
(def data-rows 100)

(def nav-home {:route :home})

(def !history #?(:cljs (atom (list nav-home))))
(p/def history (p/watch !history))
(p/def initial-page 0)
(p/def !page nil)

(p/defn Link [label nav-data]
  (ui/button {::ui/click-event (p/fn [_] (swap! !history conj nav-data))} label))

(p/defn Cell [v]
  (dom/td
    (if (and (map? v) (contains? v :route) (contains? v :param))
      (Link. (:param v) v)
      (str v))))

;; data-viewer is implemented as macro until Photon supports lazy args binding,
;; at which point it would be a regular p/defn. The goal is for `maps` to be
;; resolved on the server (so the query runs on the server) and rows
;; transfers from server to client individually.
(defmacro data-viewer [title keys maps]
  `(dom/div
    (dom/h1 ~title)
    (ui/button {::ui/click-event (p/fn [_] (swap! !page dec))} "previous page")
    (str " page " (p/watch !page) " ")
    (ui/button {::ui/click-event (p/fn [_] (swap! !page inc))} "next page")
    (dom/table
      (dom/thead
        (p/for [k# ~keys]
          (dom/td k#)))
      (dom/tbody
        (p/server
          (p/for [m# ~maps]
            (p/client
              (dom/tr
                (p/for [k# ~keys]
                  (Cell. (m# k#)))))))))))

(p/defn HomeScreen []
  (dom/div
   (binding [!page (atom 0)]
     (let [page (p/watch !page)]
       (data-viewer "Transactions" tx-attrs (new (transactions db tx-count page)))))
   (binding [!page (atom 0)]
     (let [page (p/watch !page)]
       (data-viewer "Attributes" a-attrs (new (attributes db as-count page)))))))

(p/defn EntityDetailsScreen [eid]
  (binding [!page (atom initial-page)]
    (let [page (p/watch !page)]
      (data-viewer
       (str "Entity Details: " eid)
       [:e :a :v :tx]
       (new (entity-details db eid data-rows page))))))

(p/defn TransactionOverviewScreen [txid]
  (binding [!page (atom initial-page)]
    (let [page (p/watch !page)]
      (data-viewer
       (str "Transaction Overview: " txid)
       [:e :a :v :tx]
       (new (tx-overview conn db txid data-rows page))))))

(p/defn AttributeOverviewScreen [aid]
  (binding [!page (atom initial-page)]
    (let [page (p/watch !page)]
      (data-viewer
       (str "Attribute Overview: " aid)
       [:e :a :v :tx]
       (new (a-overview db aid data-rows page))))))

(p/defn App []
  (let [{:keys [route param]} (first history)]
    (dom/div
      (when (not (= route :home))
        (Link. "Home" nav-home))
      (when (> (count history) 1)
        (ui/button {::ui/click-event (p/fn [_] (swap! !history rest))} "Back"))
     (condp = route
        :home (HomeScreen.)
        :e-details (EntityDetailsScreen. param)
        :tx-overview (TransactionOverviewScreen. param)
        :a-overview (AttributeOverviewScreen. param)))))

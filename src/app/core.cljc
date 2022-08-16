(ns app.core
  (:require [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [hyperfiddle.photon-ui :as ui]
            [missionary.core :as m]
            #?(:clj [app.queries :refer [tx-overview entity-details a-overview
                                         attributes transactions
                                         tx-datoms render-datoms task->cp]]))
  #?(:cljs (:require-macros app.core)))

(p/def db) ; server
(p/def conn) ; server
(p/def Navigate!) ; client
(p/def Navigate-back!) ; client
(p/def history)
(def limit 20)
(p/def initial-page 0)
(p/def !page nil)

(p/def Cell) ; dynamic, client

#?(:cljs (defn preventDefault [e] (.preventDefault e))) ; photon doesn't implement clojure's host interop special forms yet

(p/defn Data-viewer [cols Query-fn]
  (p/client
    (let [!page (atom 0)
          page (p/watch !page)
          more? true]
      (dom/div {::dom/class "controls"}
        (ui/element dom/a {::dom/href ""
                           ::dom/disabled (= 0 page)
                           ::ui/click-event (p/fn [e]
                                              (swap! !page dec)
                                              (preventDefault e))} "previous")
        (str " page " page " ")
        (ui/element dom/a {::dom/href ""
                           ::dom/disabled (not more?)
                           ::ui/click-event (p/fn [e]
                                              (swap! !page inc)
                                              (preventDefault e))} "next"))
      (dom/table
        (dom/thead (p/for [k cols] (dom/td k)))
        (dom/tbody
          (p/server
            (p/for [m (Query-fn. cols page)] ; are there more pages?
              (p/client
                (dom/tr
                  (p/for [k cols]
                    (dom/td {:style {:white-space :nowrap}}
                            (Cell. k (m k)))))))))))))

(p/defn Nav-link [label route param]
  (ui/element dom/a {::dom/href ""
                     ::ui/click-event (p/fn [e]
                                        (Navigate!. [route param])
                                        (preventDefault e))} label))

(p/defn HomeScreen []
  (dom/h1 "Transactions")
  (binding [Cell (p/fn [k v]
                   (case k
                     :db/id (Nav-link. v :tx-overview v)
                     :db/txInstant (pr-str v)
                     (str v)))]
    (p/server (Data-viewer.
                [:db/id :db/txInstant]
                (p/fn [shape page]
                  (new (task->cp (transactions db shape 10 page)))))))

  (dom/h1 "Attributes")
  (binding [Cell (p/fn [k v]
                   (case k
                     :db/id (Nav-link. v :e-details v)
                     :db/ident (Nav-link. v :e-details v)
                     :db/valueType (some-> v :db/ident name)
                     :db/cardinality (some-> v :db/ident name)
                     :db/unique (some-> v :db/ident name)
                     (str v)))]
    (p/server (Data-viewer.
                [:db/id :db/ident :db/valueType :db/cardinality :db/unique :db/doc
                 ; :db/fulltext :db/isComponent :db/tupleType :db/tupleTypes :db/tupleAttrs
                 ]
                (p/fn [shape page]
                  (new (task->cp (attributes db shape 10 page))))))))

(p/defn DatomCell [k v]
  (case k
    :e (Nav-link. v :e-details v)
    :a (Nav-link. v :a-overview v)
    :v (if (map? v) ; need to know valueType
         (cond
           (:db/ident v) (Nav-link. (:db/ident v) :a-overview (:db/ident v))
           (:db/id v) (Nav-link. (:db/ident v) :e-details (:db/ident v))
           :else v) v)
    :tx (Nav-link. v :tx-overview v)
    (pr-str v)))

(p/defn EntityDetailsScreen [eid]
  (dom/h1 "Entity Details: " eid)
  (binding [Cell DatomCell]
    (p/server (Data-viewer.
                [:e :a :v :tx]
                (p/fn [_shape page]
                  (new (task->cp (render-datoms db (entity-details db eid limit page)))))))))

(p/defn TransactionOverviewScreen [txid]
  (dom/h1 "Transaction Overview: " txid)
  (binding [Cell DatomCell]
    (p/server (Data-viewer. [:e :a :v :tx]
                            (p/fn [_shape page]
                              (new (task->cp (render-datoms db (tx-overview conn txid limit page)))))))))

(p/defn AttributeOverviewScreen [aid]
  (dom/h1 "Attribute Overview: " aid)
  (binding [Cell DatomCell]
    (p/server (Data-viewer. [:e :a :v :tx]
                            (p/fn [_shape page]
                              (new (task->cp (render-datoms db (a-overview db aid limit page)))))))))

(p/defn App []
  (let [[route param] history
        route (or route :home)]
    (Nav-link. "Home" :home nil) (dom/span " ") (dom/code (pr-str app.core/history))
    #_(ui/button {::dom/disabled (= (count history) 0)
                  ::ui/click-event (p/fn [_] (Navigate-back!.))} "Back")
    (condp = route
      :home (HomeScreen.)
      :e-details (EntityDetailsScreen. param)
      :tx-overview (TransactionOverviewScreen. param)
      :a-overview (AttributeOverviewScreen. param))))

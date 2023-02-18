(ns app.datomic-browser

  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros app.datomic-browser)) ; <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            #?(:clj [app.queries :refer [tx-overview entity-details a-overview
                                         attributes transactions
                                         tx-datoms render-datoms]])))

(e/def db) ; server
(e/def conn) ; server
(e/def Navigate!) ; client
(e/def Navigate-back!) ; client
(e/def history)
(def limit 20)
(e/def initial-page 0)
(e/def !page nil)

(e/def Cell) ; dynamic, client

#?(:cljs (defn preventDefault [e] (.preventDefault e))) ; photon doesn't implement clojure's host interop special forms yet

(e/defn Data-viewer [cols Query-fn]
  (e/client
    (let [!page (atom 0)
          page (e/watch !page)
          more? true]
      (dom/div {::dom/class "controls"}
        (ui/element dom/a {::dom/href ""
                           ::dom/disabled (= 0 page)
                           ::ui/click-event (e/fn [e]
                                              (swap! !page dec)
                                              (preventDefault e))} "previous")
        (str " page " page " ")
        (ui/element dom/a {::dom/href ""
                           ::dom/disabled (not more?)
                           ::ui/click-event (e/fn [e]
                                              (swap! !page inc)
                                              (preventDefault e))} "next"))
      (dom/table
        (dom/thead (e/for [k cols] (dom/td k)))
        (dom/tbody
          (e/server
            (e/for [m (Query-fn. cols page)] ; are there more pages?
              (e/client
                (dom/tr
                  (e/for [k cols]
                    (dom/td {:style {:white-space :nowrap}}
                            (Cell. k (m k)))))))))))))

(e/defn Nav-link [label route param]
  (ui/element dom/a {::dom/href ""
                     ::ui/click-event (e/fn [e]
                                        (Navigate!. [route param])
                                        (preventDefault e))} label))

(e/defn HomeScreen []
  (dom/h1 "Transactions")
  (binding [Cell (e/fn [k v]
                   (case k
                     :db/id (Nav-link. v :tx-overview v)
                     :db/txInstant (pr-str v)
                     (str v)))]
    (e/server (Data-viewer.
                [:db/id :db/txInstant]
                (e/fn [shape page]
                  (new (e/task->cp (transactions db shape 10 page)))))))

  (dom/h1 "Attributes")
  (binding [Cell (e/fn [k v]
                   (case k
                     :db/id (Nav-link. v :e-details v)
                     :db/ident (Nav-link. v :e-details v)
                     :db/valueType (some-> v :db/ident name)
                     :db/cardinality (some-> v :db/ident name)
                     :db/unique (some-> v :db/ident name)
                     (str v)))]
    (e/server (Data-viewer.
                [:db/id :db/ident :db/valueType :db/cardinality :db/unique :db/doc
                 ; :db/fulltext :db/isComponent :db/tupleType :db/tupleTypes :db/tupleAttrs
                 ]
                (e/fn [shape page]
                  (new (e/task->cp (attributes db shape 10 page))))))))

(e/defn DatomCell [k v]
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

(e/defn EntityDetailsScreen [eid]
  (dom/h1 "Entity Details: " eid)
  (binding [Cell DatomCell]
    (e/server (Data-viewer.
                [:e :a :v :tx]
                (e/fn [_shape page]
                  (new (e/task->cp (render-datoms db (entity-details db eid limit page)))))))))

(e/defn TransactionOverviewScreen [txid]
  (dom/h1 "Transaction Overview: " txid)
  (binding [Cell DatomCell]
    (e/server (Data-viewer. [:e :a :v :tx]
                            (e/fn [_shape page]
                              (new (e/task->cp (render-datoms db (tx-overview conn txid limit page)))))))))

(e/defn AttributeOverviewScreen [aid]
  (dom/h1 "Attribute Overview: " aid)
  (binding [Cell DatomCell]
    (e/server (Data-viewer. [:e :a :v :tx]
                            (e/fn [_shape page]
                              (new (e/task->cp (render-datoms db (a-overview db aid limit page)))))))))


(e/defn Datomic-browser []
  (e/server
    (binding [conn user/datomic-conn
              db (datomic.client.api.async/db user/datomic-conn)]
      (e/client
        (binding [history (e/watch !history)
                  Navigate! (e/fn [route]
                              (e/client
                                (reset! !history route)
                                #_(swap! !history conj route)))
                  #_#_app.core/Navigate-back! (p/fn [] (p/client (swap! !history rest)))]
          (dom/div (dom/text "hello world " (e/server (pr-str (type db)))))
          #_(let [[route param] history
                  route (or route :home)]
              (Nav-link. "Home" :home nil) (dom/span " ") (dom/code (pr-str app.core/history))
              #_(ui/button {::dom/disabled (= (count history) 0)
                            ::ui/click-event (e/fn [_] (Navigate-back!.))} "Back")
              (condp = route
                :home (HomeScreen.)
                :e-details (EntityDetailsScreen. param)
                :tx-overview (TransactionOverviewScreen. param)
                :a-overview (AttributeOverviewScreen. param))))))))

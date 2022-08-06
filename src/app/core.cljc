(ns app.core
  (:require #?(:clj [datomic.client.api.async :as d])
            #?(:clj [datomic.client.api :as dsync])
            [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [missionary.core :as m]
            [hyperfiddle.photon-ui :as ui])
  (:import (hyperfiddle.photon Pending)
           (missionary Cancelled))
  #?(:cljs (:require-macros app.core)))                     ; forces shadow hot reload to also reload JVM at the same time

(def tx-count 10)
(def as-count 20)
(def data-rows 100)

(def nav-home {:route :home})
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
(p/def initial-page 0)
(p/def !page nil)

(def tx-attrs [:db/id :db/txInstant])
(def a-attrs [:db/id :db/ident :db/cardinality :db/unique :db/fulltext :db/isComponent
              :db/tupleType :db/tupleTypes :db/tupleAttrs :db/valueType :db/doc])
(def conn #?(:clj (-> (dsync/client {:server-type :dev-local
                                     :system      "datomic-samples"})
                      (dsync/connect {:db-name "mbrainz-1968-1973"}))))

(p/defn Button [label F]
  (ui/button {::ui/click-event (p/fn [event] (when event (F. event)) nil)}
    (dom/text label)))

(p/defn Link [label nav-data]
  (Button.
    label
    (p/fn [_]
      (swap! !history conj nav-data))))

(p/defn BackButton []
  (Button.
    "Back"
    (p/fn [_]
      (swap! !history rest))))

(p/defn Pagination [label amount]
  (Button. label (p/fn [event]
                   (when event
                     (swap! !page + amount)))))

(p/defn Cell [v]
  (dom/td
    (if (and (map? v) (contains? v :route) (contains? v :param))
      (Link. (:param v) v)
      (dom/text v))))

;; data-viewer is implemented as macro until Photon supports lazy args binding,
;; at which point it would be a regular p/defn. The goal is for `maps` to be
;; resolved on the server (so the query runs on the server) and rows
;; transfers from server to client individually.
(defmacro data-viewer [title keys maps]
  `(dom/div
    (dom/h1 (dom/text ~title))
    (Pagination. "previous page" -1)
    (dom/text (str " page " (p/watch !page) " "))
    (Pagination. "next page" 1)
    (dom/table
      (dom/thead
        (p/for [k# ~keys]
          (dom/td
            (dom/text k#))))
      (dom/tbody
        ;; client
        (unquote-splicing ; transfer point (aka ~@)
          (p/for [m# ~maps] ; server
            (unquote-splicing ; transfer point (ak ~@)
              (dom/tr ; client
                (p/for [k# ~keys]
                  (Cell. (m# k#)))))))))))

#?(:clj
   (defn query
     "Return a task running a datomic query asynchronously, and completing
     when all streamed results have been collected into a vector."
     [query & args]
     (m/reduce into [] (p/chan->flow (d/q {:query query, :args (vec args)})))))

(defn transactions [n p]
  ;; We produce a flow which will run a query, transform its result, emit it and terminate.
  #?(:clj (->> (m/ap ; return a discreet flow
                (->> (query ; build a task running the query
                      '[:find (pull ?tx pattern)
                        :in $ pattern
                        :where [?tx :db/txInstant]]
                      (d/db conn) tx-attrs)
                     (m/?) ; run it, wait for it to succeed and produce a result
                     ; transform result
                     (map first)
                     (sort-by :db/txInstant)
                     (map #(update % :db/id nav-tx-overview))
                     (reverse)
                     (drop (* n p))
                     (take n)))
               ;; A discreet flow does not have an initial value. It is undefined until it produces a first value.
               ;; A UI is continuous, meaning it's always defined. There is no such thing as an "in between two states" UI. 
               ;; Either it's visible on screen, or it's not a UI.
               ;; We must give this discreet flow an initial value for the UI to render something. We choose `nil`.
               ;; {} is pronounced "discard", given two arguments, it ignores the left one and return the right one.
               ;; Our UI will therefore display nil then the query result will arrive, replace nil, and the UI will rerender.
               (m/reductions {} nil)
               )))

(comment
  (m/? (m/reduce {} nil (transactions 5 0))))

(defn attributes [n p]
  #?(:clj (->> (m/ap (->> (m/? (query '[:find (pull ?e pattern)
                                        :in $ pattern
                                        :where
                                        [?e :db/valueType _]]
                                      (d/db conn)
                                      a-attrs))
                          (map first)
                          (sort-by :db/ident)
                          (map #(update % :db/id nav-a-overview))
                          (map #(update-vals % possibly-nav-ref))
                          (drop (* n p))
                          (take n)))
               (m/reductions {} nil))))

(comment
  (time (m/? (m/reduce {} nil (attributes 10 0))))
  )

(p/defn HomeScreen []
  (dom/div
   (binding [!page (atom 0)]
     (let [page (p/watch !page)]
       (data-viewer "Transactions" tx-attrs (new (transactions tx-count page)))))
   (binding [!page (atom 0)]
     (let [page (p/watch !page)]
       (data-viewer "Attributes" a-attrs (new (attributes as-count page)))))))

(defn resolve-datoms [db datoms n p]
  #?(:clj (m/sp (let [ref-attr?  (->> (m/? (query '[:find ?e :where [?e :db/valueType :db.type/ref]] db))
                                      (map first)
                                      (set))
                      datoms-id? (set (concat (map #(nth % 0) datoms)
                                              (map #(nth % 1) datoms)
                                              (->> datoms
                                                   (filter #(ref-attr? (nth % 1)))
                                                   (map #(nth % 2)))))
                      id->ident  (into {} (m/? (query '[:find ?e ?v
                                                        :in $ ?datoms-id?
                                                        :where
                                                        [?e :db/ident ?v]
                                                        [(contains? ?datoms-id? ?e)]]
                                                       db
                                                       datoms-id?)))]
                  (->> datoms
                       (drop (* n p))
                       (take n)
                       (map (fn [[e a v t]]
                              {:e  (nav-e-details (get id->ident e e))
                               :a  (nav-a-overview (get id->ident a a))
                               :v  (if (ref-attr? a)
                                     (possibly-nav-ref {:db/id v :db/ident (get id->ident v v)})
                                     v)
                               :tx (nav-tx-overview t)})))))))


(defn entity-details [eid-or-eident n p]
  #?(:clj 
     (->> (m/ap (let [db     (d/db conn)
                      datoms (m/? (->> (d/datoms db {:index      :eavt
                                                     :components [eid-or-eident]})
                                       (p/chan->flow)
                                       (m/reduce into [])))]
                  (m/? (resolve-datoms db datoms n p))))
          (m/reductions {} nil))))

(comment
  (m/? (m/reduce {} nil (entity-details 1 100 0)))
  (m/? (m/reduce {} nil (entity-details :db/ident 100 0))))

(p/defn EntityDetailsScreen [eid]
  (binding [!page (atom initial-page)]
    (let [page (p/watch !page)]
      (data-viewer
       (str "Entity Details: " eid)
       [:e :a :v :tx]
       (new (entity-details eid data-rows page)))))) 


(defn tx-overview [txid n p]
  #?(:clj
     (->> (m/ap (let [tx-datoms (->> (d/tx-range conn {:start txid, :end (inc txid)})
                                     (p/chan->flow)
                                     (m/eduction (take 1)) ; flow will terminate after 1 tx is received
                                     (m/?>) ; park until flow produces a value
                                     (:data))]
                  (m/? (resolve-datoms (d/db conn) tx-datoms n p))))
          (m/reductions {} nil) ; UI need an initial value to display until query produces its first result
          )))

(comment
  (time (m/? (m/reduce {} nil (tx-overview 13194139534022 200 0)))))

(p/defn TransactionOverviewScreen [txid]
  (binding [!page (atom initial-page)]
    (let [page (p/watch !page)]
      (data-viewer
       (str "Transaction Overview: " txid)
       [:e :a :v :tx]
       (new (tx-overview txid data-rows page))))))

(defn a-overview [aid-or-ident n p]
  #?(:clj (->> (m/ap (let [db       (d/db conn)
                           a-datoms (m/? (->> (d/datoms db {:index      :aevt
                                                            :components [aid-or-ident]})
                                              (p/chan->flow)
                                              (m/reduce into [])))]
                       (m/? (resolve-datoms db a-datoms n p))))
               (m/reductions {} nil) ; UI need an initial value to display until query produces its first result
               )))

(comment
  (time (m/? (m/reduce {} nil (a-overview 10 200 0))))
  (time (m/? (m/reduce {} nil (a-overview :db/ident 200 0)))))

(p/defn AttributeOverviewScreen [aid]
  (binding [!page (atom initial-page)]
    (let [page (p/watch !page)]
      (data-viewer
       (str "Attribute Overview: " aid)
       [:e :a :v :tx]
       (new (a-overview aid data-rows page))))))

(p/defn App []
  (let [{:keys [route param]} (first history)]
    (dom/div
      (when (not (= route :home))
        (Link. "Home" nav-home))
      (when (> (count history) 1)
        (BackButton.))
     ;; `route` and `param` are bundled in the same map. Changing `param` would
     ;; cause `route` to reflow too, even if it has the same value. Curent
     ;; Photon conditionals will not deduplicate consecutive values and will
     ;; cancel the current branch, then remount it. It would cause the app's
     ;; page to unmount and remount. So we deduplicate route manually.
     (condp = (p/deduping route)
        :home (HomeScreen.)
        :e-details (EntityDetailsScreen. param)
        :tx-overview (TransactionOverviewScreen. param)
        :a-overview (AttributeOverviewScreen. param)))))

(def app
  #?(:cljs
     (p/boot (binding [dom/node (dom/by-id "root")] ; render App under #root
              (try
                (App.)
                (catch Pending _
                  (prn "App is in pending state")))))))

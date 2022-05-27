(ns user
  (:require [app.core]
    ; [datomic.client.api :as d]
            [hyperfiddle.photon :as p]
            [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as shadow-server]))

; shadow serves nrepl and browser assets including index.html
(defonce server (shadow-server/start!))
(defonce watch (shadow/watch :app))
(defonce websocket (p/start-websocket-server! {:host "localhost" :port 8081}))

;(def database "datomic-samples" #_"playground")
;(def database-name "mbrainz-subset")
;(def database-config {:server-type :dev-local
;                      :system      database})
;
;(defn current-conn []
;  (-> (d/client database-config)
;      (d/connect {:db-name database-name})))
;
;(defn current-db []
;  (-> (current-conn)
;      (d/db)))
;
;(defn current-attributes [db]
;  (->> (d/q '[:find (pull ?e [*])
;              :where
;              [?e :db/valueType _]
;              [?e :db/cardinality _]]
;            db)
;       (map first)))
;
;(comment
;  (current-attributes (current-db)))

(ns user
  (:require [datomic.client.api.async :as d]
            datomic.client.api
            hyperfiddle.photon-jetty-server
            [missionary.core :as m]
            shadow.cljs.devtools.api
            shadow.cljs.devtools.server
            app.core))

(def photon-server-config {:host "0.0.0.0"
                           :port 8080
                           :resources-path "resources/public"})

(def datomic-conn)

(defn main []
  (shadow.cljs.devtools.server/start!)
  (shadow.cljs.devtools.api/watch :app)
  (def datomic-client (datomic.client.api/client {:server-type :dev-local :system "datomic-samples"}))
  (def datomic-conn (datomic.client.api/connect datomic-client {:db-name "mbrainz-1968-1973"}))
  (def server (hyperfiddle.photon-jetty-server/start-server! photon-server-config))
  (comment (.stop server)))

(comment
  "REPL entrypoint"
  (main)

  (def db (d/db datomic-conn))
  (m/? (app.core/query
         '[:find (pull ?tx [:db/id :db/txInstant])
           :where [?tx :db/txInstant]]
         db))
  )

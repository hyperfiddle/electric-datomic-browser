(ns user
  (:require datomic.client.api
            hyperfiddle.photon-jetty-server
            hyperfiddle.rcf
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
  (def datomic-conn (datomic.client.api/connect datomic-client {:db-name "mbrainz-subset"}))
  (def server (hyperfiddle.photon-jetty-server/start-server! photon-server-config))
  (comment (.stop server))
  (println (str "\n👉 App available at http://" (:host photon-server-config) ":" (-> server (.getConnectors) first (.getPort)) "\n")))

(comment
  "REPL entrypoint"
  (main)

  "Enable RCF"
  (hyperfiddle.rcf/enable!)

  (require '[datomic.client.api.async :as d])
  (def db (d/db datomic-conn))
  (m/? (app.core/query
         '[:find (pull ?tx [:db/id :db/txInstant])
           :where [?tx :db/txInstant]]
         db))
  )

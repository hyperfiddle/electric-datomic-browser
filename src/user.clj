(ns user
  (:require datomic.client.api
            hyperfiddle.photon-jetty-server
            [hyperfiddle.rcf :refer [tests]]
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
  (println (str "\n👉 App available at http://" (:host photon-server-config) ":" (-> server (.getConnectors) first (.getPort)) "\n"))

  "dev bindings"
  (require '[datomic.client.api.async :as d])
  (def db (datomic.client.api.async/db datomic-conn))
  (hyperfiddle.rcf/enable!))

(comment
  "REPL entrypoint"
  (main)

  "Sanity check"
  (tests
    (->> (m/? (app.queries/query
                '[:find (pull ?tx [:db/id :db/txInstant])
                  :where [?tx :db/txInstant]]
                db))
         (take 1))
    := [[#:db{:id 13194139533312, :txInstant #inst"1970-01-01T00:00:00.000-00:00"}]]))

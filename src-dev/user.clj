(ns user ; Must be ".clj" file, Clojure doesn't auto-load user.cljc
  (:require [datomic.api :as d]
            [app.server :refer [start-server!]]
            [hyperfiddle.rcf :refer [tests]]))

; lazy load dev stuff - for faster REPL startup and cleaner dev classpath
(def shadow-start! (delay @(requiring-resolve 'shadow.cljs.devtools.server/start!)))
(def shadow-watch (delay @(requiring-resolve 'shadow.cljs.devtools.api/watch)))

(def electric-server-config
  {:host "0.0.0.0", :port 8080, :resources-path "public"})

(def datomic-conn)

(defn main [& args]
  (println "Starting Electric compiler and server...")
  (@shadow-start!) ; serves index.html as well
  (@shadow-watch :dev) ; depends on shadow server
  (def server (start-server! electric-server-config))
  (comment (.stop server))
  (alter-var-root #'datomic-conn (constantly (d/connect "datomic:dev://localhost:4334/mbrainz-1968-1973"))) 
  (def db (d/db datomic-conn)) ; inject test db, for repl only
  (hyperfiddle.rcf/enable!)
  (tests "datomic healthcheck"
    (->> (d/q '[:find (pull ?tx [:db/id :db/txInstant]) :where [?tx :db/txInstant]] db) (take 1))
    := [[#:db{:id 13194139533312, :txInstant #inst"1970-01-01T00:00:00.000-00:00"}]]))

(comment (main))
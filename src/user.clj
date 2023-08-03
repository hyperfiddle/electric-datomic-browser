(ns user ; Must be ".clj" file, Clojure doesn't auto-load user.cljc
  (:require datomic.api
            [hyperfiddle.rcf :refer [tests]]
            [missionary.core :as m]))

; lazy load dev stuff - for faster REPL startup and cleaner dev classpath
(def start-electric-server! (delay @(requiring-resolve 'electric-server-java11-jetty10/start-server!)))
(def shadow-start! (delay @(requiring-resolve 'shadow.cljs.devtools.server/start!)))
(def shadow-watch (delay @(requiring-resolve 'shadow.cljs.devtools.api/watch)))

(def electric-server-config
  {:host "0.0.0.0", :port 8080, :resources-path "public"})

(def datomic-conn)

(defn main [& args]
  (println "Starting Electric compiler and server...")
  (@shadow-start!) ; serves index.html as well
  (@shadow-watch :dev) ; depends on shadow server
  (def server (@start-electric-server! electric-server-config))
  (comment (.stop server))

  ; inject datomic root bindings
  (alter-var-root #'datomic-conn (constantly (datomic.api/connect "datomic:dev://localhost:4334/mbrainz-1968-1973")))

  ; inject test database, for repl only
  (require '[datomic.api :as d])
  (def db (datomic.api/db datomic-conn))
  (hyperfiddle.rcf/enable!))

; Userland Electric code is lazy loaded by the shadow build due to usage of
; :require-macros in all Electric source files.
; WARNING: make sure your REPL and shadow-cljs are sharing the same JVM!

(comment
  (main) ; Electric Clojure(JVM) REPL entrypoint
  (hyperfiddle.rcf/enable!) ; turn on RCF after all transitive deps have loaded
  (shadow.cljs.devtools.api/repl :dev) ; shadow server hosts the cljs repl
  ; connect a second REPL instance to it
  ; (DO NOT REUSE JVM REPL it will fail weirdly)
  (type 1)

  (tests "healthcheck"
    (->> (d/q
           '[:find (pull ?tx [:db/id :db/txInstant])
             :where [?tx :db/txInstant]]
           db)
      (take 1))
    := [[#:db{:id 13194139533312, :txInstant #inst"1970-01-01T00:00:00.000-00:00"}]])
  )
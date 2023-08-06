(ns user
  (:gen-class)
  (:require app.datomic-browser
            [app.server :refer [start-server!]]
            clojure.string
            [clojure.tools.logging :as log]
            [datomic.api :as d]))

(def electric-server-config
  {:host "0.0.0.0", :port 8080, :resources-path "public"})

(def app-version (System/getProperty "HYPERFIDDLE_ELECTRIC_SERVER_VERSION"))

(def datomic-conn)

(defn -main [& args] ; run with `clj -M -m prod`
  (log/info "Starting Electric server, app version: " app-version)
  (when (clojure.string/blank? app-version)
    (throw (ex-info "HYPERFIDDLE_ELECTRIC_SERVER_VERSION jvm property must be set in prod" {})))
  (alter-var-root #'datomic-conn (constantly (d/connect "datomic:dev://localhost:4334/mbrainz-1968-1973")))
  (start-server! electric-server-config))

; On CLJS side we reuse src/user.cljs for prod entrypoint
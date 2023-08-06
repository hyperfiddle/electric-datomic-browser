(ns prod
  (:gen-class)
  (:require [app.config :as config]
            app.datomic-browser
            [app.server :refer [start-server!]]
            clojure.string
            [clojure.tools.logging :as log]
            [datomic.api :as d]))

(defn -main [& args] ; run with `clj -M -m prod`
  (log/info "Starting Electric server, app version: " config/app-version)
  (when (clojure.string/blank? config/app-version)
    (throw (ex-info "HYPERFIDDLE_ELECTRIC_SERVER_VERSION jvm property must be set in prod" {})))
  (alter-var-root #'config/datomic-conn (constantly (d/connect "datomic:dev://localhost:4334/mbrainz-1968-1973")))
  (start-server! config/electric-server-config))

; On CLJS side we reuse src/user.cljs for prod entrypoint
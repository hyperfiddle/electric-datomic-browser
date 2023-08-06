(ns prod
  (:gen-class)
  (:require app.datomic-browser
            [app.server :refer [start-server!]]
            clojure.string
            [clojure.tools.logging :as log]))

(def electric-server-config
  {:host "0.0.0.0", :port 8080, :resources-path "public"})

(def app-version (System/getProperty "HYPERFIDDLE_ELECTRIC_SERVER_VERSION"))

(defn -main [& args] ; run with `clj -M -m prod`
  (log/info "Starting Electric server, app version: " app-version)
  (when (clojure.string/blank? app-version)
    (throw (ex-info "HYPERFIDDLE_ELECTRIC_SERVER_VERSION jvm property must be set in prod" {})))
  (start-server! electric-server-config))

; On CLJS side we reuse src/user.cljs for prod entrypoint
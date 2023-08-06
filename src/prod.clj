(ns prod
  (:gen-class)
  (:require app.datomic-browser
            [app.server :refer [start-server!]]
            clojure.string))

(def electric-server-config
  {:host "0.0.0.0", :port 8080, :resources-path "public"})

(defn -main [& args] ; run with `clj -M -m prod`
  (when (clojure.string/blank? (System/getProperty "HYPERFIDDLE_ELECTRIC_SERVER_VERSION"))
    (throw (ex-info "HYPERFIDDLE_ELECTRIC_SERVER_VERSION jvm property must be set in prod" {})))
  (start-server! electric-server-config))

; On CLJS side we reuse src/user.cljs for prod entrypoint
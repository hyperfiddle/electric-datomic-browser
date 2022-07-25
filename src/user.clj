(ns user
  (:require [app.core]
            [hyperfiddle.photon :as p]
            [shadow.cljs.devtools.api :as shadow]
            [hyperfiddle.photon-jetty-server :refer [start-server!]]
            [shadow.cljs.devtools.server :as shadow-server]))

; shadow serves nrepl and browser assets including index.html
(defonce server (shadow-server/start!))
(defonce watch (shadow/watch :app))
(def server (start-server! {:host "localhost" :port 8080}))

(comment
  (.stop server)
  )

(ns user
  (:require [hyperfiddle.photon :as p]
            [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as shadow-server]
            app.core))

; shadow serves nrepl and browser assets including index.html
(defonce server (shadow-server/start!))
(defonce watch (shadow/watch :app))
(defonce websocket (p/start-websocket-server! {:host "localhost" :port 8081}))

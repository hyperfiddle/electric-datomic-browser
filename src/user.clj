(ns user
  (:require [app.core]
            [hyperfiddle.photon :as p]
            [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as shadow-server]))

; shadow serves nrepl and browser assets including index.html
(defonce server (shadow-server/start!))
(defonce watch (shadow/watch :app))
(defonce websocket (p/start-websocket-server! {:host "localhost" :port 8081}))


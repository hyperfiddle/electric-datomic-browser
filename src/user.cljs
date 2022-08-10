(ns user
  (:require [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            app.core)
  (:import [hyperfiddle.photon Pending]))

(defonce reactor nil)

(defn ^:dev/after-load start! []
  (set! reactor ((p/boot
                   (try
                     (binding [dom/node (dom/by-id "root")]
                       (p/server
                         (binding [app.core/conn user/datomic-conn
                                   app.core/db (datomic.client.api.async/db user/datomic-conn)]
                           (p/client
                             (app.core/App.)
                             #_(dom/div (dom/text "hello world " (p/server (pr-str (type app.core/db)))))))))
                     (catch Pending _)))
                 #(js/console.log "Reactor success:" %)
                 #(js/console.error "Reactor failure:" %))))

(defn ^:dev/before-load stop! []
  (when reactor (reactor))                                  ; teardown
  (.. js/document (getElementById "root") (replaceChildren)) ; temporary workaround for https://github.com/hyperfiddle/photon/issues/10
  (set! reactor nil))

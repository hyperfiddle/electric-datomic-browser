(ns ^:dev/always user ; Electric currently needs to rebuild everything when any file changes. Will fix
  (:require
    app.datomic-browser
    hyperfiddle.electric))

(def electric-main
  (hyperfiddle.electric/boot ; Electric macroexpansion - Clojure to signals compiler
    (app.datomic-browser/DatomicBrowser.)))

(defonce reactor nil)

(defn ^:dev/after-load ^:export start! []
  (assert (nil? reactor) "reactor already running")
  (set! reactor (electric-main
                  #(js/console.log "Reactor success:" %)
                  #(js/console.error "Reactor failure:" %))))

(defn ^:dev/before-load stop! []
  (when reactor (reactor)) ; teardown
  (set! reactor nil))
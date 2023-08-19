(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]
            [shadow.cljs.devtools.api :as shadow-api] ; so as not to shell out to NPM for shadow
            [shadow.cljs.devtools.server :as shadow-server]))

(def lib 'com.hyperfiddle/electric-datomic-viewer)
(def version (b/git-process {:git-args "describe --tags --long --always --dirty"}))
(def basis (b/create-basis {:project "deps.edn" :aliases [:prod]}))

(defn clean [opts]
  (bb/clean opts)
  (b/delete {:path "resources/public/js"}))

(def class-dir "target/classes")
(defn default-jar-name [{:keys [version] :or {version version}}]
  (format "target/%s-%s-standalone.jar" (name lib) version))

(defn build-client
  "Prod optimized ClojureScript client build. (Note: in dev, the client is built 
on startup)"
  [{:keys [optimize debug verbose version]
    :or {optimize true, debug false, verbose false, version version}}]
  (println "Building client. Version:" version)
  (shadow-server/start!)
  (shadow-api/release :prod {:debug debug,
                             :verbose verbose,
                             :config-merge [{:compiler-options {:optimizations (if optimize :advanced :simple)}
                                             :closure-defines {'hyperfiddle.electric-client/VERSION version}}]})
  (shadow-server/stop!))

(defn uberjar [{:keys [jar-name version optimize debug verbose]
                :or   {version version, optimize true, debug false, verbose false}}]
  (println "Compiling server, version: " version)
  (clean nil)
  (build-client {:optimize optimize, :debug debug, :verbose verbose, :version version})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis      basis
                  :src-dirs   ["src" "src-prod"]
                  :ns-compile '[prod]
                  :class-dir  class-dir})
  (b/uber {:class-dir class-dir
           :uber-file "target/app.jar" #_(str (or jar-name (default-jar-name {:version version})))
           :basis     basis
           :main      'prod}))

(defn noop [_])                         ; run to preload mvn deps
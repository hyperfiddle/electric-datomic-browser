(ns app.core
  (:require [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [app.components :as c]
            #?(:clj [app.queries :as q]))
  (:import (hyperfiddle.photon Pending))
  #?(:cljs (:require-macros app.core)))                     ; forces shadow hot reload to also reload JVM at the same time

(def !nav-state #?(:cljs (atom {::route ::home})))
(p/def nav-state (p/watch !nav-state))

(p/defn Link [label nav-data]
  (c/Button.
    label
    nav-state
    (p/fn [_]
      (reset! !nav-state nav-data))))

(p/defn HomeScreen [params]
  (Link. "Other" {::route ::other})

  (c/DataViewer.
    "Last Transactions"
    ~@(q/last-transactions))
  (c/DataViewer.
    "Identifying Attributes"
    ~@(q/identifying-attributes))
  (c/DataViewer.
    "Normal Attributes"
    ~@(q/normal-attributes)))

(p/defn OtherScreen [params]
  (Link. "Home" {::route ::home}))

(p/defn App []
  (dom/div
    (condp = (::route nav-state)
      ::home (HomeScreen. (::params nav-state))
      ::other (OtherScreen. (::params nav-state)))

    (c/TimerTest.)
    (c/ClickMeTest.)))

(def app
  #?(:cljs
     (p/client
       (p/main
         (binding [dom/parent (dom/by-id "root")]
           (try
             (App.)
             (catch Pending _)))))))

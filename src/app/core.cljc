(ns app.core
  (:require [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [hyperfiddle.zero :as z]
            [missionary.core :as m]
            #?(:clj [app.queries :as q])
            #?(:cljs [app.utils :as u]))
  (:import (hyperfiddle.photon Pending))
  #?(:cljs (:require-macros app.core)))                     ; forces shadow hot reload to also reload JVM at the same time

(p/defn TimerTest []
  (dom/p (dom/span (dom/text "millisecond time: "))
         (dom/span (dom/text z/time))))

(p/defn ClickMeTest []
  (let [x (dom/button
            (dom/text "click me")
            (dom/attribute "type" "button")
            (new (->> (dom/events dom/parent "click")
                      (m/eduction (map (constantly 1)))
                      (m/reductions +))))]
    (dom/div
      (dom/table
        (dom/thead
          (dom/td (dom/style {"width" "5em"}) (dom/text "count"))
          (dom/td (dom/style {"width" "10em"}) (dom/text "type")))
        (dom/tbody
          (dom/tr
            (dom/td (dom/text (str x)))
            (dom/td (dom/text (if (odd? x)
                                ~@(pr-str (type x))         ; ~@ marks client/server transfer
                                (pr-str (type x)))))))))))

(p/defn DataViewer [title ms]
  (let [ks (u/all-keys ms)]
    (dom/h1 (dom/text title))
    (dom/table
      (dom/thead
        (dom/for [k ks]
          (dom/td (dom/style {"min-width" "5em"}) (dom/text k))))
      (dom/tbody
        (dom/for [m ms]
          (dom/tr
            (dom/for [k ks]
              (dom/td (dom/text (m k))))))))))

(p/defn HomeScreen [!route]
  (dom/button
    (dom/text "go to other screen")
    (dom/attribute "type" "button")
    (dom/events dom/parent "click"))

  (DataViewer.
    "Last Transactions"
    ~@(q/last-transactions))
  (DataViewer.
    "Identifying Attributes"
    ~@(q/identifying-attributes))
  (DataViewer.
    "Normal Attributes"
    ~@(q/normal-attributes)))

(p/defn OtherScreen []
  (dom/button
    (dom/text "go back to home screen")
    (dom/attribute "type" "button")))

(p/defn App []
  (let [!route (atom :home)
        route  (p/watch !route)]
    (dom/div
      (condp = route
        :home (HomeScreen. !route)
        :other (OtherScreen.))

      (TimerTest.)
      (ClickMeTest.))))

(def app
  #?(:cljs
     (p/client
       (p/main
         (binding [dom/parent (dom/by-id "root")]
           (try
             (App.)
             (catch Pending _)))))))

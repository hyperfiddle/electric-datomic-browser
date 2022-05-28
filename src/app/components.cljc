(ns app.components
  (:require [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [hyperfiddle.zero :as z]
            [missionary.core :as m]
            #?(:cljs [app.utils :as u])))

(p/defn Button [label watch F]
  (let [event (dom/button
                (dom/text label)
                (dom/attribute "type" "button")
                (->> (dom/events dom/parent "click")
                     (z/impulse watch)))]
    (when event
      (F. event))))

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
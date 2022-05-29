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

(p/defn DataViewer [title maps key-routes Link]
  (let [ks (u/all-keys maps)]
    (dom/h1 (dom/text title))
    (dom/table
      (dom/thead
        (dom/for [k ks]
          (dom/td (dom/style {"min-width" "5em"}) (dom/text k))))
      (dom/tbody
        (dom/for [m maps]
          (dom/tr
            (dom/for [k ks]
              (dom/td
                (if (and (contains? key-routes k) (m k))
                  (Link. (m k) {:route  (key-routes k)
                                :params (m k)})
                  (dom/text (m k)))))))))))

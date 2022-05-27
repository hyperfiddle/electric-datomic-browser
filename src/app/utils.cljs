(ns app.utils
  (:require [clojure.set :as set]))

(defn all-keys [ms]
  (reduce (fn [s v] (set/union s (set (keys v)))) #{} ms))

(ns app.utils
  (:require [clojure.set :as set]))

(defn all-keys [ms]
  (->> ms
       (reduce (fn [s v] (set/union s (set (keys v)))) #{})
       ; hacky column sorting
       (sort-by (fn [v] (condp = v
                          :db/id "0"
                          :db/ident "1"
                          :db/doc "zzz"
                          :e "0"
                          :a "1"
                          :v-ref "2"
                          :v-scalar "3"
                          :t "4"
                          (str (namespace v) "/" (name v)))))))

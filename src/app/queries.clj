(ns app.queries)

(defn last-transactions []
  [{:db/id 1 :something "1"}
   {:db/id 2}
   {:db/id 3}
   {:db/id 4}])

(defn identifying-attributes []
  [{:db/id 1 :something "1"}
   {:db/id 2}
   {:db/id 3}
   {:db/id 4}])

(defn normal-attributes []
  [{:db/id 1 :something "1"}
   {:db/id 2}
   {:db/id 3}
   {:db/id 4}])
(ns danjo.murakumo
  "Pure cljc actor boundary generated from manifest migration scaffold."
  (:require [clojure.string :as str]))

(def actor-did
  "did:web:danjo.etzhayyim.com")

(def common-gates
  [:council-charter-attestation
   :no-platform-held-key-baseline
   :no-probing-baseline
   :murakumo-only-inference-baseline
   :did-primary-baseline
   :append-only-gate-baseline
   :kotoba-only-substrate-baseline])

(defn collection
  [name]
  (str "com.etzhayyim.danjo." name))

(def cell-specs {
  :diet_statement_index {:legacy-cell "diet-statement-index"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "diet_statement_index")]
     :required-gates common-gates
     :trigger "manifest cell diet_statement_index"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
  :procurement_graph {:legacy-cell "procurement-graph"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "procurement_graph")]
     :required-gates common-gates
     :trigger "manifest cell procurement_graph"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
  :budget_ledger {:legacy-cell "budget-ledger"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "budget_ledger")]
     :required-gates common-gates
     :trigger "manifest cell budget_ledger"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
  :crossref_engine {:legacy-cell "crossref-engine"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "crossref_engine")]
     :required-gates common-gates
     :trigger "manifest cell crossref_engine"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
  :statement_consistency {:legacy-cell "statement-consistency"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "statement_consistency")]
     :required-gates common-gates
     :trigger "manifest cell statement_consistency"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
  :oversight_report {:legacy-cell "oversight-report"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "oversight_report")]
     :required-gates common-gates
     :trigger "manifest cell oversight_report"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
})

(defn safe-rkey
  [s]
  (let [clean (-> (str s)
                  (str/replace #"^did:web:" "")
                  (str/replace #"[^A-Za-z0-9._~-]" "-"))]
    (if (str/blank? clean) "unknown" clean)))

(defn gate-value
  [attestations gate]
  (or (get attestations gate)
      (get attestations (name gate))
      (when (set? attestations) (attestations gate))
      (when (set? attestations) (attestations (name gate)))))

(defn missing-gates
  [spec attestations]
  (->> (:required-gates spec)
       (remove #(boolean (gate-value attestations %)))
       vec))

(defn put-record-effect
  [collection rkey record]
  {:op :mst/put-record
   :actor actor-did
   :collection collection
   :rkey rkey
   :record record})

(defn records-for
  [spec {:keys [records record computed-at request-id]
         :as input}]
  (let [input-records (cond
                        (map? records) records
                        (some? record) {0 record}
                        :else {})
        base {:actorDid actor-did
              :computedAt computed-at
              :legacyCell (:legacy-cell spec)
              :phase (:phase spec)
              :requestId request-id
              :actorBoundary "cljc-migration-scaffold"
              :scaffold true
              :constitutionalStatus "attested-plan"}]
    (map-indexed
     (fn [idx coll]
       (let [record* (merge {:$type coll}
                            base
                            (or (get input-records coll)
                                (get input-records idx)
                                {}))
             rkey (safe-rkey (or (:rkey record*)
                                 (get record* "rkey")
                                 (:tid record*)
                                 request-id
                                 (str (:legacy-cell spec) "-" idx)))]
         {:collection coll
          :record record*
          :rkey rkey}))
     (:collections spec))))

(defn cell-plan
  [cell-key {:keys [attestations] :as input}]
  (let [spec (get cell-specs cell-key)]
    (when-not spec
      (throw (ex-info "unknown cell" {:cell cell-key})))
    (let [missing (missing-gates spec attestations)]
      (merge
       {:cell cell-key
        :legacy-cell (:legacy-cell spec)
        :actor actor-did
        :phase (:phase spec)
        :murakumo-node (:murakumo-node spec)
        :trigger (:trigger spec)
        :ceiling (:ceiling spec)
        :required-gates (:required-gates spec)
        :missing-gates missing}
       (if (seq missing)
         {:status :blocked
          :effects []}
         (let [planned-records (records-for spec input)]
           {:status :ready
            :records (vec planned-records)
            :effects (mapv (fn [{:keys [collection record rkey]}]
                             (put-record-effect collection rkey record))
                           planned-records)}))))))

(defn all-cell-plans
  [input]
  (into {}
        (map (fn [cell-key] [cell-key (cell-plan cell-key input)]))
        (keys cell-specs)))

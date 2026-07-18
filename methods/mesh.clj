;; mesh.clj — danjo 弾正 R1 KOTOBA Mesh observatory + ingest orchestrator (Clojure / bb).
;;
;; The mesh-hosting face of actor:danjo (public-accountability oversight). Observatory
;; on-kse pattern (ADR-2606230001 §4). As of ADR-2607180900 (R1 ingest trio, Founder 1/1
;; bootstrap ratification) this component is the LIVE R1 entrypoint: on each observe it
;; orchestrates the R1 ingest trio —
;;
;;   • procurement heartbeat (autorun.cljc, offline pre-published corpus → discrepancy obs)
;;   • REVENUE beat          (revenue_ledger.clj, real JP revenue/tax → per-yen trace;
;;                            answers 「源泉所得税 / 復興特別所得税 はどこに使われるか」 honestly)
;;   • DIET ingest           (diet_beat.cljc, jp_kokkai 国会会議録 → EAVT datoms)
;;   • procurement/budget    (ingest_status.cljc, :awaiting-w3-fetcher — jp_chotatsu/jp_yosan
;;                            are W3 work; honest stubs, no fabrication G8)
;;
;; — and publishes the resulting heads + per-yen trace summary + ingest status to the mesh
;; (kqe-assert!, best-effort: no-ops when unbound under plain bb). The censor's EYE only:
;; every persisted datom is a FACTUAL public-record reference, never a verdict (G4).
;;
;; Env-var gate DANJO_R1_COUNCIL_RATIFY_TX_HASH at observe entry — the live-activation
;; switch (ADR-2607180900). The three datom logs stay in their existing, separately-tested
;; formats (procurement: danjo.methods.kotoba string-key tx; revenue: revenue-ledger
;; keyword-key tx) — this orchestrator runs both and reports both heads without merging them.
(ns danjo
  "R1 KOTOBA Mesh observatory + ingest orchestrator. See file header."
  (:require [danjo.methods.autorun :as autorun]
            [danjo.methods.diet-beat :as diet]
            [danjo.methods.procurement-beat :as proc]
            [danjo.methods.ingest-status :as ingest]
            [clojure.string :as str]
            [clojure.java.io :as io]))

;; ── env-var gate (ADR-2607180900 R1 ratification) ──────────────────────────────
(defn- assert-r1-ratified! []
  (when-not (System/getenv "DANJO_R1_COUNCIL_RATIFY_TX_HASH")
    (throw (ex-info "danjo R1 not ratified: DANJO_R1_COUNCIL_RATIFY_TX_HASH unset (ADR-2607180900)"
                    {:gate "R1-ratification" :adr "2607180900" :cell "danjo-mesh-observe"}))))

;; ── revenue_ledger.clj has a pre-existing ns/path split (ns root.danjo.methods.* vs
;;    path danjo/methods/*), so require won't resolve it — load-file + ns-resolve is the
;;    bridge. Resolved lazily on first observe so the orchestration stays load-order-tolerant.
(def ^:private here
  (or (some-> *file* io/file .getAbsoluteFile .getParentFile)
      (io/file "methods")))

(defn- load-revenue-ledger! []
  (load-file (str (io/file here "revenue_ledger.clj")))
  (deref (ns-resolve 'root.danjo.methods.revenue-ledger 'run-cycle!)))

(defn- best-effort-kqe!
  "Publish to the mesh when running inside the kotoba runtime (kqe-assert! bound); no-op
  under plain bb so observe stays locally testable."
  [entity attr value]
  (when (resolve 'kqe-assert!)
    ((resolve 'kqe-assert!) "danjo" entity attr value)))

;; ── the R1 observe: run all three beats, return + publish the summary ──────────
(defn observe
  "One R1 heartbeat: gate → procurement + revenue + diet beats → ingest status → summary.
  Returns a map and best-effort publishes its heads to the mesh. Offline-deterministic."
  []
  (assert-r1-ratified!)
  (let [run-rev-cycle! (load-revenue-ledger!)
        procurement (autorun/run-cycle 1)                 ; offline pre-published corpus → obs
        revenue     (run-rev-cycle! {:tx-id "r1-rev" :as-of 20260718}) ; real JP revenue → per-yen trace
        diet-res    (diet/beat {:tx-id "r1-diet" :as-of 20260718})
        proc-res    (proc/beat {:tx-id "r1-proc" :as-of 20260718})   ; jp_chotatsu 落札実績 → procurement EAVT
        proc-status (ingest/procurement-status)
        bud-status  (ingest/budget-status)
        summary {:cell "danjo-R1-observe"
                 :procurement {:head (get procurement "cid") :observations (get procurement "observations")}
                 :procurement-graph {:head (:head proc-res) :records (:records proc-res)}
                 :revenue     {:head (:head-cid revenue)
                               :traces (:traces revenue)
                               :per-yen-honesty "earmarked only traceable; 一般会計 fungible (G4-structural)"}
                 :diet        {:head (:head diet-res) :records (:records diet-res)}
                 :ingest      {:procurement proc-status :budget bud-status}
                 :non-adjudicating true}]
    (best-effort-kqe! "r1-head" "procurement" (get-in summary [:procurement :head]))
    (best-effort-kqe! "r1-head" "procurement-graph" (get-in summary [:procurement-graph :head]))
    (best-effort-kqe! "r1-head" "revenue"     (get-in summary [:revenue :head]))
    (best-effort-kqe! "r1-head" "diet"        (get-in summary [:diet :head]))
    summary))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))

;; ── bb CLI: run one R1 observe and print a human-readable accountability summary ──
(defn -main [& _]
  (let [s (observe)
        rev-traces (get-in s [:revenue :traces])]
    (println "# danjo 弾正 — R1 public-accountability observe (founder 1/1, ADR-2607180900)")
    (println "  procurement head:" (subs (str (get-in s [:procurement :head])) 0
                                         (min 18 (count (str (get-in s [:procurement :head]))))) "…"
             "· observations:" (get-in s [:procurement :observations]))
    (println "  revenue head:    " (subs (str (get-in s [:revenue :head])) 0
                                         (min 18 (count (str (get-in s [:revenue :head]))))) "…")
    (when rev-traces
      (let [r (get rev-traces :reconstruction-surtax)
            w (get rev-traces :withholding-income)]
        (println "    復興特別所得税 (earmarked): per-yen traceable?" (:per-yen? r)
                 "· collected" (:collected r) "· spent" (:spent r) "· residual" (:residual r))
        (println "    源泉所得税 (一般会計):      per-yen traceable?" (:per-yen? w)
                 "· reason:" (:reason w))))
    (println "  diet head:       " (subs (str (get-in s [:diet :head])) 0
                                         (min 18 (count (str (get-in s [:diet :head]))))) "…"
             "· records:" (get-in s [:diet :records]))
    (println "  procurement-graph head (jp_chotatsu 落札実績):"
             (subs (str (get-in s [:procurement-graph :head])) 0
                   (min 18 (count (str (get-in s [:procurement-graph :head]))))) "…"
             "· awards:" (get-in s [:procurement-graph :records]))
    (println "  procurement cell:" (:reason (get-in s [:ingest :procurement]))
             "· fetcher" (:fetcher (get-in s [:ingest :procurement])))
    (println "  budget cell:     " (:reason (get-in s [:ingest :budget]))
             "· w3" (:w3 (get-in s [:ingest :budget])))
    (println "  · the censor's EYE, never the SWORD — non-adjudicating (G4) · live broadcast stays G7-gated")))

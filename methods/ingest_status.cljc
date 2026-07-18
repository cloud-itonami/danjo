(ns danjo.methods.ingest-status
  "ingest_status.cljc — 弾正 (danjo) R1 procurement/budget ingest status (honest axis state).
  ADR-2605301600 + ADR-2607180900 + ADR-2605263900 W3.

  procurement_graph (政府調達): the jp_chotatsu fetcher (70-tools/e7m-dataset/.../jp_chotatsu.py)
  is NOW IMPLEMENTED, and procurement_beat.cljc projects its output → kotoba EAVT. The cell runs
  on a representative fixture (G8 honest) until the operator runs the fetcher against p-portal.go.jp
  + IPFS-pins the corpus. budget_ledger (予算書) still awaits its jp_yosan W3 fetcher.

  These are PURE observability status fns (no I/O, no data emission, no fabrication surface) so
  they carry NO R1 gate themselves — the live-activation gate lives on the data-persisting beats
  (diet_beat / procurement_beat / mesh observe). Pure + stdlib only."
  (:require [clojure.string :as str]))

(defn procurement-status
  "R1 procurement_graph cell status. The jp_chotatsu fetcher is implemented (W3); procurement_beat
  projects its output → kotoba EAVT. Reports the operator step still pending (live pull + pin).
  :datoms 0 here = no LIVE-pinned procurementRecord corpus yet (the beat runs on a representative
  fixture meanwhile — G8 honest, never asserted as live data)."
  []
  {:cell "DanjoProcurementGraph" :appended false :datoms 0
   :reason :fetcher-landed-awaiting-operator-pull
   :fetcher "70-tools/e7m-dataset/src/e7m_dataset/fetchers/jp_chotatsu.py"
   :beat "20-actors/danjo/methods/procurement_beat.cljc"
   :next-step "operator: run jp_chotatsu (local-source or network) → IPFS pin → procurement_beat consumes live corpus"
   :fixture "data/gov-procurement-fixture.jp.edn (representative — not live-pinned)"
   :gates {:G3 :passive-only :G8 :no-fabrication}
   :server-held-key false})

(defn budget-status
  "R1 budget_ledger cell status. jp_yosan (予算書) fetcher is still W3 work; only a representative
  seed (data/gov-fiscal-seed.jp.json, marked :representative) exists, not a live-pinned
  budgetRecord corpus. This cell appends nothing until W3. Returns a status map (G8)."
  []
  {:cell "DanjoBudgetLedger" :appended false :datoms 0
   :reason :awaiting-w3-fetcher :w3 "jp_yosan"
   :seed "data/gov-fiscal-seed.jp.json (representative — not live-pinned)"
   :gates {:G3 :passive-only :G8 :no-fabrication}
   :server-held-key false})

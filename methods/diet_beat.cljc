(ns danjo.methods.diet-beat
  "diet_beat.cljc — 弾正 (danjo) R1 DIET ingest beat (国会会議録 → kotoba EAVT). ADR-2605301600
  + ADR-2607180900 (R1 ingest trio, Founder 1/1 bootstrap ratification).

  The R1 ingest cell for gov.dataset.parliamentRecord (JP 国会会議録). Projects the
  pre-published, IPFS-pinned jp_kokkai_kaigiroku sensor corpus into kotoba EAVT datoms
  (gov-official ↔ diet-statement ↔ cross-reference-link) and appends ONE content-addressed
  transaction to the local append-only danjo.diet.kotoba.edn log.

  Constitutional posture holds by construction (the censor's EYE, never the SWORD):
    G3 — PASSIVE: reads ONLY the offline pre-published fixture/corpus; never fetches a portal.
    G4 — NON-adjudicating: a diet statement is indexed as a FACTUAL public-record reference
          (who spoke, when, in what capacity); no verdict token is representable. Every entity
          carries :danjo.obs/non-adjudicating true.
    G5 — every diet-statement cites ≥2 source CIDs (its payloadCid + the dataset manifest CID).
    G12 — read-only: emits datoms only; never mutates an upstream record.

  Distinct from autorun.cljc (procurement/observation) and revenue_ledger.clj (revenue):
  this is the DIET axis of the R1 ingest trio. procurement/budget stay W3-stubbed
  (methods/ingest_status.cljc) until jp_chotatsu / jp_yosan fetchers land.

  Pure projection + file I/O behind #?(:clj …); runs under bb and clojure. Deterministic
  (caller supplies tx-id + as-of). Env-var gate DANJO_R1_COUNCIL_RATIFY_TX_HASH at the beat
  entry — refuses until the Founder/Council ratification (ADR-2607180900) is set in the
  cell-runner env."
  (:require [danjo.methods.kotoba :as kotoba]
            [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.java.io :as io])))

;; ── env-var gate (ADR-2607180900 R1 ratification) ──────────────────────────────
#?(:clj
   (defn- assert-r1-ratified!
     "Refuse to run until the R1 Council/Founder ratification env-var is set in the
     cell-runner env (ADR-2607180900). The gate is the live-activation switch — unset ⇒
     the cell is R0-inert by construction, exactly as the master ADR §roadmap requires."
     []
     (when-not (System/getenv "DANJO_R1_COUNCIL_RATIFY_TX_HASH")
       (throw (ex-info "danjo R1 not ratified: DANJO_R1_COUNCIL_RATIFY_TX_HASH unset (ADR-2607180900)"
                       {:gate "R1-ratification" :adr "2607180900" :cell "DanjoDietStatementIndex"})))))

;; ── pure projection: sensor fixture → EAVT datoms ──────────────────────────────
(defn- official-eid [speaker-name] (str "gov-official:" speaker-name))
(defn- diet-eid    [record-id]     (str "diet-statement:" record-id))
(defn- xref-eid    [record-id speaker-name] (str "xref:diet:" record-id ":" speaker-name))

(defn- speaker-datoms
  "gov-official entity for one speaker (keyless factual reference)."
  [r manifest-cid]
  (let [name (get r :speakerName)
        e    (official-eid name)]
    [(kotoba/add e ":gov.official/name" name)
     (kotoba/add e ":gov.official/role" (get r :speakerRole ""))
     (kotoba/add e ":gov.official/house" (get r :house ""))
     (kotoba/add e ":gov.official/sourcing" ":representative")
     (kotoba/add e ":danjo.obs/non-adjudicating" true)
     (kotoba/add e ":gov.official/source-record-cids" [(get r :payloadCid) manifest-cid])]))

(defn- statement-datoms
  "diet-statement entity for one record (G5: ≥2 source CIDs; G4: non-adjudicating)."
  [r manifest-cid]
  (let [rid (get r :recordId)
        e   (diet-eid rid)]
    [(kotoba/add e ":diet.statement/record-id" rid)
     (kotoba/add e ":diet.statement/session-date-utc" (get r :sessionDateUtc ""))
     (kotoba/add e ":diet.statement/legislature" "jp-kokkai")
     (kotoba/add e ":diet.statement/house" (get r :house ""))
     (kotoba/add e ":diet.statement/native-kind" (get r :nativeKind ""))
     (kotoba/add e ":diet.statement/session" (get r :session))
     (kotoba/add e ":diet.statement/issue" (get r :issue ""))
     (kotoba/add e ":diet.statement/speaker" (official-eid (get r :speakerName)))
     (kotoba/add e ":diet.statement/payload-cid" (get r :payloadCid ""))
     (kotoba/add e ":diet.statement/source-record-cids" [(get r :payloadCid) manifest-cid])
     (kotoba/add e ":diet.statement/non-adjudicating" true)
     (kotoba/add e ":danjo.obs/non-adjudicating" true)
     (kotoba/add e ":diet.statement/sourcing" ":representative")]))

(defn- xref-datoms
  "cross-reference-link edge: diet-statement ↔ the gov-official who spoke. Factual only."
  [r]
  (let [e (xref-eid (get r :recordId) (get r :speakerName))]
    [(kotoba/add e ":xref/source" (diet-eid (get r :recordId)))
     (kotoba/add e ":xref/target" (official-eid (get r :speakerName)))
     (kotoba/add e ":xref/kind" ":statement-speaker")
     (kotoba/add e ":danjo.obs/non-adjudicating" true)]))

(defn project-datoms
  "Pure: a jp_kokkai_kaigiroku fixture/corpus map → append-only EAVT datoms
  (gov-official + diet-statement + cross-reference-link per record). G4-structural:
  RAISES if any verdict token creeps into an attribute (mirrors kotoba/derived-datoms)."
  [fixture]
  (let [manifest-cid (get fixture :manifest-cid "gov.dataset.manifest:jp_kokkai_kaigiroku#unknown")
        records      (get fixture :records [])
        out (reduce (fn [out r]
                      (if-not (and (map? r) (get r :recordId) (get r :speakerName))
                        out
                        (-> out
                            (into (speaker-datoms r manifest-cid))
                            (into (statement-datoms r manifest-cid))
                            (into (xref-datoms r)))))
                    []
                    records)]
    (doseq [d out]
      (let [attr (str/lower-case (str (nth d 2)))]
        (when (some #(str/includes? attr %) kotoba/forbidden-verdict-tokens)
          (throw (ex-info (str "G4: verdict attr " (pr-str (nth d 2))
                               " is unrepresentable in a danjo diet datom")
                          {:gate "G4" :attr (nth d 2)})))))
    out))

;; ── beat: load fixture → project → append one content-addressed tx ────────────
#?(:clj
   (def ^:private fixture-default
     (let [here (some-> *file* io/file .getAbsoluteFile .getParentFile)
           data (some-> here (.getParentFile) (io/file "data"))]
       (when data (io/file data "gov-diet-fixture.jp.edn")))))

#?(:clj
   (def ^:private log-default
     (let [here (some-> *file* io/file .getAbsoluteFile .getParentFile)
           data (some-> here (.getParentFile) (io/file "data" "persisted"))]
       (when data (io/file data "danjo.diet.kotoba.edn")))))

#?(:clj
   (defn load-fixture
     "Read the offline jp_kokkai fixture EDN (passive, G3)."
     ([] (load-fixture nil))
     ([path]
      (let [f (io/file (or path (str fixture-default)))
            f (if (.exists f) f (io/file "../data/gov-diet-fixture.jp.edn"))]
        (edn/read-string (slurp f))))))

#?(:clj
   (defn beat
     "ONE R1 diet ingest beat: load fixture → project datoms → append one tx to the
     local danjo.diet.kotoba.edn log, chained on its head. Env-var gated (ADR-2607180900).
     Returns {:cell :head :datoms :appended :records}. Deterministic / resume-safe."
     ([] (beat nil))
     ([opts]
      (let [{:keys [fixture-path log-path tx-id as-of]} (merge {:tx-id "diet-beat" :as-of 0} opts)]
        (assert-r1-ratified!)
        (let [fixture (load-fixture fixture-path)
              log     (io/file (or log-path (str log-default)))
              datoms  (project-datoms fixture)
              tx      (kotoba/make-tx datoms :tx-id tx-id :as-of as-of
                                      :prev-cid (kotoba/head-cid (str log)))
              cid     (kotoba/append-tx tx (str log))]
          {:cell "DanjoDietStatementIndex" :head cid :datoms (count datoms)
           :records (count (get fixture :records [])) :appended true :server-held-key false})))))

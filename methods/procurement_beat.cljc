(ns danjo.methods.procurement-beat
  "procurement_beat.cljc — 弾正 (danjo) JP 政府調達 procurement beat (jp_chotatsu NDJSON → kotoba EAVT).
  ADR-2605301600 + ADR-2607180900 + ADR-2605263900 W3.

  The R1 procurement ingest axis. Projects jp_chotatsu fetcher output (落札実績 award records,
  procurementRecord-conformant) into kotoba EAVT datoms (contracting-authority ↔ procurement-award
  ↔ corp-entity(awardee) ↔ cross-reference-link) and appends ONE content-addressed transaction to
  the local append-only danjo.procurement.kotoba.edn log.

  Constitutional posture holds by construction (the censor's EYE, never the SWORD):
    G3 — PASSIVE: reads ONLY the offline pre-published fetcher NDJSON / representative fixture;
          never fetches the portal itself (the jp_chotatsu fetcher + operator do that, passively).
    G4 — NON-adjudicating: an award is indexed as a FACTUAL public-record reference (who awarded
          whom, how much, when); no verdict token is representable. Every entity carries
          :danjo.obs/non-adjudicating true. NO single-bidder / opacity conclusion is drawn here —
          that is R2 crossref_engine territory (Council-gated).
    G5 — every procurement-award cites ≥2 source CIDs (its payloadCid + the dataset manifest CID).
    G12 — read-only: emits datoms only; never mutates an upstream record.

  Distinct from diet_beat (parliamentRecord) + revenue_beat (revenue): this is the PROCUREMENT axis.
  Input = jp_chotatsu NDJSON shape ({:noticeId :recordKind :contractingAuthority :awardeeName
  :awardeeLocalId :awardAmountLocal :awardDateUtc :payloadCid}). awardeeLei is null until the
  gleif_lei fetcher + corp.leiReference cross-ref land (separate work).

  Pure projection + file I/O behind #?(:clj …); runs under bb and clojure. Deterministic
  (caller supplies tx-id + as-of). Env-var gate DANJO_R1_COUNCIL_RATIFY_TX_HASH at the beat entry."
  (:require [danjo.methods.kotoba :as kotoba]
            [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.java.io :as io])))

;; ── env-var gate (ADR-2607180900 R1 ratification) ──────────────────────────────
#?(:clj
   (defn- assert-r1-ratified! []
     (when-not (System/getenv "DANJO_R1_COUNCIL_RATIFY_TX_HASH")
       (throw (ex-info "danjo R1 not ratified: DANJO_R1_COUNCIL_RATIFY_TX_HASH unset (ADR-2607180900)"
                       {:gate "R1-ratification" :adr "2607180900" :cell "DanjoProcurementGraph"})))))

;; ── pure projection: jp_chotatsu records → EAVT datoms ────────────────────────
(defn- authority-eid [name] (str "contracting-authority:" name))
(defn- awardee-eid    [name] (str "corp-entity:" name))
(defn- award-eid      [notice-id] (str "procurement-award:" notice-id))
(defn- xref-eid       [a b] (str "xref:proc:" a ":" b))

(defn- authority-datoms
  "contracting-authority entity (keyless factual reference)."
  [r manifest-cid]
  (let [name (get r :contractingAuthority)
        e    (authority-eid name)]
    [(kotoba/add e ":contracting.authority/name" name)
     (kotoba/add e ":contracting.authority/source-record-cids" [(get r :payloadCid) manifest-cid])
     (kotoba/add e ":danjo.obs/non-adjudicating" true)
     (kotoba/add e ":contracting.authority/sourcing" ":representative")]))

(defn- awardee-datoms
  "corp-entity entity for the awardee (LEI absent until gleif cross-ref lands)."
  [r manifest-cid]
  (let [name (get r :awardeeName)
        e    (awardee-eid name)]
    [(kotoba/add e ":corp.entity/name" name)
     (kotoba/add e ":corp.entity/local-id" (get r :awardeeLocalId ""))
     (kotoba/add e ":corp.entity/source-record-cids" [(get r :payloadCid) manifest-cid])
     (kotoba/add e ":danjo.obs/non-adjudicating" true)
     (kotoba/add e ":corp.entity/sourcing" ":representative")]))

(defn- award-datoms
  "procurement-award entity (G5: ≥2 source CIDs; G4: non-adjudicating)."
  [r manifest-cid]
  (let [nid (get r :noticeId)
        e   (award-eid nid)]
    [(kotoba/add e ":procurement.award/notice-id" nid)
     (kotoba/add e ":procurement.award/record-kind" (get r :recordKind "award"))
     (kotoba/add e ":procurement.award/title" (get r :title ""))
     (kotoba/add e ":procurement.award/contracting-authority" (authority-eid (get r :contractingAuthority)))
     (kotoba/add e ":procurement.award/awardee" (awardee-eid (get r :awardeeName)))
     (kotoba/add e ":procurement.award/amount-jpy" (get r :awardAmountLocal 0))
     (kotoba/add e ":procurement.award/currency-iso4217" (get r :currencyIso4217 "JPY"))
     (kotoba/add e ":procurement.award/award-date-utc" (get r :awardDateUtc ""))
     (kotoba/add e ":procurement.award/payload-cid" (get r :payloadCid ""))
     (kotoba/add e ":procurement.award/source-record-cids" [(get r :payloadCid) manifest-cid])
     (kotoba/add e ":procurement.award/non-adjudicating" true)
     (kotoba/add e ":danjo.obs/non-adjudicating" true)
     (kotoba/add e ":procurement.award/sourcing" ":representative")]))

(defn- xref-datoms
  "cross-reference-link edges: authority↔award + award↔awardee. Factual only."
  [r]
  (let [nid  (get r :noticeId)
        auth (get r :contractingAuthority)
        awd  (get r :awardeeName)
        e1   (xref-eid nid "auth")
        e2   (xref-eid nid "awardee")]
    [(kotoba/add e1 ":xref/source" (award-eid nid))
     (kotoba/add e1 ":xref/target" (authority-eid auth))
     (kotoba/add e1 ":xref/kind" ":award-authority")
     (kotoba/add e1 ":danjo.obs/non-adjudicating" true)
     (kotoba/add e2 ":xref/source" (award-eid nid))
     (kotoba/add e2 ":xref/target" (awardee-eid awd))
     (kotoba/add e2 ":xref/kind" ":award-awardee")
     (kotoba/add e2 ":danjo.obs/non-adjudicating" true)]))

(defn project-datoms
  "Pure: jp_chotatsu award records → append-only EAVT datoms (authority + awardee + award +
  cross-reference-link per record). G4-structural: RAISES if any verdict token creeps into an
  attribute. Skips records missing noticeId OR contractingAuthority OR awardeeName."
  [records manifest-cid]
  (let [out (reduce
             (fn [out r]
               (if-not (and (map? r)
                            (get r :noticeId)
                            (get r :contractingAuthority)
                            (get r :awardeeName))
                 out
                 (-> out
                     (into (authority-datoms r manifest-cid))
                     (into (awardee-datoms r manifest-cid))
                     (into (award-datoms r manifest-cid))
                     (into (xref-datoms r)))))
             []
             records)]
    (doseq [d out]
      (let [attr (str/lower-case (str (nth d 2)))]
        (when (some #(str/includes? attr %) kotoba/forbidden-verdict-tokens)
          (throw (ex-info (str "G4: verdict attr " (pr-str (nth d 2))
                               " is unrepresentable in a danjo procurement datom")
                          {:gate "G4" :attr (nth d 2)})))))
    out))

;; ── beat: load fixture → project → append one content-addressed tx ────────────
#?(:clj
   (def ^:private fixture-default
     (let [here (some-> *file* io/file .getAbsoluteFile .getParentFile)
           data (some-> here (.getParentFile) (io/file "data"))]
       (when data (io/file data "gov-procurement-fixture.jp.edn")))))

#?(:clj
   (def ^:private log-default
     (let [here (some-> *file* io/file .getAbsoluteFile .getParentFile)
           data (some-> here (.getParentFile) (io/file "data" "persisted"))]
       (when data (io/file data "danjo.procurement.kotoba.edn")))))

#?(:clj
   (defn load-fixture
     "Read the offline jp_chotatsu fixture EDN (passive, G3). Returns the records seq."
     ([] (load-fixture nil))
     ([path]
      (let [f (io/file (or path (str fixture-default)))
            f (if (.exists f) f (io/file "../data/gov-procurement-fixture.jp.edn"))]
        (:records (edn/read-string (slurp f)))))))

#?(:clj
   (defn beat
     "ONE R1 procurement beat: load fixture → project datoms → append one tx to the local
     danjo.procurement.kotoba.edn log, chained on its head. Env-var gated (ADR-2607180900).
     Returns {:cell :head :datoms :appended :records}. Deterministic / resume-safe."
     ([] (beat nil))
     ([opts]
      (let [{:keys [fixture-path log-path tx-id as-of]} (merge {:tx-id "procurement-beat" :as-of 0} opts)]
        (assert-r1-ratified!)
        (let [records (load-fixture fixture-path)
              manifest-cid "gov.dataset.manifest:jp_chotatsu#fixture-representative"
              log     (io/file (or log-path (str log-default)))
              datoms  (project-datoms records manifest-cid)
              tx      (kotoba/make-tx datoms :tx-id tx-id :as-of as-of
                                      :prev-cid (kotoba/head-cid (str log)))
              cid     (kotoba/append-tx tx (str log))]
          {:cell "DanjoProcurementGraph" :head cid :datoms (count datoms)
           :records (count records) :appended true :server-held-key false})))))

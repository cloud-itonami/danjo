# danjo 弾正 — operator quickstart

Everything below was executed on the tree this file landed in, and the quoted output is what
it printed (commit `0a6d435`..`e93fc76`, 2026-09-11). If a step here disagrees with the tree,
the tree is right and this file is stale — say so in `MATURITY.md` and fix it here.

danjo is a **non-adjudicating** public-accountability actor: it cross-references *already
published* government records and emits factual, source-cited observations into an
append-only, content-addressed Datom log. It never says "wrongdoing" (G4). Nothing in this
quickstart touches the network (G3) — every input is a committed fixture under `data/`.

## 0. What you need

- `bb` (babashka) on `PATH`. The tests and every command below run under bb.
- **Not the JVM.** `CLJ_RUNNER=clojure ./run_tests_clj.sh` is refused (exit 2) with the
  reason: JVM Clojure allows reader conditionals only for `.cljc` paths, and this repo's
  source is `.cljk` since the 2026-09-11 rename (`cljk-origin.edn`).
- Optional, for one suite: a checkout of `etzhayyim/root` (the danjo lexicons live there
  under `00-contracts/lexicons/com/etzhayyim/danjo/`). In the west layout it is the sibling
  `orgs/etzhayyim/root` and is found automatically; elsewhere set `DANJO_CONTRACTS_ROOT`.

All paths are relative to the repo root unless a step says `cd methods`.

## 1. Run the tests

```bash
./run_tests_clj.sh > /tmp/danjo-tests.log; echo EXIT=$?
tail -1 /tmp/danjo-tests.log
```

```
EXIT=0
── danjo clj: ALL 16 suites green (10 load-file suites, 6 clojure.test suites) ──
```

Without the lexicon checkout the last line is instead

```
SKIPPED: test-charter-gates (no lexicon dir: set DANJO_CONTRACTS_ROOT to an etzhayyim/root checkout)
── danjo clj: ALL 15 suites green (10 load-file suites, 5 clojure.test suites; 1 skipped) ──
```

— skipped is printed as skipped, never counted green. The runner owns every exit code:
a failing assertion anywhere is exit 1, a suite that ran zero tests is `REFUSED` exit 2
(both were provoked on 2026-09-11; see `docs/adr/0001-*`). Redirect to a file first and
read `$?` from the runner, not from `tail`.

The ten load-file suites print `── <name>: N checks, M failures ──` each; the six
`clojure.test` suites print `Ran N tests containing M assertions.` If you want one suite:

```bash
cd methods && bb -e '(load-file "test_revenue_ledger.cljk")' | tail -1
```

```
── revenue_ledger: 25 checks, 0 failures ──
```

## 2. Three autonomous heartbeats into a scratch log

The procurement heartbeat (`methods/autorun.cljk`): load the committed corpus
(`data/corpus.seed.edn`, 11 procurement records) and the open method-pack
(`methods/v1-jp-seed.edn`, 7 methods, of which `single-bidder-streak` is implemented), run
the detectors, append one content-addressed transaction per cycle. `--fresh` starts the
scratch log over; `--log` keeps it out of `data/persisted/`.

```bash
cd methods
bb -e '(load-file "kotoba.cljk") (load-file "analyze.cljk") (load-file "autorun.cljk")
       (danjo.methods.autorun/-main "--cycles" "3" "--log" "/tmp/danjo-quickstart.datoms.kotoba.edn" "--fresh")'
```

```
# danjo — AUTONOMOUS public-accountability cross-reference over the kotoba Datom log (offline corpus, LOCAL persist; live fetch / named-party publish stays G3/G10-gated)

  ♥ cycle 1: 11 procurement records / 7 open methods → 1 discrepancy observation(s) +84 datoms → cid b99bf42b2dcfc0…
  ♥ cycle 2: 11 procurement records / 7 open methods → 1 discrepancy observation(s) +84 datoms → cid b6f5d354ea6b54…
  ♥ cycle 3: 11 procurement records / 7 open methods → 1 discrepancy observation(s) +84 datoms → cid b5d1395e6de037…

  log: 3 tx · head b5d1395e6de037… · chain OK ✓ · the censor's EYE, never the SWORD — non-adjudicating (G4)
```

Those three CIDs are the ones `test-cid-pin` in `methods/test_autorun.cljk` pins. Same
inputs → same CIDs, on every run, on every machine; change one amount in the corpus and the
test goes red (provoked 2026-09-11: `48000000` → `48000001` in record 001 → `FAIL in (test-cid-pin)`).

### What the detector actually said

The one observation, as datoms (still in `methods/`):

```bash
bb -e '(load-file "kotoba.cljk")
       (let [tx (first (danjo.methods.kotoba/read-log "/tmp/danjo-quickstart.datoms.kotoba.edn"))]
         (doseq [d (get tx ":tx/datoms") :when (re-find #"danjo-obs" (str (nth d 1)))] (prn d)))'
```

```
[":db/add" "danjo-obs:single-bidder-streak:bafy-proc-001" ":danjo.obs/category" ":single-bidder-streak"]
[":db/add" "danjo-obs:single-bidder-streak:bafy-proc-001" ":danjo.obs/non-adjudicating" true]
[":db/add" "danjo-obs:single-bidder-streak:bafy-proc-001" ":danjo.obs/pattern" "6 consecutive single-bid awards from auth:jp:mlit to lei:5493ACME000000000001 within the method window"]
[":db/add" "danjo-obs:single-bidder-streak:bafy-proc-001" ":danjo.obs/source-record-cids" ["bafy-proc-001" "bafy-proc-002" "bafy-proc-003" "bafy-proc-004" "bafy-proc-005" "bafy-proc-006"]]
[":db/add" "danjo-obs:single-bidder-streak:bafy-proc-001" ":danjo.obs/method-note-cid" "method:single-bidder-streak:a36805a76dc6"]
[":db/add" "danjo-obs:single-bidder-streak:bafy-proc-001" ":danjo.obs/known-false-positive-modes" ["Genuinely specialized / sole-source-justified requirement (proprietary spare parts, single accredited supplier, national-security exemption) — single-bidder by lawful necessity, not by steering." "Small or thin supplier market for the category — few eligible bidders exist." "Source records mis-coded single-bid where multiple bids were actually received (upstream data-quality, not a real streak)." "Framework / call-off agreements that legitimately re-award to a pre-qualified vendor."]]
[":db/add" "danjo-obs:single-bidder-streak:bafy-proc-001" ":danjo.obs/sourcing" ":representative"]
```

Read the shape, because it *is* the constitution: `non-adjudicating true` (G4), six source
record CIDs (G5 asks for ≥2), a method-note CID (G6 — the detector itself is public), and the
false-positive modes travel *with* the observation. There is no attribute a verdict could
live in; `test_kotoba.cljk` checks every derived attribute name against the forbidden-token list.

### Verify the chain, then break it

```bash
bb -e '(load-file "kotoba.cljk") (prn (danjo.methods.kotoba/verify-chain "/tmp/danjo-quickstart.datoms.kotoba.edn"))'
cp /tmp/danjo-quickstart.datoms.kotoba.edn /tmp/danjo-tampered.datoms.kotoba.edn
perl -pi -e 'if ($. == 3) { s/48000000/48000001/ }' /tmp/danjo-tampered.datoms.kotoba.edn   # line 3 = tx 2
bb -e '(load-file "kotoba.cljk") (prn (danjo.methods.kotoba/verify-chain "/tmp/danjo-tampered.datoms.kotoba.edn"))'
```

```
{"ok" true, "length" 3, "broken_at" -1}
{"ok" false, "length" 3, "broken_at" 1}
```

`broken_at` is the 0-based index of the first transaction whose stored CID no longer matches
its content — the edit was in the second tx. A log that verifies is a log nobody edited by hand.

## 3. The R1 observe (all three beats) — and the gate in front of it

`methods/mesh.cljk` is the R1 orchestrator: procurement heartbeat + revenue per-yen trace +
Diet-record beat + jp_chotatsu procurement graph, each into its own log under
`data/persisted/` (all four are gitignored). It is gated on Council ratification
(ADR-2607180900): without the env var it refuses. Run it from the **repo root**.

```bash
cd "$(git rev-parse --show-toplevel)"
CHAIN='(load-file "methods/kotoba.cljk") (load-file "methods/analyze.cljk") (load-file "methods/autorun.cljk")
       (load-file "methods/diet_beat.cljk") (load-file "methods/procurement_beat.cljk")
       (load-file "methods/ingest_status.cljk") (load-file "methods/mesh.cljk")'
env -u DANJO_R1_COUNCIL_RATIFY_TX_HASH bb -e "$CHAIN (danjo/-main)" > /tmp/gate-closed.log 2>&1; echo EXIT=$?
grep -m1 Message /tmp/gate-closed.log
```

```
EXIT=1
Message:  danjo R1 not ratified: DANJO_R1_COUNCIL_RATIFY_TX_HASH unset (ADR-2607180900)
```

```bash
DANJO_R1_COUNCIL_RATIFY_TX_HASH=0xquickstart bb -e "$CHAIN (danjo/-main)"; echo EXIT=$?
```

```
# danjo 弾正 — R1 public-accountability observe (founder 1/1, ADR-2607180900)
  procurement head: b99bf42b2dcfc00c60 … · observations: 1
  revenue head:     b9ab36062c9d1e6a08 …
    復興特別所得税 (earmarked): per-yen traceable? true · collected 410000000000 · spent 410000000000 · residual 0
    源泉所得税 (一般会計):      per-yen traceable? false · reason: :non-earmarked-general-account
  diet head:        b0ca4b08f219c7dccf … · records: 3
  procurement-graph head (jp_chotatsu 落札実績): b6500ff1341d48338b … · awards: 3
  procurement cell: :fetcher-landed-awaiting-operator-pull · fetcher 70-tools/e7m-dataset/src/e7m_dataset/fetchers/jp_chotatsu.py
  budget cell:      :awaiting-w3-fetcher · w3 jp_yosan
  · the censor's EYE, never the SWORD — non-adjudicating (G4) · live broadcast stays G7-gated
EXIT=0
```

The heads are stable on a fresh checkout (first cycle). Run it again and the procurement head
becomes cycle 2's `b6f5d354…` — the logs append; `git status` stays clean because the four
logs are ignored. The revenue line is the honest fact this actor exists to state: an
earmarked tax (復興特別所得税 → 復興特会) traces per yen with residual 0; a general-account tax
(源泉所得税) does not, and that is an accounting property (non-affectation), not a finding.

The two `cell:` lines are status, not results: the jp_chotatsu fetcher path is a monorepo
path that is not in this repo, and the budget beat waits on a fetcher that does not exist
yet (`ingest_status.cljk` says so; `test_ingest_status.cljk` pins that it says so).

## 4. The revenue trace by itself

```bash
cd methods && bb -e '(load-file "revenue_ledger.cljk") (root.danjo.methods.revenue-ledger/-main)'
```

```
復興特別所得税 (earmarked → 復興特会):
  traceable? true  per-yen? true  collected 410000000000  spent 410000000000  residual 0
    {:step :collect, :account :general, :amount-jpy 410000000000}
    {:step :transfer, :from :general, :to :special/reconstruction, :amount-jpy 410000000000}
    {:step :outlay, :account :special/reconstruction, :program 福島再生・原子力災害復興, :cofog 06.6, :recipient-class 福島県・市町村・関連機関 (aggregate), :amount-jpy 230000000000}
    {:step :outlay, :account :special/reconstruction, :program インフラ復興・住宅再建・なりわい再生, :cofog 06.1, :recipient-class 被災自治体・事業者 (aggregate), :amount-jpy 150000000000}
    {:step :outlay, :account :special/reconstruction, :program 復興庁 行政経費・予備費, :cofog 01.1, :recipient-class 復興庁 (aggregate), :amount-jpy 30000000000}

源泉所得税 (一般会計, fungible):
  traceable? false  reason :non-earmarked-general-account
   源泉所得税の特定の1円が特定の歳出に充てられた、という会計的事実は存在しない (ノン・アフェクタシオン原則)。danjo は予算→支出の相互参照と乖離の事実指摘までに留まる。
```

The seed is `data/gov-revenue-seed.jp.edn` (representative, FY2023–2024, marked
`:representative` — **not** an authoritative budget statement; `data/REVENUE-COVERAGE.md`
carries the honest coverage numbers).

## 5. Regenerate the two coverage scorecards and check they are current

```bash
cd methods
bb -e '(load-file "coverage.cljk") (root.danjo.methods.coverage/-main)' | tail -2
git -C .. diff --stat -- data/REVENUE-COVERAGE.md          # empty = the committed file is what the code produces
bb -e '(load-file "registry_coverage.cljk") (root.danjo.methods.registry-coverage/-main "../registry/sources.seed.edn" "/tmp/danjo-registry-COVERAGE.md")' | tail -2
```

```
wrote ../data/REVENUE-COVERAGE.md
  fiscal-years: (2023 2024) | national taxes: 29 | org-actors: 8 | datoms: 137
wrote /tmp/danjo-registry-COVERAGE.md
  sources: 175 | jurisdictions: 36 | verified: 0
```

`verified: 0` is the true number: 175 fiscal sources across 36 jurisdictions are
*catalogued*, none is *verified* (G14 tiers in `registry/VERIFICATION.md`). A catalogued
source is not a verified source, and the scorecard says so rather than hiding it.

## 6. Where things are

| | |
|---|---|
| `methods/*.cljk` | implementation and tests side by side (`test_*.cljk`); ns names `danjo.methods.*` / `root.danjo.methods.*` do not match the path, which is why everything is `load-file`d — see `run_tests_clj.sh` header |
| `methods/v1-jp-seed.edn` | the open method-pack (G6): 7 detectors, thresholds are draft placeholders, `councilAttestation []` |
| `data/corpus.seed.edn` | 11 representative procurement records the heartbeat reads |
| `data/gov-*.jp.edn`, `data/jp-*.edn` | revenue / fiscal / Diet / procurement fixtures and the tax + org registries |
| `data/persisted/` | the append-only local logs (ignored) plus the committed `danjo-observations.kotoba.edn` report |
| `registry/sources.seed.edn` | 175 worldwide fiscal sources, all `unverified-seed` |
| `cljk-origin.edn` | what extension each `.cljk` had before the rename (a loader reads this; nothing in the repo does) |
| `docs/adr/` | decisions taken in this repo after it left the monorepo |
| `MATURITY.md` | the iteration ledger — what is done, what is `⏳`, and why |

What is **not** here: the Council, the lexicon JSON (etzhayyim/root), the fetchers
(`70-tools/e7m-dataset`, monorepo), and any live government data. R2/R3 (named-party
observations, oversight reports) are Council-gated and not runnable from this repo.

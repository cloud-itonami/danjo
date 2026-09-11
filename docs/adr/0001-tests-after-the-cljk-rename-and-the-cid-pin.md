# ADR 0001 — the test suite after the `.cljk` rename, and what the runner had been hiding

- **Status**: accepted (2026-09-11)
- **Scope**: this repo (`cloud-itonami/danjo`). Workspace-level authority for the rename is
  `adr-2609111500-cljk-rename-all-clojure-source` in `com-junkawasaki/root`; this ADR records
  only what that rename did *here* and the two decisions it forced.

## Context — measured, not read

`77c6fd9` renamed all 33 Clojure files to `.cljk` and wrote `cljk-origin.edn`. Blob contents
were unchanged, so the 22 `(load-file "x.clj")` / `"x.cljc"` calls inside `methods/` and the
14 file names in `run_tests_clj.sh` kept naming files that no longer existed. At the west pin
`2443b33` the runner reported **14/14 suites FAILED: File does not exist**.

Fixing that made the runner own its exit codes, and that surfaced an older fact. On the
pre-rename tree (`2909411`) `./run_tests_clj.sh` printed `── danjo clj: ALL suites green ──`
and exited 0 — while `test_autorun.cljc` printed `1 failures, 0 errors` twelve lines above.
Its `-main` returned the `run-tests` summary map and never called `System/exit`, so the
runner's `if "${RUN[@]}"` saw 0. The MATURITY.md entry of 2026-07-18 that says "全 14 suite
実実行 green" was written over that output. The failing assertion was
`test-cid-matches-python`, which pinned the three heartbeat tx CIDs that `python3 autorun.py`
produced over `data/corpus.seed.json`.

Why it failed, measured by diffing the datoms the two trees produce:

| tree | corpus | datom set | per-record datom order | method-note CID | tx CIDs |
|---|---|---|---|---|---|
| `b98188c` (2026-06-24) | `corpus.seed.json` | 84 datoms | JSON insertion order | `…:955ade7944f2` | the pinned three — **suite green** |
| `8d921b0` (2026-07-18) onward | `corpus.seed.edn` | the **same** 84 datoms | keyword-sorted EDN order | `…:a36805a76dc6` | different — **1 failure, hidden** |

Same facts, different bytes: the tx CID hashes the ordered datom vector and the method-note
CID hashes the method-pack, and `8d921b0` changed both encodings. The Python implementation
and the JSON corpus were removed in the same commit, so the parity claim became
unverifiable the moment it started failing.

## Decisions

1. **Suites load by explicit `load-file` chains, in dependency order, listed in the runner.**
   No runtime resolves `danjo.methods.*` from `methods/*.cljk` by classpath (nbb tries
   `[.cljs .cljc .clj]`, the JVM `[.clj .cljc]`, bb `[.bb .clj .cljc]` — the last one is in
   the error text you get when a chain is short), and the ns names never matched the path.
   A module that gains a `require` must be added to its chain; the failure is
   `Could not locate danjo/methods/<x>.bb, .clj or .cljc on classpath` — loud, never a skip.
   Provoked 2026-09-11 by dropping `analyze.cljk` from the autorun chain.
2. **The runner owns every exit code and refuses a suite that ran nothing.** clojure.test
   suites exit through the runner's own `(System/exit (cond (zero? (:test r)) 2 …))`; the
   suite's `-main` is bypassed. Load-file suites must print `N checks` with N > 0 or are
   `REFUSED` (exit 2). Provoked: a test file with no `deftest` → `REFUSED … ran zero tests`,
   exit 2; a load-file suite printing `0 checks` → `REFUSED … ran no checks`, exit 2; one
   broken assertion in `test_revenue_ledger` → exit 1.
3. **`test-cid-matches-python` becomes `test-cid-pin`** over the committed EDN inputs, with
   this history in its docstring. Parity with Python is not a claim this repo can check any
   more; that the committed corpus + method-pack still produce these three CIDs is. Provoked:
   `48000000 → 48000001` in record 001 → `FAIL in (test-cid-pin)`. The determinism test
   (`same cycles → same CIDs` within a run) was already there; the pin adds *across commits*.
4. **`test_analyze` and `test_charter_gates` join the run** (16 suites; the old list had 14
   and `run_tests.sh` — which `cd ../..`'d into a monorepo root that does not exist here —
   was the only thing that named them). `test_charter_gates` reads the four danjo lexicon
   JSON files from `etzhayyim/root`; the runner finds the west sibling
   `orgs/etzhayyim/root` or takes `DANJO_CONTRACTS_ROOT`, and without either prints
   `SKIPPED: test-charter-gates (…)` and counts 15, not 16. The test's `actor-name` is now
   the literal `"danjo"` — it was the checkout directory's name, which a worktree is not.
5. **`CLJ_RUNNER=clojure` is refused (exit 2), not attempted.** JVM Clojure enables reader
   conditionals only for paths ending in `.cljc` (`Compiler.load` / `RT.load`), so
   `load-file` on a `.cljk` containing `#?(…)` stops at `Conditional read not allowed`.
   Measured: 8 of 16 suites. The fix-forward is a mirror carrying the origin extensions from
   `cljk-origin.edn` (the shape of `scripts/cljk-classpath.cljs` in the superproject); it is
   not built here because bb reads `.cljk` as-is and is the runtime this suite has always
   defaulted to. Until it exists, the JVM path is a stated gap, not a green one.

Two adjacent defects found by walking the operator quickstart, fixed in the same series:
`revenue_ledger.cljk` resolved its seed and log from the **cwd** (from `methods/` the seed
fallback was missing; from the repo root the log was written *outside the repo* at
`../data/persisted/`); it now resolves from `*file*` like the two other beats.
`registry_coverage -main` still defaulted to `sources.seed.json`, gone since `8d921b0`.

## Consequences

- `./run_tests_clj.sh` → `ALL 16 suites green (10 load-file suites, 6 clojure.test suites)`,
  exit 0, under bb. What it prints on the two skip/refuse paths is in
  `docs/operator-quickstart.md` §1.
- The 2026-07-18 MATURITY claim of "all 14 suites green" is corrected in MATURITY.md, not
  deleted: it was true of 13 and false of 1, and the runner was what made it look true.
- The datom order inside a transaction depends on map iteration order of the loaded record.
  Within one runtime that is deterministic (the pin holds run after run); across encodings
  it is not (this ADR). A canonical datom order would make the tx CID encoding-independent.
  Not done here — it would change every CID again, and that is a decision about the log's
  identity, not about tests.

#!/usr/bin/env bash
# danjo 弾正 — Clojure test suites. Runs under bb (default, fast) or the JVM (CLJ_RUNNER=clojure).
#
# Since the .cljk rename (2026-09-11, cljk-origin.edn) no runtime resolves `danjo.methods.*`
# from methods/*.cljk by classpath — nbb tries [.cljs .cljc .clj], the JVM [.clj .cljc] — and the
# ns names (`danjo.methods.*`, `root.danjo.methods.*`) never matched the path (`methods/*`)
# anyway. So every suite is loaded by an explicit load-file chain, in dependency order, and a
# later `require` finds the namespace already present. Add a require to a module and this list
# must grow with it — the failure is loud (`Could not find namespace`), never a silent skip.
#
# This runner owns every exit code. A suite whose own -main forgets to exit non-zero (test_autorun
# hid one failing assertion from 2026-07-18 to 2026-09-11 that way; docs/adr/0001) still fails
# here, and a suite that ran zero tests is REFUSED (exit 2), not counted green.
set -uo pipefail
cd "$(dirname "$0")/methods"

RUNNER="${CLJ_RUNNER:-bb}"   # CLJ_RUNNER=clojure ./run_tests_clj.sh  to use the JVM
command -v "$RUNNER" >/dev/null 2>&1 || { echo "runner '$RUNNER' not found"; exit 127; }

# `bb -e` / `clojure -M -e`: both evaluate a form string in the cwd (methods/).
if [ "$RUNNER" = "clojure" ]; then EVAL=( clojure -M -e ); else EVAL=( bb -e ); fi

# ── load-file suites: each prints `── <name>: N checks, M failures ──` and exits 1 on failure.
LOADFILE_SUITES=( test_revenue_ledger.cljk test_ingest.cljk test_discrepancy.cljk test_taxes.cljk
                  test_org_actor.cljk test_coverage.cljk test_registry_coverage.cljk
                  test_kotoba_bridge.cljk test_budget_ledger.cljk test_kotoba.cljk )

# ── clojure.test suites: "<test ns>|<load-file chain, dependency order, test file last>"
REQUIRE_SUITES=(
  "danjo.methods.test-analyze|test_analyze.cljk"
  "danjo.methods.test-autorun|kotoba.cljk analyze.cljk autorun.cljk test_autorun.cljk"
  "danjo.methods.test-diet-beat|kotoba.cljk diet_beat.cljk test_diet_beat.cljk"
  "danjo.methods.test-ingest-status|ingest_status.cljk test_ingest_status.cljk"
  "danjo.methods.test-procurement-beat|kotoba.cljk procurement_beat.cljk test_procurement_beat.cljk"
)

# ── test_charter_gates reads the danjo lexicons from the etzhayyim monorepo
# (00-contracts/lexicons/com/etzhayyim/danjo). Default: the west sibling checkout
# orgs/etzhayyim/root; override with DANJO_CONTRACTS_ROOT. Absent = SKIPPED, printed as such.
CONTRACTS_ROOT="${DANJO_CONTRACTS_ROOT:-$(cd ../../../etzhayyim/root 2>/dev/null && pwd)}"
if [ -n "$CONTRACTS_ROOT" ] && [ -f "$CONTRACTS_ROOT/00-contracts/lexicons/com/etzhayyim/danjo/methodNote.json" ]; then
  export DANJO_CONTRACTS_ROOT="$CONTRACTS_ROOT"
  REQUIRE_SUITES+=( "danjo.methods.test-charter-gates|test_charter_gates.cljk" )
  skipped=""
else
  skipped="test-charter-gates (no lexicon dir: set DANJO_CONTRACTS_ROOT to an etzhayyim/root checkout)"
fi

fail=0; refused=0; ran=0

for s in "${LOADFILE_SUITES[@]}"; do
  out="$(mktemp)"
  "${EVAL[@]}" "(load-file \"$s\")" >"$out" 2>&1; rc=$?
  cat "$out"
  n="$(grep -oE '── [^:]+: [0-9]+ checks' "$out" | grep -oE '[0-9]+ checks' | awk '{print $1}' | tail -1)"
  if [ "$rc" -ne 0 ]; then echo "FAILED: $s (exit $rc)"; fail=1
  elif [ -z "$n" ] || [ "$n" -eq 0 ]; then echo "REFUSED: $s ran no checks (green for having found nothing)"; refused=1
  else ran=$((ran + 1)); fi
  rm -f "$out"
done

for spec in "${REQUIRE_SUITES[@]}"; do
  ns="${spec%%|*}"; chain="${spec#*|}"
  forms="(require 'clojure.test)"
  for f in $chain; do forms="$forms (load-file \"$f\")"; done
  # exit 2 = zero tests ran; 1 = failures/errors; 0 = green. The suite's own -main is bypassed.
  forms="$forms (let [r (clojure.test/run-tests '$ns)]
                  (System/exit (cond (zero? (:test r)) 2
                                     (pos? (+ (:fail r) (:error r))) 1
                                     :else 0)))"
  "${EVAL[@]}" "$forms"; rc=$?
  case "$rc" in
    0) ran=$((ran + 1)) ;;
    2) echo "REFUSED: $ns ran zero tests"; refused=1 ;;
    *) echo "FAILED: $ns (exit $rc)"; fail=1 ;;
  esac
done

[ -n "$skipped" ] && echo "SKIPPED: $skipped"
suites=$(( ${#LOADFILE_SUITES[@]} + ${#REQUIRE_SUITES[@]} ))
if [ "$fail" -ne 0 ]; then echo "── danjo clj: FAILURES above ──"; exit 1
elif [ "$refused" -ne 0 ]; then echo "── danjo clj: REFUSED (a suite ran nothing) ──"; exit 2
else echo "── danjo clj: ALL ${suites} suites green (${#LOADFILE_SUITES[@]} load-file suites, ${#REQUIRE_SUITES[@]} clojure.test suites${skipped:+; 1 skipped}) ──"; exit 0
fi

#!/usr/bin/env bash
# danjo 弾正 — Clojure test suites (revenue ledger). Runs under bb (fast) or clojure (JVM).
# Each suite prints its own count and exits non-zero on failure; this aggregates.
set -uo pipefail
cd "$(dirname "$0")/methods"

RUNNER="${CLJ_RUNNER:-bb}"   # CLJ_RUNNER=clojure ./run_tests_clj.sh  to use the JVM
command -v "$RUNNER" >/dev/null 2>&1 || { echo "runner '$RUNNER' not found"; exit 127; }

SUITES=( "test_revenue_ledger.clj" "test_ingest.clj" "test_discrepancy.clj" "test_taxes.clj" "test_org_actor.clj" "test_coverage.clj" "test_registry_coverage.clj" "test_kotoba_bridge.clj" "test_budget_ledger.clj" "test_kotoba.clj" "test_autorun.cljc" "test_diet_beat.cljc" "test_ingest_status.cljc" "test_procurement_beat.cljc" )

# require-based suites (:require danjo.methods.* — test_autorun / test_diet_beat / test_ingest_status)
# need 20-actors/ on the classpath for `danjo/methods/*.cljc` to resolve as `danjo.methods.*`.
# The load-file-based siblings need no special classpath.
fail=0
for s in "${SUITES[@]}"; do
  case "$s" in
    test_autorun.cljc|test_diet_beat.cljc|test_ingest_status.cljc|test_procurement_beat.cljc)
      if [ "$RUNNER" = "clojure" ]; then RUN=( clojure -Sdeps '{:paths ["." "../.."]}' -M "$s" ); else RUN=( "$RUNNER" -cp "../.." "$s" ); fi ;;
    *)
      if [ "$RUNNER" = "clojure" ]; then RUN=( clojure -M "$s" ); else RUN=( "$RUNNER" "$s" ); fi ;;
  esac
  if "${RUN[@]}"; then :; else echo "FAILED: $s"; fail=1; fi
done

if [ "$fail" -eq 0 ]; then echo "── danjo clj: ALL suites green ──"; else echo "── danjo clj: FAILURES above ──"; exit 1; fi

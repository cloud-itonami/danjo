#!/usr/bin/env bash
# danjo — test entry. The former body `cd ../..` + `bb -e (require danjo.methods.test-…)` assumed
# the monorepo root on the classpath; that root does not exist here and no runtime resolves
# `danjo.methods.*` from methods/*.cljk anyway. run_tests_clj.sh is the runner (docs/adr/0001).
exec "$(dirname "$0")/run_tests_clj.sh" "$@"

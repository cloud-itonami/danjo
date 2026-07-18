#!/usr/bin/env bash
# danjo — bb/clj test suite (ADR-2606160842 py→clj port wave; Python pruned).
set -euo pipefail
repo_dir="$(cd "$(dirname "$0")" && pwd)"
classpath_dir="$(mktemp -d "${TMPDIR:-/tmp}/danjo-classpath.XXXXXX")"
trap 'rm -rf "$classpath_dir"' EXIT
ln -s "$repo_dir" "$classpath_dir/danjo"
cd "$repo_dir"
exec bb -cp "$classpath_dir" -e '(require (quote clojure.test) (quote danjo.methods.test-analyze) (quote danjo.methods.test-charter-gates) (quote danjo.methods.test-autorun) )(let [r (clojure.test/run-tests (quote danjo.methods.test-analyze) (quote danjo.methods.test-charter-gates) (quote danjo.methods.test-autorun) )](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'

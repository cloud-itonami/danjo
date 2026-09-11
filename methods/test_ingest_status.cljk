(ns danjo.methods.test-ingest-status
  "Tests for the R1 procurement/budget ingest STATUS stubs (ingest_status.cljc).
  clojure.test + require. These are pure observability fns (no gate, no I/O) — ADR-2607180900."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [danjo.methods.ingest-status :as ingest]
            [clojure.string :as str]))

(deftest procurement-status-shape
  (testing "procurement cell: fetcher landed, operator pull+pin pending; persists nothing (G8)"
    (let [s (ingest/procurement-status)]
      (is (= (:appended s) false))
      (is (= (:reason s) :fetcher-landed-awaiting-operator-pull))
      (is (:fetcher s) "fetcher path present (jp_chotatsu.py)")
      (is (:beat s) "procurement_beat path present")
      (is (zero? (:datoms s)))
      (is (= (get-in s [:gates :G8]) :no-fabrication)))))

(deftest budget-status-shape
  (testing "budget cell declares its W3 dependency + the representative seed honestly (G8)"
    (let [s (ingest/budget-status)]
      (is (= (:appended s) false))
      (is (= (:reason s) :awaiting-w3-fetcher))
      (is (= (:w3 s) "jp_yosan"))
      (is (zero? (:datoms s)))
      (is (str/includes? (:seed s) "representative") "seed is honestly labeled representative"))))

(deftest status-is-non-adjudicating
  (testing "status carries no verdict language (G4 posture extends to status reporting)"
    (doseq [s [(ingest/procurement-status) (ingest/budget-status)]]
      (is (false? (:server-held-key s)))
      (is (= (get-in s [:gates :G3]) :passive-only)))))

;; Run when invoked as the bb entry file; exit non-zero on failure so run_tests_clj.sh detects it.
#?(:clj
   (do
     (defn -main [& _]
       (let [{:keys [fail error]} (run-tests 'danjo.methods.test-ingest-status)]
         (System/exit (if (pos? (+ fail error)) 1 0))))
     (when (= *file* (System/getProperty "babashka.file")) (-main))))

(ns danjo.methods.test-diet-beat
  "Tests for the R1 DIET ingest beat (diet_beat.cljc). clojure.test + require (needs
  20-actors/ on the classpath for danjo.methods.*, like test_autorun). ADR-2607180900.
  The pure projection (project-datoms) is unit-tested here; the beat I/O path + the
  DANJO_R1_COUNCIL_RATIFY_TX_HASH gate are exercised end-to-end by methods/mesh.clj
  observe (run: bb -cp 20-actors -e '... (load-file mesh.clj) (-main)')."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [danjo.methods.diet-beat :as diet]
            [danjo.methods.kotoba :as kotoba]
            [clojure.string :as str]))

(def fixture
  {:manifest-cid "gov.dataset.manifest:jp_kokkai_kaigiroku#test"
   :records
   [{:recordId "TEST-1" :sessionDateUtc "2025-01-21T00:00:00Z"
     :payloadCid "https://kokkai.ndl.go.jp/talk/TEST-1"
     :house "衆議院" :nativeKind "本会議" :session 217 :issue "第1号"
     :speakerName "テスト太郎" :speakerRole "議長" :bodyExcerpt "…"}
    {:recordId "TEST-2" :sessionDateUtc "2025-01-22T00:00:00Z"
     :payloadCid "https://kokkai.ndl.go.jp/talk/TEST-2"
     :house "参議院" :nativeKind "予算委員会" :session 217 :issue "第2号"
     :speakerName "テスト花子" :speakerRole "大臣" :bodyExcerpt "…"}]})

(deftest project-datoms-shape
  (testing "projects gov-official + diet-statement + cross-reference-link per record"
    (let [datoms (diet/project-datoms fixture)
          attrs  (map #(nth % 2) datoms)]
      (is (pos? (count datoms)) "non-empty projection")
      (is (some #(= % ":gov.official/name") attrs))
      (is (some #(= % ":diet.statement/record-id") attrs))
      (is (some #(= % ":xref/source") attrs)))))

(deftest g4-non-adjudicating-structural
  (testing "a non-adjudicating flag is asserted; no verdict token in any attr (G4)"
    (let [datoms (diet/project-datoms fixture)
          stmt-flags (for [d datoms
                           :when (and (str/starts-with? (str (nth d 1)) "diet-statement:")
                                      (= ":danjo.obs/non-adjudicating" (nth d 2)))]
                       (nth d 3))]
      (is (seq stmt-flags) "diet-statement entities carry :danjo.obs/non-adjudicating")
      (is (every? true? stmt-flags))
      (doseq [a (map #(str/lower-case (str %)) (map #(nth % 2) datoms))]
        (is (not (some #(str/includes? a %) kotoba/forbidden-verdict-tokens))
            (str "no verdict token in attr " a))))))

(deftest g5-two-source-cids
  (testing "every diet-statement cites ≥2 source CIDs (G5)"
    (let [datoms (diet/project-datoms fixture)
          cid-datoms (for [d datoms
                           :when (= ":diet.statement/source-record-cids" (nth d 2))]
                       (nth d 3))]
      (is (seq cid-datoms))
      (doseq [cids cid-datoms]
        (is (>= (count cids) 2) (str "≥2 source CIDs, got " (pr-str cids)))))))

(deftest project-datoms-empty-safe
  (testing "project-datoms is safe on empty / malformed records (no crash, no datoms)"
    (is (empty? (diet/project-datoms {:records []})))
    (is (empty? (diet/project-datoms {:records [{:recordId "X"}]})) "missing speaker → skipped")))

;; Run when invoked as the bb entry file; exit non-zero on failure so run_tests_clj.sh detects it.
#?(:clj
   (do
     (defn -main [& _]
       (let [{:keys [fail error]} (run-tests 'danjo.methods.test-diet-beat)]
         (System/exit (if (pos? (+ fail error)) 1 0))))
     (when (= *file* (System/getProperty "babashka.file")) (-main))))

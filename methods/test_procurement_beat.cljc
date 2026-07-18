(ns danjo.methods.test-procurement-beat
  "Tests for the R1 PROCUREMENT ingest beat (procurement_beat.cljc). clojure.test + require
  (needs 20-actors/ on the classpath for danjo.methods.*, like test_autorun/test_diet_beat).
  ADR-2605263900 W3 (jp_chotatsu fetcher output shape)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [danjo.methods.procurement-beat :as proc]
            [danjo.methods.kotoba :as kotoba]
            [clojure.string :as str]))

(def manifest-cid "gov.dataset.manifest:jp_chotatsu#test")

(def records
  [{:noticeId "R-001" :recordKind "award" :title "保守業務"
    :contractingAuthority "総務省" :awardeeName "〇〇株式会社" :awardeeLocalId "A1"
    :awardAmountLocal 98000000 :currencyIso4217 "JPY" :awardDateUtc "2025-04-15T00:00:00Z"
    :payloadCid "https://www.p-portal.go.jp/result/R-001"}
   {:noticeId "R-002" :recordKind "award" :title "調査業務"
    :contractingAuthority "経済産業省" :awardeeName "△△合同会社" :awardeeLocalId "B2"
    :awardAmountLocal 5000000 :currencyIso4217 "JPY" :awardDateUtc "2025-05-01T00:00:00Z"
    :payloadCid "https://www.p-portal.go.jp/result/R-002"}
   {:noticeId "R-003"}])  ; missing authority + awardee → skipped

(def datoms (proc/project-datoms records manifest-cid))

(defn- attrs
  "All attribute names (3rd element of each EAVT datom)."
  []
  (map #(nth % 2) datoms))

(defn- vals-for
  "Values of all datoms whose attribute equals `a`."
  [a]
  (->> datoms (filter #(= a (nth % 2))) (map #(nth % 3))))

(defn- verdict-attrs
  "Attribute names that contain a forbidden verdict token (G4 violation if non-empty)."
  []
  (for [a (attrs)
        :let [lc (str/lower-case (str a))]
        :when (some #(str/includes? lc %) kotoba/forbidden-verdict-tokens)]
    a))

(deftest project-datoms-shape
  (testing "projects authority + corp-entity + procurement-award + cross-reference-link"
    (is (pos? (count datoms)) "non-empty projection")
    (is (some #(= % ":contracting.authority/name") (attrs)))
    (is (some #(= % ":corp.entity/name") (attrs)))
    (is (some #(= % ":procurement.award/notice-id") (attrs)))
    (is (some #(= % ":xref/source") (attrs)))))

(deftest skips-identity-less-records
  (testing "records missing noticeId/authority/awardee are skipped"
    (is (= #{"R-001" "R-002"} (set (vals-for ":procurement.award/notice-id")))
        "R-003 (identity-less) skipped")))

(deftest g4-non-adjudicating-structural
  (testing "non-adjudicating flag on awards; no verdict token in any attr (G4)"
    (let [flags (vals-for ":procurement.award/non-adjudicating")]
      (is (seq flags) "procurement-award entities carry the non-adjudicating flag")
      (is (every? true? flags)))
    (is (empty? (verdict-attrs))
        (str "no verdict token may appear in any attr; found: " (verdict-attrs)))))

(deftest g5-two-source-cids
  (testing "every procurement-award cites >=2 source CIDs (G5)"
    (let [cid-lists (vals-for ":procurement.award/source-record-cids")]
      (is (= 2 (count cid-lists)) "one source-record-cids per award (2 awards)")
      (is (every? #(>= (count %) 2) cid-lists) "each award cites >=2 CIDs"))))

(deftest award-amounts-and-edges
  (testing "award amount + authority/awardee edges are projected factually"
    (is (= #{98000000 5000000} (set (vals-for ":procurement.award/amount-jpy"))))
    (is (some #(= % ":award-authority") (vals-for ":xref/kind")))
    (is (some #(= % ":award-awardee") (vals-for ":xref/kind")))))

;; Run when invoked as the bb entry file; exit non-zero on failure so run_tests_clj.sh detects it.
#?(:clj
   (do
     (defn -main [& _]
       (let [{:keys [fail error]} (run-tests 'danjo.methods.test-procurement-beat)]
         (System/exit (if (pos? (+ fail error)) 1 0))))
     (when (= *file* (System/getProperty "babashka.file")) (-main))))

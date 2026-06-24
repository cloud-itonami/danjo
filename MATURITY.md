# danjo (弾正) — Maturity Ledger

`/loop` (30分毎) の進捗台帳。各イテレーションで **1項目** だけ成熟度を上げ、ここに
記録する。honest framing: できていないことは「未」と明記する。

- Actor: `did:web:danjo.etzhayyim.com` · ADR-2605301600 (+ ADR-2605302245 global
  fiscal-flow extension) · **R0 scaffold**
- 不変条件(全イテレーション厳守): R0 では cell 非実行 · live ingestion/dispatch なし ·
  **NON-adjudicating (G4)** — 犯罪/不正/法令違反 を断定しない、verdict なし ·
  **passive-only ingestion (G3)** — 既公開 IPFS-pinned `gov.dataset.*` のみ、portal 再 scrape 禁止 ·
  source-provenance 必須 (G5) · open method (G6) · Transparent Force discipline (G11/§1.12) ·
  Murakumo-only inference (ADR-2605215000) · G8 非捏造 · G14 verified-source-only ·
  コミットはユーザー明示時のみ

## 成熟度チェックリスト

| # | 項目 | 状態 | 完了イテレーション |
|---|---|---|---|
| 1 | ADR-2605301600 (master) + ADR-2605302245 (fiscal-flow) | ✅ | init |
| 2 | manifest.jsonld + README + CLAUDE.md | ✅ | init |
| 3 | Lexicon skeletons (`com.etzhayyim.danjo.*`) | ✅ | init |
| 4 | worldwide fiscal-source registry seed (`registry/sources.seed.json`, 全件 unverified-seed) | ✅ | seed |
| 5 | fail-closed registry invariants test + G14 VERIFICATION.md | ✅ | この iter |

## イテレーション記録

### worldwide fiscal-source catalog hardening (2026-06-02)
**WORLDWIDE fiscal-source 台帳の fail-closed 固定 + G14 検証ワークフロー文書化。**
既存の `registry/sources.seed.json`(166 件 / 34 distinct jurisdiction + 国際機関
[IMF / World Bank / OECD / UN / IATI / OGP] / sourceKind 6種: audit-institution /
budget-portal / intl-aggregator / legislature-record / open-spending /
procurement-system)に対し、sibling toritsugi 方式で 2 層の hardening を追加:
(1) `70-tools/scripts/audit/test_danjo_registry_seed.py`(**8 test, 全 green**)—
①JSON parse + `sources` 非空 ②`sourceId` 一意(重複で fail)③全件
`verificationStatus="unverified-seed"`(G14)④全件 https provenance + ISO-8601
`lastVerified` ⑤≥12 distinct jurisdiction(worldwide guard; JP-only 退行ガード)
⑥全件 `sourceKind` が allowed catalog set 内 ⑦全件 notes 非空 + 台帳が
NON-adjudicating / observational 境界を参照 ⑧top-level 整数 `freshnessWindowDays`。
test-only・network-free・cell 実行なし(R0 ceiling 不変)。
(2) `20-actors/danjo/registry/VERIFICATION.md` — G14 三層(unverified-seed →
maintainer-verified → council-verified)の人手チェックリスト。per-field 10項目 +
**per-jurisdiction official-domain provenance check**(.gov / .go.jp / .gouv.fr /
.gov.uk / europa.eu / .gob.* / .go.kr / 国際機関ドメイン、fail-closed)+
NON-adjudicating / observational 境界 re-check を明記。
**honest (G8)**: **検証済みソースは 0 件** — 全件 unverified-seed のまま。台帳は
既公開公式データの ingestion scaffold であり authoritative inventory ではない。
実際の verification 実行は R1(Council ratify + fiscal-source-verification
maintainer DID 登録後)。danjo finds + cross-references; kanae renders; neither
adjudicates。

### 2026-06-17 (loop) — manifest+lexicon charter-gate test (構造ゲート pin)
新設 `methods/test_charter_gates.cljc`(**7 tests green**)で manifest G1–G13 + 4 lexicon の非裁定ゲートを固定: G4 discrepancyObservation/oversightReport const nonAdjudicatingNotice=true + 全lexicon に verdict/accusation/guilt/ruling フィールド不在(censor's eye, never sword)/ G5 observation が sourceRecordCids + methodNoteCid、crossReferenceLink が basisRecordCids(≥2 source)/ G6 methodNote が definition+inputs+version / G11 publiclyNamedBasis={procurement-awardee, diet-member-on-record, budget-recipient, contracting-authority} / governance oversightReport が councilAttestations + councilReviewCid + oneSbtOneVoteChainCid。`run_tests.sh` 新設。working-tree edits only。

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `danjo.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).

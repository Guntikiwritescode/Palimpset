# PALIMPSEST — Project Context & Decision Record

*A handoff document capturing the full reasoning behind the project, for future continuation.*

This record summarizes a design conversation that produced a differentiating portfolio project targeting Palantir's Forward Deployed Engineer roles. The two **authoritative build specifications** are the separate files `PALIMPSEST-Part-1-Core-Platform.md` and `PALIMPSEST-Part-2-Expansion.md`; this document explains the *why* behind them and preserves the context, decisions, verified facts, and open threads. If pasted into a fresh conversation, it should be enough to continue seamlessly.

---

## 1. The goal

The user is building a post-graduate-level portfolio project to differentiate themselves for Palantir's Forward Deployed Engineer roles. Stated context: strong software skills across the languages these roles list, a desire for the most complex/difficult project that reflects real experience, ample time and tooling, and — importantly — **a second degree in history that they want to leverage as a genuine differentiator, not decoration.**

The user works critically and honestly: they push back, refine, and prefer depth over breadth. Their stated preferences: be honest and straightforward, back claims with reliable sources, never hallucinate, and never take credit for GitHub commits.

---

## 2. The target roles and hard constraints

Four Palantir postings were reviewed; all are one job family (the Forward Deployed Engineer):

| Posting | Track | Key gate |
|---|---|---|
| Forward Deployed Software Engineer, New Grad — Commercial | FDSE | Graduating 2026 or earlier; **invites an optional demo for priority review** |
| Forward Deployed Software Engineer, New Grad — US Government | FDSE | Active/eligible US security clearance |
| Forward Deployed Infrastructure Engineer, Internship — US Government | FDIE | Graduating 2028; clearance; adds Go, observability, distributed-systems ops |
| Forward Deployed Software Engineer, Internship | FDSE | Internship |

**Shared DNA across all four:** take an open-ended real-world problem, integrate massive/messy/heterogeneous data, build a semantic/operational layer, ship a custom application a non-technical operator actually uses, and own it end-to-end like a "startup CTO."

**Two honest constraints carried throughout:**
- **A project is necessary but not sufficient.** These roles weight problem-framing, communication with non-technical stakeholders, and comfort with ambiguity as heavily as engineering.
- **Hard gates override any project** (graduation year; clearance eligibility). Confirm the gate on whichever role is targeted.

---

## 3. Key facts about Palantir that shaped the design (verified this session)

Palantir's Foundry **Ontology** sits atop integrated datasets/models and connects them to real-world **objects, properties, and links** (semantic), plus **actions, functions, and dynamic security** (kinetic). Palantir explicitly frames it as **not a thin "semantic layer,"** because its value is the four-fold integration of **data, logic, action, and security**, including governed write-back and what-if simulation. The platform flow is source systems → connectors → datasets → transforms (with lineage) → Ontology → applications (Workshop/Slate/OSDK), with deployment via Apollo.

**Design consequence:** the most differentiating project mirrors this exact shape — a small, vertically-integrated "mini-Foundry" with a semantic/operational core and an operator-facing tool on top. And the hardest, most authentic version of that problem is one where contradictory sources must be reconciled under governed reliability — which is precisely what the chosen domain provides.

---

## 4. How the idea evolved (the path taken)

1. **Start:** a project mirroring Palantir's mini-Foundry architecture around a real open-ended problem.
2. **First draft (abandoned):** a wildfire-risk → power-grid de-energization platform — one of Palantir's own headline example problems. Strong, but did not use the history degree.
3. **Pivot:** leverage the history degree, reframing historical scholarship as an *evidence-integration and entity-resolution discipline under uncertainty* — the same thing Palantir's Ontology does, on the hardest possible data.
4. **Three candidate history domains** were proposed and grounded: (a) a provenance/looted-cultural-property integrity platform; (b) event & network reconstruction over declassified government archives; (c) large-scale historical social-network reconstruction with first-class uncertainty.
5. **Chosen: Idea (c), expanded far beyond a single people-network** into a multi-source temporal knowledge graph fusing people, places, polities, events, environment, and texts.
6. **Named PALIMPSEST**, with the defining commitment that uncertainty and provenance are first-class.
7. **Depth-vs-breadth debate:** the user asked whether covering many periods would be better; the conclusion was depth-first, with breadth spent only where it is cheap and compounds.
8. **Two-part structure** agreed: a core (build now) and an expansion (later, gated).
9. **Final rewrite:** two complete, separate build specifications (Part 1, Part 2) with accuracy verification.

---

## 5. The final design: PALIMPSEST (summary)

**Thesis (one commitment carried through every layer):** build a temporal knowledge graph — a "digital twin of the past" — in which every relationship is a time-scoped, provenance-backed, confidence-weighted **claim**, where the archive's survivorship bias is modeled explicitly and competing interpretations are represented rather than collapsed into a single false truth. Deliberately **not** "a complete model of history."

**Core abstraction — the Claim:** `(subject, predicate, object, valid-time, support = {sources}, asserted-by, method, confidence-distribution, system-time, status)`. Two sources that disagree produce two retained claims, never a silent merge. This mirrors the established Digital-Humanities **"factoid" model**, so the core matches recognized scholarly practice.

**Architecture (six layers):** data/pipeline (harvesters + lineage) → uncertainty-aware ontology engine (claim store, bitemporal history, competing-claim model, governed write-back) → analytics (entity resolution, relation extraction, uncertainty machinery) → typed ontology SDK → the explorer app → infrastructure (Kubernetes, IaC, observability, incremental recompute).

**The differentiator — the uncertainty model:** calibrated claim confidence derived from source reliability; aleatoric vs. epistemic uncertainty; Monte Carlo over uncertain inputs producing *distributions* over network metrics rather than point estimates; survivorship-bias-aware network inference (ERGM / latent-space models); an optional, humble causal/what-if layer that tests hypotheses without claiming to prove causation.

**Core slice:** early-modern Europe (c. 1500–1700), chosen for richest overlapping data; the engine is built domain-general.

**Two-part structure:**
- **Part 1 (build first):** the core platform on the early-modern slice.
- **Part 2 (later, gated):** a repeatable source-onboarding capability, proven by integrating a maximally-different period (the Roman Mediterranean), plus a cheap comparative-overlay expansion. **Part 2 must not begin until Part 1's core is built and working** (a seven-point gate is defined in both specs).

**The signature demo beat (same for both parts):** browse one figure's evidence-backed network and timeline, then drag a confidence slider and watch weakly-attested links disappear — the single most legible expression of the whole thesis, and it doubles as the optional application demo.

---

## 6. Key decisions and the reasoning behind them (the most important section)

- **Mirror Palantir's ontology architecture.** Because the FDE screen is about integrating messy data into a governed, operational semantic layer; building a miniature of that is the most direct signal.
- **Treat the history degree as a methodological asset.** Source criticism (reliability weighting), disambiguation, and reasoning about archival silences are the historian's trained method and precisely the hard part of ontology-building over primary sources. This makes the degree load-bearing rather than thematic — a story a pure-CS candidate cannot easily tell.
- **Make uncertainty + provenance first-class (the thesis).** This simultaneously (a) is the most historically honest choice, (b) directly answers cliodynamics' central criticism (that quantitative-history conclusions are often artifacts of data gaps and coding choices), and (c) is identical to Palantir's "reconcile contradictory sources with governed reliability; not a semantic layer" ethos.
- **Reject "completeness" as a goal.** A "complete model of society/history" reads as naïve over-scoping to a Palantir reviewer, who prizes judgment about what to cut. The ambition lives in the *breadth of source types and inference methods*; the rigor lives in refusing to assert more certainty than the evidence supports.
- **Depth-first, breadth-second.** Building breadth before the core produces weak resolution everywhere and no evidence of judgment — the exact junior failure mode. Depth first proves the hard engineering; reach second proves generalization.
- **For expansion: capability, not count.** The deliverable of Part 2 is a *repeatable onboarding capability* (adapter framework + acquisition methodology), demonstrated once well — because that is literally the Forward Deployed Engineer job, and it beats a tally of regions.
- **The factoid-family finding (an honest refinement of an earlier claim).** Earlier the cost of each new period was framed as roughly linear and non-compounding. That is true for *bespoke* corpora, but a family of period prosopographies (DPRR, PASE, PBW, PoMS, the Making of Charlemagne's Europe) shares the factoid/claim structure and interoperability standards (SNAP:DRGN), so adding one of those collapses largely to a schema mapping — meaningfully cheaper. Periods also link at the geography layer via shared place gazetteers even when they share no people.
- **Two-part split with a hard prerequisite gate.** So the plan is finishable and legible: the core stands alone as a strong artifact; expansion is explicitly future work that only adds value once the foundation exists.

**Honest corrections made during the work (kept for accuracy):**
- **GDELT/ACLED are modern-only** (events since 1979) and do **not** apply to an early-modern core; they are named only as the archetype of the source-reliability problem and as a modern-era extension.
- **Pleiades is ancient-world**, so it belongs to Part 2's Roman slice; Part 1's geography uses the all-period World Historical Gazetteer.
- **Dataset figures stated conservatively** where sources disagreed (e.g., "roughly 15,000 figures" for Six Degrees of Francis Bacon; "over 1,400 societies" for D-PLACE).
- **Numismatic linked-data** was left flagged "evaluate before use" rather than asserted; **VIAF** appears as a recommended authority option, not a verified specific claim.

---

## 7. Verified data sources (high-value reference; re-confirm license/API/export before building)

Facts below were verified against live sources during this session. Data resources change; treat this as a starting point, not a guarantee.

### Part 1 — early-modern Europe core
| Source | What it is | Status |
|---|---|---|
| Six Degrees of Francis Bacon | ~15,000 early-modern figures, ~170,000 relationships, NLP-inferred from the ODNB (Poisson graphical lasso → a confidence matrix); downloadable | Verified |
| Mapping the Republic of Letters; Cultures of Knowledge / EMLO | Correspondence networks; dated, directed epistolary links | Verified |
| World Historical Gazetteer | 2M+ place records, 70+ datasets, Bronze Age→present; "no record is authoritative" | Verified |
| NOAA Paleoclimatology / ITRDB | Tree-ring series + reconstructed climate, 2,000+ sites, six continents; dendrochronology also dates timber | Verified |
| Seshat: Global History Databank | Polities across ~10,000 years (Neolithic→Industrial); provenance-tracked; **non-commercial** license | Verified |
| HathiTrust (computational access) | Large digital library for scholarly computational use | Lightly referenced |
| GDELT / ACLED | Modern structured events (1979+); GDELT ~55% field accuracy, ~20% redundancy, needs URL dedup; ACLED expert-curated but reporting-biased | Verified — **modern-only** |

### Part 2 — Roman-Mediterranean contrast slice
| Source | What it is | Status |
|---|---|---|
| DPRR (Digital Prosopography of the Roman Republic) | Roman Republic elite — offices, status, life dates, family; RDF/LOD, stable URIs, Wikidata cross-link | Verified |
| PIR (Prosopographia Imperii Romani) | Prosopography of the Roman Empire's notable persons | Verified (named) |
| Trismegistos | Papyrological/epigraphic text metadata (700,000+ records); people substructure (attestation→individual→variant→name) = an ER scaffold | Verified |
| EDH (Epigraphic Database Heidelberg) | Roman inscriptions; open data (CC BY-SA 4.0) in EpiDoc XML, inscriptions-with-prosopography in RDF, geo in GeoJSON; reuses Pleiades/GeoNames/Periodo/SNAP:DRGN URIs | Verified |
| EDCS (Epigrafik-Datenbank Clauss-Slaby) | ~500,000 Latin inscriptions; openly retains duplicates and sometimes dubious restorations | Verified |
| EAGLE (Europeana network of Greek & Latin Epigraphy) | LOD aggregation; uses Trismegistos IDs to collate duplicate inscriptions | Verified |
| Perseus / Scaife | Openly-licensed Greek/Latin corpus (~83.8M words); already does NER linking to authority lists such as Pleiades | Verified |
| Pleiades | 36,000+ ancient places as a graph; RDF/GeoJSON dumps; the shared geographic spine | Verified |
| ORBIS | Stanford geospatial network model of Roman travel time/cost; explicitly models its calibration uncertainty | Verified |
| Numismatic linked-data (e.g., coinage LOD) | Coinage as economic/temporal evidence | **Flagged — evaluate before use** |

### Cross-cutting / comparative & infrastructure
| Item | Role | Status |
|---|---|---|
| D-PLACE | 1,400+ societies; culture/language/environment, per-datapoint sources + coding uncertainty; the cheap comparative-overlay expansion | Verified |
| Factoid model + family (PASE, PBW, PoMS, Making of Charlemagne's Europe) | Established sourced-prosopography model = PALIMPSEST's claim model; family enables cheap factoid-family expansion | Verified |
| SNAP:DRGN; Periodo; Pelagios/LAWDI; Digital Classicist | Interoperability standards / discovery registries | Verified (named) |
| Wikidata; VIAF; Lexicon of Greek Personal Names | Authority hubs for cross-source entity resolution | Wikidata/LGPN referenced; VIAF a recommendation |
| Cliodynamics (Turchin; Seshat/D-PLACE as its data) | The contested intellectual context; PALIMPSEST is positioned as the rigor its critics demand, not an endorsement | Verified as contested |

---

## 8. Honest caveats and constraints (carry these forward)

- **Necessary but not sufficient**, and **hard gates** (clearance eligibility; graduation year) override any project.
- **Cliodynamics is genuinely contested** (e.g., specific Seshat results were challenged as artifacts of data gaps; the authors rebutted). Position PALIMPSEST as building the uncertainty/provenance infrastructure the critiques call for — not as taking a side on any theory of history.
- **History is not a controlled experiment.** The optional causal/what-if layer tests hypotheses; it does not prove causation.
- **No fake cross-period entity resolution.** People do not overlap across a millennium; connect periods through shared places and authority hubs, and resolve people within a period.
- **Confidence must reflect fragmentary, biased attestation** (e.g., EDCS's openly-noted duplicates and dubious restorations must surface as low confidence; ancient evidence survives unevenly).
- **Licensing varies per source** (several open, some non-commercial such as Seshat, some CC BY-SA such as EDH). Confirm each before building.
- **Data-source facts were verified as of this session and can change.**

---

## 9. Anti-patterns (consolidated)

Chasing completeness; a knowledge graph with no calibrated confidence; false precision / silent merging of contradictory sources; leakage in inference (naïve random validation on spatiotemporal/network data — use block + forward-chaining); ignoring survivorship bias; claiming proven causation; treating infrastructure as an afterthought (fatal for infra roles); taking a side in the cliodynamics dispute; for Part 2 — measuring by region count instead of demonstrated capability, underestimating bespoke-corpus cost, and blurring the coarse comparative overlay with the fine-grained resolved graph.

---

## 10. Deliverables produced in this chat

| File | Status |
|---|---|
| `PALIMPSEST-Part-1-Core-Platform.md` | **Current / authoritative** — the core build spec |
| `PALIMPSEST-Part-2-Expansion.md` | **Current / authoritative** — the gated expansion spec |
| `palimpsest-historical-ontology-blueprint.md` | Superseded by the Part 1 rewrite |
| `palimpsest-part2-expansion-playbook.md` | Superseded by the Part 2 rewrite |
| `palantir-fde-capstone-blueprint.md` | Abandoned (the earlier wildfire/grid direction, before the history pivot) |

For any future work, use the two **current** files as the source of truth; the other three are historical.

---

## 11. Open threads / recommended next step

- **Highest-leverage next step (offered, not yet done):** write the concrete claim + entity-resolution schema and build a small working prototype on the Six Degrees of Francis Bacon data. This is the hardest single component and the fastest way to prove the whole design is real.
- **Other possible deep-dives:** the detailed adapter interface for Part 2; the infrastructure/observability layer for the FDIE angle; the calibration + Monte Carlo uncertainty machinery in implementation detail.

---

## 12. Working-style notes (for continuity)

The user prefers rigor and honesty: claims backed by verifiable sources, no overstatement, explicit uncertainty, and depth over breadth. They engage critically and refine the plan collaboratively. They want a maximally-differentiating project but respond well to honest pushback on scope and feasibility. Keep the two-part structure and the "capability, not count" and "uncertainty as first-class" commitments central to any continuation.

---

### One-line framing of the whole project
> Part 1 builds an uncertainty-aware historical knowledge graph deeply on a single early-modern slice — every relationship a time-scoped, provenance-backed, confidence-weighted claim; Part 2 turns integration into a repeatable capability and proves it by onboarding a maximally-different period (the Roman world) into the unchanged engine — the same "integrate a new domain's messy data fast" loop that defines forward-deployed engineering.

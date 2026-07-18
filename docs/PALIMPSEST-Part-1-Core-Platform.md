# PALIMPSEST — Part 1: The Core Platform

**A complete build specification for the foundation.** This document is fully self-contained. It describes only the core platform, built on a single historical slice. Expansion into additional periods is a *separate, later effort* (Part 2) that must not begin until the core defined here is built and working; Part 1 has no dependency on Part 2.

*A palimpsest is a manuscript overwritten many times, with traces of earlier text still showing through — an incomplete, layered, contested record. That is what this system models, and how it models it.*

---

## 0. Contents

1. Problem and thesis
2. Who it's for: role mapping and honest gating
3. Why it maps to Palantir specifically
4. Honest intellectual context
5. The core abstraction: the Claim
6. The ontology: objects, links, actions
7. Data layers for the core slice
8. System architecture (six layers)
9. Entity resolution
10. The uncertainty model
11. The explorer application
12. Infrastructure
13. Testing and validation
14. Language map
15. Data sources (grounded)
16. Scope discipline and phased build
17. Anti-patterns
18. Packaging and demo
19. Definition of "core complete" (the gate for Part 2)

---

## 1. Problem and thesis

Historical knowledge is fragmentary, contradictory, multilingual, and radically sampled by what happened to survive. A person or event is attested by scattered evidence of uneven reliability; two sources routinely disagree; most of what once existed is simply gone. Existing digital-history resources tend to flatten this — presenting a single "answer" and discarding the doubt, or building a graph with no notion of how much to trust each edge.

**The thesis is one design commitment carried through every layer:**

> Build a temporal knowledge graph — a "digital twin of the past" — in which every relationship is a time-scoped, provenance-backed, confidence-weighted **claim**, where the archive's survivorship bias is modeled explicitly and competing interpretations are represented rather than collapsed into a single false truth.

This is deliberately **not** "a complete model of history." Completeness is a direction, not a deliverable. The ambition lives in the breadth of source types and inference methods the engine fuses; the rigor lives in refusing to assert more certainty than the evidence supports. Making uncertainty and provenance first-class is the entire intellectual and engineering core.

**The core is built on one slice: early-modern Europe (c. 1500–1700).** This is chosen because it has the richest overlapping data for the layers below — a large, already-confidence-scored people/network dataset, extensive correspondence data, good tree-ring climate coverage, and abundant digitized text — while the architecture is built to be domain-general so it can later ingest very different periods (Part 2).

---

## 2. Who it's for: role mapping and honest gating

This project targets Palantir's Forward Deployed Engineer family: Forward Deployed Software Engineer (FDSE) and Forward Deployed Infrastructure Engineer (FDIE), across commercial and government tracks and internships. They share one DNA: integrate messy real-world data, build a semantic/operational layer, ship an application a non-expert uses, and own it end-to-end.

Three honesty points to keep visible:

- **A project is necessary but not sufficient.** These roles weight problem-framing, communication with non-technical stakeholders, and comfort with ambiguity as heavily as engineering. Build the project *and* be able to tell the story of what was ambiguous, what you cut, and how it scales.
- **Hard gates exist and no project overrides them.** Government roles require US security-clearance eligibility; specific postings gate on graduation year (e.g., certain roles require graduating in or before a given year, and one internship requires a specific future graduation year). Confirm you clear the gate on whichever you apply to.
- **At least one posting invites an optional demo for priority review.** This project *is* that demo. Design the whole thing to be demonstrable in five minutes to a non-technical person (see §11 and §18).

---

## 3. Why it maps to Palantir specifically

Palantir's own materials describe their Ontology as an operational layer that sits atop integrated datasets and connects them to real-world objects, properties, and links — and they explicitly insist it is *not* a thin "semantic layer," because its value is the four-fold integration of **data, logic, action, and security**, including governed write-back and what-if simulation. Reconciling contradictory sources under a governed reliability and security model is the whole point.

Historical primary sources are the hardest possible instance of exactly that problem. So this project lets you demonstrate, on the most unforgiving data that exists:

- **Ontology-building over messy sources** — cross-source entity resolution over sparse, noisy, multilingual attributes is a harder version of the enterprise-integration problem FDSEs face daily.
- **Uncertainty as a first-class property of every claim** — real intelligence and enterprise data is uncertain; modeling everything as certain is a junior failure. You will have built calibrated confidence, propagated end-to-end.
- **End-to-end ownership** — ingestion → uncertainty-aware graph engine → inference → an accessible operator-grade explorer → deployment and observability.
- **A load-bearing domain skill** — source criticism (weighting reliability), disambiguation, and reasoning about the silences in the record are the historian's trained method and precisely the hard part here. The history background is an asset, not a theme.

---

## 4. Honest intellectual context (include this in your write-up — it is a strength)

This project sits in **cliodynamics**, the quantitative/mathematical modeling of history associated with Peter Turchin, whose Seshat databank and the D-PLACE database are two of its major data resources. Be honest that **cliodynamics is genuinely contested.** Historians have criticized "chartist" cycle-finding, and specific Seshat results (notably work on moralizing gods and social complexity) were challenged on the grounds that the observed patterns were artifacts of omissions and gaps in the underlying data — a critique the authors rebutted at length.

That debate is not a reason to avoid the space; it is the reason the project exists. **Position PALIMPSEST as the infrastructure the critiques call for, not as an endorsement of any theory of history.** Its contribution is methodological: a system where you cannot state a relationship without also stating what evidence supports it, how reliable that evidence is, and how confident the resulting claim is — where data gaps are modeled explicitly rather than silently generating spurious patterns. That stance is principled, useful, and identical to Palantir's "reconcile contradictory sources with governed reliability" philosophy.

Two further commitments, both of which read as maturity:

- **History is not a controlled experiment.** Any causal/what-if capability *encodes and tests hypotheses*; it does not prove causation. Frame it humbly or it reads as crankish.
- **Precision you don't have is a lie.** Everywhere, surface confidence and competing claims; never fabricate false exactness.

---

## 5. The core abstraction: the Claim

Everything in PALIMPSEST reduces to one first-class object. A **Claim** is:

```
Claim {
  id
  subject        : EntityRef            // e.g. a Person
  predicate      : RelationType         // e.g. "corresponded-with"
  object         : EntityRef | Literal  // e.g. another Person, or a date/place
  valid_time     : TimeInterval         // when the claim holds in the world (may be fuzzy)
  support        : [SourceRef]          // the documents/datasets asserting it
  asserted_by    : AgentRef             // who entered/derived it (a scholar, or a pipeline)
  method         : ProvenanceMethod     // manual | imported(dataset) | extracted(model) | inferred(algorithm)
  confidence     : Distribution         // calibrated; not a bare scalar
  system_time    : TimeInterval         // when the platform believed this (bitemporal)
  status         : asserted | disputed | superseded
}
```

Two sources that disagree produce **two claims**, both retained, each with its own support and confidence — never silently merged. A scholar can later `Dispute` or `Supersede` a claim, and the history of that is preserved (see §6). This is the World-Historical-Gazetteer principle ("no record is ultimately authoritative") generalized into a governed engine, and it is the Palantir "not a semantic layer" principle in practice.

**Worked example.** A biographical source says Person A tutored Person B in the 1590s; a second source implies they never met. PALIMPSEST stores: `Claim1(A, tutored, B, valid≈1590s, support={Src1}, method=imported, confidence≈0.55)` and `Claim2(A, met, B, valid=∅, support={Src2}, method=extracted, confidence≈0.30, status: in tension with Claim1)`. The explorer shows both, with their evidence, and lets the user filter by confidence. Nothing is hidden; nothing is forced to a single truth.

This design is not idiosyncratic. It mirrors the established Digital-Humanities **"factoid" model** for structured prosopography (in which each factoid records that *a particular source states* something about a person), so your core matches recognized scholarly practice rather than reinventing it.

---

## 6. The ontology: objects, links, actions

**Object types (illustrative, for the core slice):** `Person`, `Place`, `Polity`/`Society`, `Event`, `Institution`, `Organization`, `Document`/`Source`, `EnvironmentalSeries`, and `Claim` itself. Each has typed properties, all of which are themselves expressed as claims (so a birth date is a claim with support and confidence, not a bare field).

**Link types (all time-scoped and confidence-weighted):** person↔person (`knew`, `corresponded-with`, `kin-of`, `taught`, `patron-of`); person↔place (`born-at`, `active-at`, `died-at`); person↔institution/org (`member-of`, `held-office`); entity↔document (`attested-by` — everything ultimately links to its sources); place↔polity (`located-in`, `part-of`); environment↔society (`co-occurs-with`, for e.g. a climate series over a region and period).

**Action types (governed, attributed write-back — the "kinetic" layer):** `AssertClaim`, `DisputeClaim`, `SupersedeClaim`, `MergeEntities`, `SplitEntity`, `AdjustConfidence`. Every action is validated, attributed to an agent, and written through a path that preserves full history. This is what makes the graph an *operational* system a scholar edits, not a static dataset.

**Bitemporality.** The store keeps both **valid-time** (when a claim holds in the world) and **system-time** (when the platform believed it), so you can ask "what did we believe about this in an earlier state of the database, and what do we believe now?" This is essential for a system whose beliefs change as sources are re-evaluated, and it is exactly the discipline the cliodynamics critiques demand.

---

## 7. Data layers for the core slice (early-modern Europe)

Each layer is a real, public resource. **Verify each source's current license, API, and bulk-export terms before building** — several are open but carry specific conditions.

- **People and social ties.** *Six Degrees of Francis Bacon (SDFB)* — roughly 15,000 early-modern figures and on the order of 170,000 relationships, inferred from the Oxford Dictionary of National Biography via natural-language processing (named-entity recognition plus a Poisson graphical-lasso model over co-occurrence in biographical text), which *already yields a confidence matrix*. This is the anchor layer and, crucially, it is already uncertainty-bearing — you extend its method, not just its data.
- **Correspondence.** Epistolary-network projects — Stanford's *Mapping the Republic of Letters* and Oxford's *Cultures of Knowledge / Early Modern Letters Online (EMLO)* — where a letter is both evidence of and constitutive of a tie. These add directed, dated links grounded in specific documents.
- **Places.** The *World Historical Gazetteer (WHG)* — over two million place records across 70+ datasets spanning the Bronze Age to the present, built on the explicit principle that no record is authoritative, concatenating every linked claim. Use it as the geographic spine; reconcile to shared identifiers (e.g., GeoNames, Wikidata) for linkage.
- **Environment and climate.** *NOAA Paleoclimatology / the International Tree-Ring Data Bank (ITRDB)* — ring-width and density series and reconstructed climate parameters from over 2,000 sites on six continents, with good European coverage. Dendrochronology also doubles as a **dating** method (cross-dating historical timber), which feeds event-date uncertainty.
- **Polities (light comparative overlay).** *Seshat: Global History Databank* — social and political organization of polities across roughly 10,000 years (Neolithic to Industrial), which includes the early-modern period; provenance-tracked and openly released under a non-commercial license. In the core, use a light overlay; heavy comparative expansion is Part 2's cheap-breadth win.
- **Texts (the extraction substrate).** Digitized early-modern corpora — the EMLO letters and large digital libraries offering computational access (e.g., HathiTrust for scholarly computational use) — are where you *derive* new entities and relations rather than only ingesting pre-structured ones.

**Honest note on events.** For early-modern Europe, structured event data is the *thinnest* layer, and events come mainly from **text extraction** (chronicles, pamphlets) plus specialized datasets. The large modern structured event datasets — GDELT (global events since 1979, whose key fields are only about 55% accurate with roughly 20% redundancy and require URL-level deduplication) and ACLED (expert-curated but subject to reporting bias where media access is thin) — **do not apply to the early-modern period.** They are named here only as the archetype of the source-reliability problem the whole system is designed around, and as a data source relevant *only if* the scope is later extended into the modern era. Do not pretend they cover the 1600s.

---

## 8. System architecture (six layers)

```
   EXPLORER (FDSE / frontend, TypeScript)
   Map + timeline + graph. Browse any entity; see the EVIDENCE + CONFIDENCE behind
   every link; filter "the network as of 1590 at confidence ≥ 0.7"; assert/dispute claims.
                         │  typed Ontology SDK
   ONTOLOGY ENGINE (backend, JVM — Java/Scala)
   Claim store · bitemporal history · competing-claim model · governed, attributed
   write-back · change-data-capture indexer keeping derived views fresh
                         │
   ┌──────────────────┬──┴───────────────────┬──────────────────────────┐
 ENTITY RESOLUTION   INFERENCE               UNCERTAINTY               (Python / C++)
 probabilistic       NER + relation          calibrated confidence;
 record linkage →    extraction from text;   Monte Carlo over uncertain
 canonical entities  survivorship-bias-aware  dates/links → DISTRIBUTIONS
 (resolve to         network inference        over metrics; (optional,
 authority hubs)     (ERGM / latent space)    humble) causal / what-if
   └──────────────────┴──────────────────────┴──────────────────────────┘
                         │
   DATA / PIPELINE (Python + JVM)
   Harvesters: linked-data / SPARQL · structured dumps · text extraction.
   Incremental updates. FULL LINEAGE: which source record produced which claim.
                         │
   INFRASTRUCTURE (FDIE / Go)
   Kubernetes · Terraform (IaC) · graph database at scale · OpenTelemetry
   (metrics/traces/logs) · incremental-recompute operator · SLOs · CI/CD · chaos tests
```

**Layer 1 — Pipeline.** Per-source harvesters normalize heterogeneous inputs (RDF/SPARQL endpoints, CSV/JSON dumps, text corpora) into candidate claims. Every transform is versioned and reproducible; every claim retains a lineage pointer to the exact source record that produced it. Batch-dominant with incremental refresh.

**Layer 2 — Ontology engine.** The claim store and its governed write path (§5–§6). Serves reads (entity views, filtered subgraphs, aggregations), maintains bitemporal history, and runs a change-data-capture indexer that keeps derived materializations (e.g., a "high-confidence network as of year Y") in sync as claims change.

**Layer 3 — Analytics.** Entity resolution (§9), relation extraction from texts, and the uncertainty machinery (§10). These read from and write claims back into the engine (an extraction produces claims with `method=extracted` and a confidence).

**Layer 4 — Ontology SDK.** A typed TypeScript client generated from the schema, so the app manipulates objects/links/claims/actions rather than raw tables — the analog of an operational SDK over an ontology.

**Layer 5 — Explorer.** §11.

**Layer 6 — Infrastructure.** §12.

---

## 9. Entity resolution

This is the make-or-break component and the single hardest data problem in the project. The same person appears across SDFB, letters, and biographical texts under name variants, with sparse and inconsistent attributes; "same name" is not "same person," and "different spelling" is not "different person."

- **Approach.** Blocking to generate candidate pairs, then **probabilistic record linkage** (Fellegi–Sunter as the classical baseline) augmented with embedding-based similarity over names and contexts, and **collective/relational** resolution that uses graph structure (shared correspondents, shared places) as evidence. Resolve entities to **shared authority identifiers** (e.g., Wikidata, and name-authority files) so cross-source identity is anchored, not re-guessed each time.
- **Uncertainty-aware output.** Resolution decisions are themselves claims with confidence. Ambiguous merges are surfaced for human adjudication via the `MergeEntities`/`SplitEntity` actions, not silently forced.
- **Evaluation (see §13).** Hand-label a gold set of match/non-match pairs; report precision and recall; tune the operating point deliberately and *state* it. Surfacing confidence honestly beats faking precision.

---

## 10. The uncertainty model (the differentiator — go deep here)

Almost no student project does this, which is exactly why it differentiates, and it is the direct answer to the cliodynamics critique.

- **Claim confidence from source reliability.** Each source carries a reliability model; a claim's confidence is derived from the reliability of its support and the method that produced it (a manual scholarly assertion, a dataset import, a model extraction, an algorithmic inference each carry different base credibility). Confidence is a **distribution**, not a bare scalar, so downstream computations can propagate it.
- **Aleatoric vs. epistemic.** Distinguish irreducible uncertainty (a date genuinely only known to a decade) from reducible uncertainty (a link we could pin down with more evidence). They behave differently and should be shown differently.
- **Calibration.** A stated confidence of 0.7 must mean something. Validate with reliability diagrams against held-out labeled claims (§13) and recalibrate.
- **Monte Carlo over the uncertain graph.** Sample over uncertain dates, uncertain links, and uncertain resolutions to produce **distributions** over derived quantities (centrality, community structure, network density over time) instead of false point estimates, plus sensitivity analysis. This is the rigorous way to honor contingency and "random chance": not mysticism, but disciplined uncertainty quantification.
- **Survivorship-bias-aware network inference.** The observed network is a biased sample — only some letters and records survived. Rather than treating the observed graph as complete, use statistical network models (exponential random graph models; latent-space network models) that can reason about missing/sampled ties. This is statistically sophisticated *and* the most historian-authentic move in the project — the "silence of the archives," formalized.
- **Optional causal / what-if layer (humble).** Encode hypothesized mechanisms as explicit structural models / DAGs and let users run governed "what-if" scenarios, testing predictions against the data — echoing an ontology's simulation capability, while stating plainly that this tests hypotheses and does not prove historical causation. Treat this as a Phase-3 stretch, not core.

---

## 11. The explorer application

A genuinely usable decision/exploration tool — non-expert usability is itself the Palantir operational-app skill.

- **Entity view.** Pick a person, place, polity, or event and see its properties and links, **each annotated with the evidence and confidence behind it**, and each traceable to the underlying source.
- **Map + timeline.** Geographic layout (via the gazetteer) and a time scrubber; the graph and map update as you move through time.
- **The signature interaction — the confidence slider.** Drag a confidence threshold and watch weakly-attested links appear and disappear. "Show the network as of 1590 at confidence ≥ 0.7" is the single most legible way to convey the entire thesis, and it is the centerpiece of the demo.
- **Competing claims, shown.** Where sources disagree, both claims are visible with their support; the UI never hides the disagreement.
- **Write-back actions.** A scholar can assert, dispute, supersede, merge, or split, and see the audit trail. This is what makes it operational rather than a static visualization.

---

## 12. Infrastructure (the FDIE surface — half the evaluation for infra roles)

- **Orchestration and IaC.** Kubernetes for services; Terraform for reproducible provisioning.
- **Storage.** A graph database sized for the claim store, plus object/relational storage for source documents and lineage; indices for temporal and geospatial queries.
- **Observability.** OpenTelemetry across the pipeline → engine → app path (metrics, distributed traces, structured logs); dashboards; defined SLOs (e.g., query latency, ingestion freshness).
- **Incremental recompute.** A custom operator/controller (Go) that, when sources or claims change, recomputes only the affected derived views rather than everything — a real "automate the manual runbook" artifact.
- **CI/CD and resilience.** Automated build/test/deploy; chaos/failure tests that prove graceful degradation.

---

## 13. Testing and validation (credibility depends on this)

- **Entity resolution.** Gold-labeled match/non-match pairs; precision/recall; a stated, deliberate operating point.
- **Calibration.** Reliability diagrams on held-out labeled claims; recalibrate until stated confidence tracks observed correctness.
- **Pipeline.** Reproducibility (same inputs → same claims), lineage completeness (every claim traces to a source), and schema-evolution handling.
- **Leakage and artifact traps (state that you avoided these).** For any network/temporal inference, use spatial-block and temporal forward-chaining validation rather than naïve random splits, which leak on spatiotemporal data. For any comparative pattern, check sensitivity to data gaps — the exact failure mode cliodynamics is criticized for.
- **Load.** Confirm interactive query latency at the graph sizes you target.

---

## 14. Language map (uses the languages these roles list, each where it belongs)

| Language | Component | Why |
|---|---|---|
| **Python** | Harvesting, entity resolution, NER/relation extraction, Monte Carlo, uncertainty math, optional causal modeling | The data-science and NLP ecosystem |
| **Java / Scala** | The uncertainty-aware claim/ontology engine, indexing, write-back | High-throughput stateful backend; aligns with a JVM-heavy production stack |
| **C++** | Performance kernel: large-graph algorithms (centrality, community detection) and the Monte Carlo inner loop, via bindings | The hot path that must be fast, called from Python/JVM — where post-graduate C++ shows |
| **TypeScript/JS** | The explorer (React + map + graph visualization) and the generated SDK | User-facing surface + typed client |
| **Go** | Infra tooling, harvester orchestration, the incremental-recompute operator, CLI | Idiomatic cloud-native control planes; the FDIE surface |
| *(supporting)* | RDF/SPARQL, a graph database, Terraform | "Storage systems" and "cloud infrastructure" |

---

## 15. Data sources for the core (grounded; verify license/API/export before use)

| Layer | Source | Notes / caution |
|---|---|---|
| People / ties | Six Degrees of Francis Bacon | Downloadable; edges carry confidence; NLP-inferred from the ODNB |
| Correspondence | Mapping the Republic of Letters; Cultures of Knowledge / EMLO | Dated, directed epistolary links |
| Places | World Historical Gazetteer (+ GeoNames/Wikidata for linkage) | 2M+ records; competing-claims by design |
| Environment | NOAA Paleoclimatology / ITRDB | 2,000+ sites; dendrochronology also dates timber |
| Polities (light overlay) | Seshat: Global History Databank | Open but **non-commercial** license; provenance-tracked |
| Texts (extraction substrate) | EMLO; large digital libraries (e.g., HathiTrust computational access) | Mind copyright/use terms |
| Events | (thin for this period) text extraction; GDELT/ACLED are **modern-era only** | GDELT ~55% field accuracy, ~20% redundancy, dedup by URL; not applicable pre-1979 |

---

## 16. Scope discipline and phased build

**Pick one slice; keep the architecture general.** Do not attempt all of history. Build early-modern Europe well, and make the engine domain-general so breadth is *demonstrated by design*. Being able to say "here is the slice I built, and here is exactly how the architecture generalizes" is the senior move.

**Phase 0 — Design.** Write the claim model, the confidence/provenance model, the chosen slice, and explicit non-goals. This document is itself the "startup CTO" artifact reviewers value.

**Phase 1 — MVP spine (strong on its own).** Ingest 2–3 structured layers (SDFB people + a correspondence set + one climate series); build the claim-based graph with provenance and confidence; a read-only explorer with the confidence slider; deploy on Kubernetes with basic observability and one SLO. *Done when:* you can browse a real person's evidence-backed, confidence-filtered network in a deployed app.

**Phase 2 — Fusion and kinetics.** Cross-source **entity resolution** to merge people across datasets; governed **write-back** (assert/dispute/merge) with audit history and bitemporality; add the places layer fully (map + timeline). *Done when:* a scholar can correct a merge and dispute a claim, and the history is preserved.

**Phase 3 — Senior differentiators.** Relation extraction from a text corpus; **survivorship-bias-aware** network inference; **Monte Carlo** uncertainty propagation with distributions over metrics and calibration; the C++ performance kernel; full observability with the incremental-recompute operator; the generated TypeScript SDK; a written scaling analysis; optionally the humble causal/what-if layer. *Done when:* the differentiators in §10 work end-to-end and are validated per §13.

Each phase is a real stopping point that still demonstrates value.

---

## 17. Anti-patterns (traps specific to this project)

- **Chasing "completeness."** Scope a slice; generalize by architecture. "Complete model of history" reads as naïve.
- **A knowledge graph with no confidence.** Without first-class, calibrated uncertainty, this is just another graph. The uncertainty is the whole point.
- **False precision / silent merging.** Collapsing contradictory sources into one "fact" discards the most honest and most Palantir-relevant part of the system. Keep competing claims.
- **Leakage in inference.** Naïve random validation lies on spatiotemporal/network data. Use block and forward-chaining validation and say so.
- **Ignoring survivorship bias.** Treating the observed network as complete produces exactly the spurious patterns cliodynamics is criticized for. Model the sampling.
- **Claiming to prove causation.** The optional causal layer tests hypotheses; it does not settle why anything happened. Say so.
- **Infra as an afterthought.** For infra roles this is fatal: graph-scale storage, incremental recompute, and observability are half the evaluation.
- **Taking a side in the cliodynamics dispute.** You are building rigorous infrastructure, not defending a theory of history.

---

## 18. Packaging and demo

- **A 5-minute screen recording** framed for a non-expert: pick one figure, show the evidence-backed network and timeline, then **drag the confidence slider** and watch weakly-attested links vanish. That single interaction conveys the entire thesis and doubles as the optional application demo.
- **An architecture write-up** with the claim model, the confidence/provenance model, a "what I cut and why," and a "how this scales" section. Judgment and tradeoff-reasoning are the FDE signal.
- **A data-integration war story:** the hardest entity-resolution case (the same person spelled several ways across sources, and how you resolved it and with what confidence). This is the most FDSE-authentic artifact you can show.
- **An honest limitations section** citing the cliodynamics debate and your response to it. Reviewers trust candidates who state limits.
- **One-command deploy** if feasible, or the Terraform + Kubernetes manifests and observability dashboards.

Order for a reviewer: demo → architecture doc → the entity-resolution + uncertainty engine (your hardest work) → the infra/observability → the scaling analysis.

---

## 19. Definition of "core complete" (this is the gate for Part 2)

**Do not begin Part 2 until all of the following are true.** These are the exact prerequisites Part 2 checks for.

1. The **claim engine** is built: claims are stored with support, method, confidence, bitemporal history, and competing-claim representation.
2. **At least the early-modern people + correspondence + one environment layer** are ingested through the pipeline with full lineage.
3. **Cross-source entity resolution** runs and is evaluated (precision/recall on a gold set).
4. **Governed write-back** works: assert/dispute/merge/split with audit history.
5. The **explorer** is deployed and usable by a non-expert, including the confidence slider and evidence transparency.
6. **Infrastructure** basics are real: Kubernetes deployment, IaC, observability, at least one SLO, and the incremental-recompute path.
7. At least the **calibration** and **survivorship-bias-aware inference** differentiators are working and validated (the rest of §10 may still be in progress).

When these hold, the foundation is genuinely built — and only then does expanding into new periods (Part 2) add value rather than surface area.

---

### One-line pitch
> Built a temporal, uncertainty-aware knowledge graph of the early-modern past that fuses heterogeneous public datasets into a single model where every relationship is a time-scoped, provenance-backed, confidence-weighted claim — with cross-source entity resolution, survivorship-bias-aware network inference, and Monte Carlo uncertainty propagation — served through an evidence-transparent explorer and deployed on Kubernetes with full observability.

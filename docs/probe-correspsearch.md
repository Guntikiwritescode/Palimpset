# correspSearch coverage probe (WP2b step 1) — GO

**Date:** 2026-07-18 · **Probe by:** implementer · **Decision:** **GO** (architect ratified "do both").

The handoff makes correspSearch the project's *next external check* (§20 A1): with a
single source there are no competing claims and no cross-source entity resolution,
so this gates the thesis. This probe answers the two open questions in §11
("API + CC-BY 4.0 verified; volume in 1500–1700 unknown").

## Evidence (measured against the live API)

### 1. License — CC-BY 4.0, stated in-band ✓
Every API response carries, in its `teiHeader`:

```xml
<availability>
  <licence target="https://creativecommons.org/licenses/by/4.0/">CC-BY 4.0</licence>
</availability>
```

CC-BY 4.0 permits reuse with attribution — materially different from the SDFB
dump (license unconfirmed). correspSearch data is therefore **not** gated the way
SDFB is; attribution is preserved via the edition `source`/`ref` on each letter.

### 2. Volume in the 1500–1700 window — substantial ✓
Queried well-attested early-modern correspondents on the **v1.1 production API**:

| Correspondent | GND | letters (one page) | date span | in-window |
|---|---|---|---|---|
| Martin Luther | 118575449 | **503** | 1518–1546 | 503/503 |
| Erasmus of Rotterdam | 118530666 | 31 | 1519–1536 | 31/31 |

Luther year histogram (excerpt): 1518:10 · 1521:14 · 1525:20 · 1530:69 · 1540:41 ·
1545:28 · 1546:11. The only out-of-window "year" is `2026`, which is the API's
response `<date>` timestamp, not a letter date. **0 letters fall outside 1500–1700.**

### 3. API choice — v1.1, not v2.0 ✓
The **v2.0 BETA endpoint returns a static stub** — the same 10 placeholder
`correspDesc` entries (Else Lasker-Schüler; a `1093` date) regardless of the query
params, so its date/correspondent filters do not apply. The **v1.1 production
endpoint** (`https://correspsearch.net/api/v1.1/tei-xml.xql?correspondent=<GND-URI>`)
returns real, filtered CMIF/TEI. **The adapter targets v1.1.**

### 4. CMIF structure (informs the adapter, A2)
```xml
<correspDesc source="#BAKFJ-Online" ref="https://bakfj.saw-leipzig.de/print/675" key="675">
  <correspAction type="sent">
    <persName ref="http://d-nb.info/gnd/118575449">Martin Luther</persName>
    <placeName ref="http://www.geonames.org/7303020">[Wittenberg]</placeName>
    <date when="1518-02-15">15. Februar 1518</date>
  </correspAction>
  <correspAction type="received">
    <persName ref="http://d-nb.info/gnd/118798170">Georg Spalatin</persName>
  </correspAction>
</correspDesc>
```

Adapter mapping (normative, A2):
- Persons are identified by **GND** URIs (`http://d-nb.info/gnd/<id>`) → the
  correspSearch authority is **`gnd`** (identity-bearing 1:1 anchor).
- Each `correspDesc` → one **letter**: a `document` entity (external id = `key`,
  edition via `source`/`ref`) and a **`corresponded-with`** claim, directed
  sender→receiver, dated from `date when`/`notBefore`/`notAfter` (four-date model).
- `corresponded-with` claims are **unscored** — a letter attests the tie, but
  correspSearch provides no confidence score and we invent none (I5). This is the
  first source of genuinely **unscored relationship claims** (D4/Q-2: the dashed
  canvas grammar earns its wiring at WP2b).
- Support = a `source_record` carrying the verbatim CMIF fragment + the edition
  `ref` URL (lineage); source slug `correspsearch` (license_confirmed **true**,
  CC-BY 4.0).

## What this unlocks — and the one honest caveat
- **Cross-source entities & competing claims** become demonstrable: a person can
  carry an SDFB `associated-with` (scored) *and* a correspSearch `corresponded-with`
  (unscored) — the pair dossier shows both; PP3 becomes falsifiable.
- **Caveat (cross-source linking):** correspSearch identifies people by **GND**,
  SDFB by **sdfb** ids. Linking the same real person across the two authorities is
  cross-source **entity resolution (WP7)** — via a shared hub (Wikidata/VIAF) or a
  `same-as` claim. So the *real* two-source overlap depends on WP7. Per the §14.3/A5
  pattern, WP2b builds the real adapter (tested on synthetic CMIF) and demonstrates
  the cross-source + competing-claims **UX** on the **synthetic fixture** (A3),
  where both synthetic sources share the `synth` authority so overlap is exact and
  needs no ER. The real GND↔sdfb harvest is the slow/local path.

## Decision
**GO.** License and volume both clear the gate. Proceed to A2 (adapter) and A3
(synthetic second source). Findings to the architect before the WP2b merge, as the
standing rule requires.

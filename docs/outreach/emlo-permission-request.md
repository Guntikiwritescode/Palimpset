# Draft — EMLO bulk-use permission request

**To:** the Cultures of Knowledge / Early Modern Letters Online (EMLO) team,
Bodleian Libraries, University of Oxford · **Suggested address:** the EMLO
contact/feedback form, or the Cultures of Knowledge project contacts.
**From:** [your name] · **Subject:** Requesting permission for bulk use of EMLO metadata

---

Dear EMLO team,

I'm building a non-commercial, portfolio research project that models early-modern
correspondence and social networks as an uncertainty-aware knowledge graph, with
full attribution to each source.

For the letters layer I currently use **correspSearch** (the union catalogue of
scholarly correspondence metadata, CMIF/TEI), which is openly available for reuse.
EMLO is the richer catalogue I'd most like to draw on next, but I understand that
EMLO's terms permit consultation of individual records while **bulk use / bulk
download of the metadata requires prior permission**. I'd therefore like to ask
whether, and on what terms, I could obtain and process EMLO correspondence metadata
in bulk (for example, an epistolary metadata export, or the CMIF/TEI slices EMLO
contributes).

To be concrete about the use:

- **Non-commercial** only; a personal portfolio / research demonstrator, not a
  product;
- the metadata is **not redistributed** — I ingest it into a private system and
  display derived, attributed views (each letter and relationship marked as
  *derived from EMLO*, linking back to the EMLO record);
- I will honour whatever **citation form, embargo, or snapshot policy** you specify,
  and I'm happy to sign a data-use agreement;
- until I have written permission, no EMLO-derived data is publicly deployed or
  published — this is enforced mechanically in the system (a per-source licence
  gate), not merely intended.

Nothing in the current build depends on this — correspSearch already provides the
Part 1 letters layer — so there is no time pressure at your end. I'm asking now
because an EMLO adapter would be a natural, high-value next source **if** bulk use
can be granted.

Could you let me know the process and any terms? Thank you for building and
sustaining EMLO — it's an extraordinary resource for the period.

Best regards,
[your name]
[affiliation / contact]

---
*Note (do not send): EMLO is deliberately **not** a Part 1 dependency — correspSearch
is the Part 1 letters source. An EMLO adapter is contract-first like every other
adapter (person entities keyed to a shared authority, letters as document entities,
`corresponded-with` as UNSCORED claims dated via the four-date model), so if bulk
use is granted it slots in with no schema change (V2 covers it). Until a written
grant arrives, the EMLO source stays `license_confirmed=false` and no EMLO-derived
data leaves the system.*

# Draft — Folger / SDFB richer-export data request

**To:** the Folger Shakespeare Library (SDFB data steward) / the SDFB project.
**From:** [your name] · **Subject:** Requesting the fuller SDFB relational export

---

Dear [Folger / SDFB data steward],

I'm using the public Six Degrees of Francis Bacon export in a non-commercial
research project (attribution to SDFB throughout). The downloadable export I have
gives person records and a person-to-person relationship table with a single
aggregate confidence per tie. I understand a **richer relational export** exists,
and it would substantially strengthen the modelling. Where possible, could I obtain
the following additional tables/fields:

1. **Relationship types** — the typed predicate for each tie (rather than a single
   generic "associated-with"), with each type's own confidence, dates,
   justification, and citation.
2. **Relationship and group categories.**
3. **Person and group notes.**
4. **Sub-year date precision** — day/month where recorded, not only year.
5. **An alternate birth/death year per person**, where the sources disagree — i.e.
   the source-native *competing* life dates.

Item 5 is the most valuable for my work: my system's whole point is representing
competing claims from disagreeing sources rather than collapsing them, so a
source-native alternate life date is exactly the kind of evidence it's built to show.

I'm happy to work within any access conditions (a data-use agreement, a fixed
snapshot, non-redistribution — my system does not republish the data). Please let me
know the process and any terms.

With thanks,
[your name]
[affiliation / contact]

---
*Note (do not send): these fields map to already-reserved parts of the model —
typed predicates become distinct `relation_type` slugs; sub-year precision fills the
four-date model's finer bounds; the alternate life date lands as a second, competing
`born`/`died` claim under a distinct source. No schema change is required (V2 covers it).*

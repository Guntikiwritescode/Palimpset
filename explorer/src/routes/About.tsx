import { Link } from "react-router-dom";
import { api } from "../api/client";
import type { ImportRun, MetaSource, StatsSummary } from "../api/types";
import { useAsync } from "../lib/useAsync";
import { ErrorState, Skeleton } from "../components/States";
import { replayTour } from "../components/Tour";

// GOVERNING RULE (§16): every number on this page is computed live from the corpus.
// Nothing is typed into markup. The retracted precision figure is OMITTED (D1/Q-1):
// calibration is Phase-3 work, stated without a precision number.

const pct = (n: number): string => `${(n * 100).toFixed(1)}%`;

const BAND_DISPLAY: Record<string, string> = {
  very_strong: "very strong (≥ 0.90)",
  strong: "strong (0.70–0.89)",
  moderate: "moderate (0.40–0.69)",
  weak: "weak (0.20–0.39)",
  very_weak: "very weak (< 0.20)",
  unscored: "unscored",
};

interface AboutData {
  stats: StatsSummary;
  runs: ImportRun[];
  sources: MetaSource[];
}

function Histogram({ stats }: { stats: StatsSummary }) {
  const max = Math.max(1, ...stats.confidenceHistogram.map((b) => b.count));
  return (
    <div className="histo">
      {stats.confidenceHistogram.map((b) => (
        <div className="hrow" key={b.band}>
          <span>{BAND_DISPLAY[b.band] ?? b.band}</span>
          <span className="hbar" style={{ width: `${(b.count / max) * 100}%` }} aria-hidden="true" />
          <span className="num" style={{ textAlign: "right" }}>
            {b.count}
          </span>
        </div>
      ))}
    </div>
  );
}

export function AboutRoute() {
  const { data, error, loading, reload } = useAsync<AboutData>(
    () => Promise.all([api.getStats(), api.getRuns(), api.getSources()]).then(([stats, runs, sources]) => ({ stats, runs, sources })),
    [],
  );

  if (loading) return <div className="panel"><Skeleton rows={8} /></div>;
  if (error || !data) return <ErrorState error={error} onRetry={reload} />;

  const { stats, runs, sources } = data;
  const genderTotal = stats.gender.male + stats.gender.female + stats.gender.unknown || 1;

  return (
    <div>
      <h1>What the numbers mean</h1>

      {/* Panel 1 — What this is */}
      <section className="panel">
        <h2>What this is</h2>
        <p>
          An uncertainty-aware knowledge graph of early-modern people and the ties between them, assembled from
          historical sources. It is a research instrument, not an encyclopedia.
        </p>
        <p>
          <strong>
            Nothing here is presented as established fact. Every statement is a claim, attributed to a source,
            with an explicit measure of how well that source supports it.
          </strong>
        </p>
      </section>

      {/* Panel 2 — What's in the corpus (LIVE) */}
      <section className="panel">
        <h2>What's in the corpus</h2>
        <p>
          <b>{stats.entities.total.toLocaleString()}</b> people ·{" "}
          <b>{stats.claims.total.toLocaleString()}</b> claims ·{" "}
          <b>{stats.sourceRecords.toLocaleString()}</b> source records ·{" "}
          <b>{sources.length}</b> {sources.length === 1 ? "source" : "sources"}.
        </p>
        <div className="grid-2">
          <div>
            <h3>People by type</h3>
            <table className="stat-table">
              <tbody>
                {Object.entries(stats.entities.byType).map(([t, n]) => (
                  <tr key={t}>
                    <td>{t}</td>
                    <td className="num">{n.toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div>
            <h3>Claims by predicate</h3>
            <table className="stat-table">
              <tbody>
                {Object.entries(stats.claims.byPredicate).map(([p, n]) => (
                  <tr key={p}>
                    <td>{p.replace(/-/g, " ")}</td>
                    <td className="num">{n.toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
        {stats.span && (
          <p className="small">
            The slice is described as {stats.span.advertised}, but the record is measured, not billed:{" "}
            {stats.preSliceBirths && (
              <>
                <b>{pct(stats.preSliceBirths.share)}</b> of people carry a birth year before 1500 (earliest{" "}
                {stats.preSliceBirths.earliestYear}).
              </>
            )}{" "}
            Measured span: {stats.span.earliestYear}–{stats.span.latestYear}.
          </p>
        )}
      </section>

      {/* Panel 3 — What the numbers mean (LIVE) */}
      <section className="panel">
        <h2>What the numbers mean</h2>
        <Histogram stats={stats} />
        {stats.thresholdShares && (
          <p className="small">
            Only <b>{pct(stats.thresholdShares.atLeast90)}</b> of relationship claims sit at ≥ 0.90 and{" "}
            <b>{pct(stats.thresholdShares.atLeast60)}</b> at ≥ 0.60 — a filtered view is a small, deliberately
            chosen slice of the record.
          </p>
        )}
        <p className="small">
          The scores are the source's own, normalized. A score is a <em>bootstrap selection frequency</em> — how
          often an edge survived resampling — not a probability that the relationship existed. It has{" "}
          <strong>not been calibrated</strong> against ground truth; treat it as a ranking signal, not a
          probability. Calibration is Phase-3 work, and no precision figure is quoted here until the source's own
          validation has been read directly.
        </p>
      </section>

      {/* Panel 4 — What's missing, and how the record is biased (LIVE) */}
      <section className="panel">
        <h2>What's missing, and how the record is biased</h2>
        <p className="small">
          <b>{pct(stats.gender.male / genderTotal)}</b> of people are recorded male,{" "}
          <b>{pct(stats.gender.female / genderTotal)}</b> female
          {stats.gender.unknown > 0 && <> , {pct(stats.gender.unknown / genderTotal)} unknown</>}.{" "}
          <b>{pct(stats.noRelationshipPct)}</b> have no recorded relationship at all in this source.
        </p>
        <p className="small">
          The people layer derives from a biographical dictionary, so “who is in the graph” reflects who was
          judged notable by a particular editorial tradition, not who existed — and the surviving record
          over-represents the literate, propertied, and institutionally connected. An absent tie means{" "}
          <em>no surviving attested evidence in these sources</em>, never <em>no relationship</em>.
        </p>
      </section>

      {/* Panel 5 — Method notes (LIVE where measured) */}
      <section className="panel">
        <h2>Method notes</h2>
        <ul className="small">
          <li>
            <b>Date-code interpretation — unconfirmed.</b>{" "}
            <b>{pct(stats.temporalCodeShare.share)}</b> of relationship intervals rest on the single code{" "}
            <span className="mono">{stats.temporalCodeShare.code}</span>. Every time filter and the decade strip
            inherit one reading of it. Corroborated by an independent converter, not confirmed against the
            source's own code.
          </li>
          <li>
            <b>Circa dates.</b> A <span className="mono">c.</span> date renders with an <em>approximate</em>{" "}
            marker and no invented ± window — manufacturing a numeric interval the source never states would be
            the date-layer equivalent of inventing a confidence number.
          </li>
          <li>
            <b>Possibly vs certainly.</b> “Possibly active” means the evidence does not rule a tie out during a
            window; “certainly active” means the evidence <em>requires</em> it. The distinction is between what
            the record permits and what it requires — the UI never blurs it.
          </li>
        </ul>
      </section>

      {/* Panel 6 — Data-quality findings (LIVE) */}
      <section className="panel">
        <h2>Data-quality findings — found and handled</h2>
        <table className="stat-table">
          <tbody>
            {Object.entries(stats.anomalyCounters).map(([k, v]) => (
              <tr key={k}>
                <td>{k.replace(/([A-Z])/g, " $1").replace(/^./, (m) => m.toUpperCase())}</td>
                <td className="num">{v.toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="small muted">
          Each counter is a class of anomaly the pipeline inspects for and records — a strength, not a
          confession.
        </p>
      </section>

      {/* Panel 7 — Provenance and change (LIVE) */}
      <section className="panel">
        <h2>Provenance and change</h2>
        <table className="stat-table">
          <thead>
            <tr>
              <th>Run</th>
              <th>Source version</th>
              <th className="num">Inserted</th>
              <th className="num">Duplicates</th>
              <th className="num">Rejected</th>
              <th>Finished</th>
            </tr>
          </thead>
          <tbody>
            {runs.map((r) => (
              <tr key={r.id}>
                <td className="mono">{r.id}</td>
                <td className="mono">{r.sourceVersion}</td>
                <td className="num">{r.inserted.toLocaleString()}</td>
                <td className="num">{r.duplicates.toLocaleString()}</td>
                <td className="num">{r.rejected.toLocaleString()}</td>
                <td className="small">{r.finishedAt ? r.finishedAt.slice(0, 10) : "in progress"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {/* Panel 8 — License and access (LIVE) */}
      <section className="panel">
        <h2>License and access</h2>
        <table className="stat-table">
          <thead>
            <tr>
              <th>Source</th>
              <th>License</th>
              <th>Confirmed</th>
            </tr>
          </thead>
          <tbody>
            {sources.map((s) => (
              <tr key={s.slug}>
                <td>{s.title}</td>
                <td>{s.license}</td>
                <td>{s.licenseConfirmed ? "yes" : "no"}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {sources.some((s) => !s.licenseConfirmed) && (
          <p className="warn">
            This system is not publicly deployed because at least one source's terms are unresolved. Export
            refuses that source's content, and there is no public URL while the license is unconfirmed —
            internal use only.
          </p>
        )}
      </section>

      {/* Panel 9 — What this system cannot tell you */}
      <section className="panel">
        <h2>What this system cannot tell you</h2>
        <ul className="small">
          <li>It cannot tell you two people <em>didn't</em> know each other — absence is missing evidence, not proof.</li>
          <li>It cannot tell you a relationship's nature beyond what the source recorded — these are generic associations, with no typed predicate.</li>
          <li>It cannot support causal inference.</li>
          <li>It cannot be treated as a sample of early-modern society — only as a sample of what survived and was judged worth recording.</li>
        </ul>
      </section>

      {/* Panel 10 — Technical view */}
      <section className="panel">
        <h2>The technical view</h2>
        <p className="small">
          Immutable claims, an append-only event log, and a single write authority. Reads flow through a small
          typed client; every rendered fact reaches its source record in ≤ 2 clicks.
        </p>
        <p className="small">
          <button className="btn" onClick={() => { replayTour(); window.location.href = "/"; }} data-testid="replay-tour">
            Replay the first-run tour
          </button>{" "}
          <Link to="/">Back to search</Link>
        </p>
      </section>
    </div>
  );
}

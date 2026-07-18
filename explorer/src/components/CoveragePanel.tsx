import type { Coverage } from "../api/types";

/**
 * Coverage panel (§13.2d): per-source contribution for THIS entity, the
 * scored/unscored split, and the calibration status. "What's actually here?"
 * answered locally.
 */
export function CoveragePanel({ coverage }: { coverage: Coverage }) {
  return (
    <section className="panel" aria-label="Coverage for this person">
      <h2>What's actually here</h2>
      <table className="stat-table">
        <thead>
          <tr>
            <th>Source</th>
            <th className="num">Relationship claims</th>
            <th className="num">Attribute claims</th>
          </tr>
        </thead>
        <tbody>
          {coverage.bySource.map((s) => (
            <tr key={s.slug}>
              <td>{s.slug}</td>
              <td className="num">{s.relationshipClaims}</td>
              <td className="num">{s.attributeClaims}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="small">
        <b>{coverage.scored}</b> scored · <b>{coverage.unscored}</b> unscored ·{" "}
        <span className="muted">confidence {coverage.calibration}</span>
      </p>
    </section>
  );
}

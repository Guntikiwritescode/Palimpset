import type { ClaimDetail, SourceRef } from "../api/types";

/** Amber license badge for any content from a source with license_confirmed=false (PP6). */
export function LicenseBadge({ source }: { source: SourceRef }) {
  if (source.licenseConfirmed) return null;
  return (
    <span className="badge-amber" title="Source license unconfirmed — internal use only.">
      ⚠ Source license unconfirmed — internal use only
    </span>
  );
}

/** Provenance strip: method · source · source's own score · calibration status (§13.3). */
export function ProvenanceStrip({ claim, sourceSlug }: { claim: ClaimDetail; sourceSlug?: string }) {
  const raw = claim.confidence.raw;
  const scorePart = claim.confidence.scored && raw != null ? `max_certainty ${String(raw)}` : "unscored";
  const calib = claim.confidence.calibrated ? "calibrated" : "uncalibrated";
  const src = sourceSlug ?? claim.assertedBy.slug;
  return (
    <div className="provenance">
      {claim.method} · {src} · {scorePart} · {calib}
    </div>
  );
}

/** Status grammar (§13.3): disputed struck-through-but-visible; superseded dimmed+link. */
export function statusClass(status: string): string {
  if (status === "disputed") return "disputed";
  if (status === "superseded") return "superseded";
  return "";
}

export function StatusNote({ status }: { status: string }) {
  if (status === "disputed") {
    return (
      <span className="small" style={{ color: "var(--danger)" }} title="Contested — shown, not refuted.">
        contested
      </span>
    );
  }
  if (status === "superseded") {
    return (
      <span className="small muted" title="Superseded by a later claim.">
        superseded
      </span>
    );
  }
  return null;
}

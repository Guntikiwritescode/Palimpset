// Copy-citation (§13.8): a text block carrying the assertion, its source, the
// confidence AND its calibration status, the accessed date, and the reproducing
// URL. It propagates the uncertainty caveat into whatever a scholar writes.

import type { ClaimDetail, Evidence } from "../api/types";
import { confidenceChipText } from "./confidence";
import { formatValidTime } from "./fuzzyDate";

function objectText(claim: ClaimDetail): string {
  if (claim.object.entity) return claim.object.entity.displayName;
  if (claim.object.literal) return claim.object.literal.value;
  return "—";
}

/** Neutral, attributed assertion — never "X knew Y" (§13.5 rule 2). */
export function assertionText(claim: ClaimDetail): string {
  const subj = claim.subject.displayName;
  const obj = objectText(claim);
  const src = claim.assertedBy.displayName;
  const when = formatValidTime(claim.validTime);
  const period = when === "date unknown" ? "" : ` (${when})`;
  return `${src} records "${claim.predicate}" between ${subj} and ${obj}${period}.`;
}

export interface CitationInput {
  claim: ClaimDetail;
  evidence?: Evidence;
  reproducingUrl: string;
  accessedDate?: Date;
}

export function buildCitation(input: CitationInput): string {
  const { claim, evidence, reproducingUrl } = input;
  const accessed = (input.accessedDate ?? new Date()).toISOString().slice(0, 10);
  const conf = confidenceChipText(claim.confidence);
  const calib = claim.confidence.calibrated ? "calibrated" : "uncalibrated";
  const source = evidence?.support[0]?.source;
  const lines = [
    assertionText(claim),
    `Attestation: ${claim.assertedBy.displayName} (${claim.method}).`,
  ];
  if (source) {
    lines.push(`Source: ${source.title}, version ${source.version} — ${source.retrievalUri}.`);
    if (!source.licenseConfirmed) {
      lines.push(`License: unconfirmed — internal use only.`);
    }
  }
  lines.push(`Confidence: ${conf} [${calib}].`);
  lines.push(`Accessed: ${accessed}.`);
  lines.push(`Reproducing view: ${reproducingUrl}`);
  return lines.join("\n");
}

export interface ViewCitationInput {
  entityName: string;
  filterSummary: string;
  reproducingUrl: string;
  accessedDate?: Date;
}

/** Citation for a filtered view (§13.8) — carries the filter state and the caveat. */
export function buildViewCitation(input: ViewCitationInput): string {
  const accessed = (input.accessedDate ?? new Date()).toISOString().slice(0, 10);
  return [
    `PALIMPSEST view of ${input.entityName}.`,
    `Filter: ${input.filterSummary}.`,
    `Confidence scores are the source's own, normalized and uncalibrated — a ranking signal, not a probability.`,
    `Accessed: ${accessed}.`,
    `Reproducing view: ${input.reproducingUrl}`,
  ].join("\n");
}

export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    /* fall through */
  }
  return false;
}

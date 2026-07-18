import { useState } from "react";
import type { ClaimDetail, Evidence } from "../api/types";
import { buildCitation, copyToClipboard } from "../lib/citation";

interface Props {
  claim: ClaimDetail;
  evidence?: Evidence;
  label?: string;
}

/**
 * Copy-citation (§13.8): assertion + source + confidence AND calibration status +
 * accessed date + reproducing URL. Propagates the uncertainty caveat into footnotes.
 */
export function CitationButton({ claim, evidence, label = "Copy citation" }: Props) {
  const [copied, setCopied] = useState(false);
  const [open, setOpen] = useState(false);
  const reproducingUrl = typeof window !== "undefined" ? window.location.href : "";
  const text = buildCitation({ claim, evidence, reproducingUrl });

  return (
    <div>
      <button
        className="btn"
        onClick={async () => {
          const ok = await copyToClipboard(text);
          setCopied(ok);
          if (!ok) setOpen(true);
        }}
        data-testid="copy-citation"
      >
        {copied ? "Citation copied ✓" : label}
      </button>{" "}
      <button className="btn" onClick={() => setOpen((o) => !o)}>
        {open ? "Hide" : "Show"} citation text
      </button>
      {open && (
        <pre className="raw" data-testid="citation-text" style={{ marginTop: "0.5rem" }}>
          {text}
        </pre>
      )}
    </div>
  );
}

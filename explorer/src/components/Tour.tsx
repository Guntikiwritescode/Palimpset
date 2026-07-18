import { useEffect, useState } from "react";

const KEY = "palimpsest.tour.dismissed.v1";

const STEPS = [
  "This is a historical knowledge graph where every link is a claim backed by a source.",
  "Drag the slider to filter by how strongly a link is attested.",
  "Click any link to see the evidence behind it — down to the original record.",
  "Nothing here is presented as certain. The About page explains what the numbers mean.",
];

export function tourDismissed(): boolean {
  try {
    return localStorage.getItem(KEY) === "1";
  } catch {
    return true;
  }
}

export function replayTour(): void {
  try {
    localStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
}

/**
 * First-run tour (§13.8): four dismissible steps, persisted in localStorage, never
 * reappears uninvited, skippable in one click, does not block the pre-set view.
 */
export function Tour() {
  const [step, setStep] = useState(0);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    setOpen(!tourDismissed());
  }, []);

  if (!open) return null;

  const dismiss = () => {
    try {
      localStorage.setItem(KEY, "1");
    } catch {
      /* ignore */
    }
    setOpen(false);
  };

  const last = step === STEPS.length - 1;
  return (
    <div className="tour-backdrop" role="dialog" aria-modal="true" aria-label="Welcome tour">
      <div className="tour-card">
        <p className="steps">
          Step {step + 1} of {STEPS.length}
        </p>
        <p>{STEPS[step]}</p>
        <div className="actions">
          <button className="btn" onClick={dismiss} data-testid="tour-skip">
            Skip
          </button>
          {last ? (
            <button className="btn" onClick={dismiss}>
              Done
            </button>
          ) : (
            <button className="btn" onClick={() => setStep((s) => s + 1)}>
              Next
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

# analytics/ — Python (WP7+)

Batch jobs that read the store with the `analytics_ro` role and contribute
conclusions back **as claims** through the engine (never direct DB writes).
Part 1 scope begins at WP7 (entity resolution: blocking → Fellegi–Sunter →
collective features; emits `same-as` claims + an adjudication queue). Calibration,
Monte Carlo, and survivorship-bias-aware inference are WP9. Evaluation is
forward-chaining / spatial-block only — never naive random splits (§3.5, §13).

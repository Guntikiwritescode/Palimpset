---
paths: ["deploy/**"]
---
# deploy/ — the no-Ingress rule is a hard gate

**No Ingress until the SDFB license is confirmed** (ARCHITECTURE §3.8, §5.6,
§11; HANDOFF §11 tripwire). Access is `kubectl port-forward` only. The gate is
enforced at the network layer, not by intention — and mechanically by
`scripts/check_no_ingress.sh` (pre-commit + CI): no manifest may declare
`kind: Ingress` (or a Gateway/HTTPRoute/LoadBalancer public exposure) while the
license is unconfirmed.

Other standing constraints here:
- Secrets (DB password, scholar bearer token) are Kubernetes `Secret` objects,
  **referenced not embedded** — never in images, manifests, or git history (§7).
- Postgres roles are least-privilege at the DB: `engine_rw`, `analytics_ro`,
  `migrate`.
- Two namespaces: `palimpsest`, `observability`. Kustomize bases + overlays
  (`local` now; `cloud` overlay is a stub until the ADR-003 checkpoint).
- The restore drill is documented in `deploy/README.md` next to the reason the
  system is not publicly exposed.

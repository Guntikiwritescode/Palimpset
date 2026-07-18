# cloud-stub — deferred cloud overlay (ADR-003 checkpoint)

**Status: STUB. Do not deploy.** This overlay is a placeholder so the
local→cloud seam is explicit in the repo. The standing cloud cluster is
deferred by **ADR-003** until the WP5 review checkpoint decides it is worth
standing up. Until then, `overlays/local` (kind) is the only real target.

## The gate that does not move

Whatever a cloud overlay eventually looks like, it inherits the **no-Ingress
hard gate** (`ARCHITECTURE.md` §3.8, §11; `.claude/rules/deploy.md`): while the
SDFB dataset license is unconfirmed (`license_confirmed=false`), there is **no
Ingress, no Gateway/HTTPRoute, and no Service of type LoadBalancer** — in cloud
any more than in kind. Access remains `kubectl port-forward` (or an
authenticated bastion). `scripts/check_no_ingress.sh` runs against this overlay
too, and it will fail the build if that ever changes here.

## What a real cloud overlay must decide before it is written

These are intentionally *unresolved* — resolving them is the ADR-003 checkpoint
work, not this stub's:

- **Storage classes.** The kind overlay relies on the default local-path
  provisioner. Cloud needs real `StorageClass` names for the Postgres data PVC,
  the backup PVC, and the Prometheus TSDB PVC, with snapshot policy.
- **Secrets.** kind creates dev Secrets imperatively. Cloud must source the DB
  passwords and bearer tokens from a managed secret store (External Secrets /
  cloud secret manager) — never from `secret.example.yaml`, never from git.
- **Backups off-cluster.** The `pg_dump` CronJob writes to an in-cluster PVC.
  Cloud must ship dumps to durable object storage with retention and a tested
  off-site restore, extending `scripts/restore_drill.sh`.
- **Resource sizing & HA.** Replicas, PodDisruptionBudgets, requests/limits,
  and whether Postgres stays a single StatefulSet or moves to a managed DB
  (ADR-001 revisit triggers in §6.4).
- **Access path after license confirmation.** Only once `license_confirmed=true`
  does the Ingress question even open — and it opens as its own ADR, not as an
  edit to this file.

## How this stub is kept honest

`overlays/cloud-stub` currently just references `../../base` so that
`kubectl kustomize overlays/cloud-stub` renders and passes the no-Ingress gate.
It is annotated `palimpsest.dev/status: deferred-until-adr-003-checkpoint` so no
one mistakes it for a finished target.

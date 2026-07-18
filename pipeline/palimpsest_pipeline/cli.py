"""Pipeline CLI.

    palimpsest-pipeline ingest <adapter> --data-dir DIR --run-id R [--engine URL --token T]
    palimpsest-pipeline validate DIR
    palimpsest-pipeline synth --out fixtures/synthetic [--scale N]

The pipeline's contract with operators is "silent success or loud, specific
failure": the CLI exits nonzero on any schema-invalid claim or engine reject,
printing the per-line report.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from . import __version__
from .adapters.base import Claim, EntityRecord, to_ndjson_line
from .adapters.sdfb import SDFBAdapter
from .harvest.local_dump import file_content_hash, read_delimited
from .schema import validate_claim, validate_entity
from .submit.manifest import RunManifest
from .synth import edge_counts_by_threshold, write_fixture

ADAPTERS = {"sdfb": SDFBAdapter}


# --------------------------------------------------------------------------- #
# validate
# --------------------------------------------------------------------------- #
def _validate_ndjson(path: Path, kind: str) -> tuple[int, list[str]]:
    """Validate every line of an NDJSON file. Returns (line_count, errors)."""
    if not path.is_file():
        return 0, [f"{path.name}: missing"]
    validator = validate_entity if kind == "entities" else validate_claim
    errors: list[str] = []
    count = 0
    with open(path, encoding="utf-8") as fh:
        for i, line in enumerate(fh, 1):
            line = line.strip()
            if not line:
                continue
            count += 1
            try:
                obj = json.loads(line)
            except json.JSONDecodeError as exc:
                errors.append(f"{path.name}:{i}: invalid JSON: {exc}")
                continue
            for err in validator(obj):
                errors.append(f"{path.name}:{i}: {err}")
    return count, errors


def cmd_validate(args: argparse.Namespace) -> int:
    d = Path(args.dir)
    total_errors: list[str] = []
    summary: dict[str, int] = {}
    for name, kind in (("entities.ndjson", "entities"), ("claims.ndjson", "claims")):
        count, errors = _validate_ndjson(d / name, kind)
        summary[name] = count
        total_errors.extend(errors)
    for name, count in summary.items():
        print(f"{name}: {count} line(s)")
    if total_errors:
        print(f"\nINVALID — {len(total_errors)} error(s):", file=sys.stderr)
        for e in total_errors[:100]:
            print(f"  {e}", file=sys.stderr)
        return 1
    print("OK — all lines validate against the frozen contract.")
    return 0


# --------------------------------------------------------------------------- #
# ingest
# --------------------------------------------------------------------------- #
def _self_validate(
    entities: list[EntityRecord], claims: list[Claim]
) -> tuple[int, list[str]]:
    """Adapter self-validation (Flow A step 4): a nonzero invalid count aborts."""
    errors: list[str] = []
    for i, e in enumerate(entities):
        for err in validate_entity(e.to_dict()):
            errors.append(f"entity[{i}]: {err}")
    for i, c in enumerate(claims):
        for err in validate_claim(c.to_dict()):
            errors.append(f"claim[{i}]: {err}")
    return len(errors), errors


def cmd_ingest(args: argparse.Namespace) -> int:
    adapter_cls = ADAPTERS.get(args.adapter)
    if adapter_cls is None:
        print(f"unknown adapter: {args.adapter} (known: {sorted(ADAPTERS)})", file=sys.stderr)
        return 2

    data_dir = Path(args.data_dir)
    people_path = _first_existing(data_dir, ["people.csv", "people.tsv"])
    rel_path = _first_existing(data_dir, ["relationships.csv", "relationships.tsv"])
    if people_path is None or rel_path is None:
        print(
            f"expected people.[csv|tsv] and relationships.[csv|tsv] in {data_dir}",
            file=sys.stderr,
        )
        return 2

    adapter = adapter_cls()
    people = list(read_delimited(people_path, record_kind="person"))
    relationships = list(read_delimited(rel_path, record_kind="relationship"))
    entities, claims = adapter.transform(people, relationships)

    invalid, errors = _self_validate(entities, claims)
    print(f"emitted: {len(entities)} entities, {len(claims)} claims")
    print(f"anomaly counters: {json.dumps(adapter.counters.as_dict())}")

    manifest = RunManifest(
        run_id=args.run_id,
        adapter=adapter.name,
        adapter_version=adapter.version,
        source_slug=adapter.source_slug,
        source_version=adapter.source_slug,
        input_content_hashes={
            people_path.name: file_content_hash(people_path),
            rel_path.name: file_content_hash(rel_path),
        },
        counts={"entities": len(entities), "claims": len(claims)},
        anomaly_counters=adapter.counters.as_dict(),
        validation={"invalid": invalid},
    )

    if invalid:
        print(f"\nABORT — {invalid} invalid line(s); nothing submitted:", file=sys.stderr)
        for e in errors[:100]:
            print(f"  {e}", file=sys.stderr)
        _write_run_outputs(args, manifest, entities, claims)
        return 1

    # Optionally submit to the engine.
    if args.engine:
        if not args.token:
            print("--engine requires --token", file=sys.stderr)
            return 2
        from .submit.client import ImportClient  # local import: requests only if used

        client = ImportClient(args.engine, args.token, args.run_id, adapter.source_slug)
        ent_result = client.submit_entities(entities)
        claim_result = client.submit_claims(claims)
        manifest.submission = {
            "entities": ent_result.as_dict(),
            "claims": claim_result.as_dict(),
        }
        print(f"submitted entities: {json.dumps(ent_result.as_dict())}")
        print(f"submitted claims:   {json.dumps(claim_result.as_dict())}")
        _write_run_outputs(args, manifest, entities, claims)
        if not (ent_result.ok and claim_result.ok):
            print("\nREJECTS present — exiting nonzero.", file=sys.stderr)
            return 1
        return 0

    _write_run_outputs(args, manifest, entities, claims)
    print("no --engine given; validated and wrote local NDJSON + manifest only.")
    return 0


def _write_run_outputs(
    args: argparse.Namespace,
    manifest: RunManifest,
    entities: list[EntityRecord],
    claims: list[Claim],
) -> None:
    out_dir = Path(args.out) if args.out else Path(args.data_dir) / "_runs" / args.run_id
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "entities.ndjson").write_text(
        "".join(to_ndjson_line(e) + "\n" for e in entities), encoding="utf-8"
    )
    (out_dir / "claims.ndjson").write_text(
        "".join(to_ndjson_line(c) + "\n" for c in claims), encoding="utf-8"
    )
    manifest.write(out_dir / "manifest.json")
    print(f"run outputs written to {out_dir}")


def _first_existing(d: Path, names: list[str]) -> Path | None:
    for n in names:
        if (d / n).is_file():
            return d / n
    return None


# --------------------------------------------------------------------------- #
# synth
# --------------------------------------------------------------------------- #
def cmd_synth(args: argparse.Namespace) -> int:
    summary = write_fixture(args.out, scale=args.scale)
    print(f"wrote synthetic fixture to {summary['out_dir']} (scale={summary['scale']})")
    print(f"  entities: {summary['entities']}   claims: {summary['claims']}")
    print(f"  anomaly counters: {json.dumps(summary['anomaly_counters'])}")
    print("  edge count by confidence threshold:")
    print("    threshold  edges")
    for t, n in summary["edge_counts"]:
        print(f"    {t:>7.2f}  {n:>5d}")
    return 0


# --------------------------------------------------------------------------- #
# entry point
# --------------------------------------------------------------------------- #
def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="palimpsest-pipeline", description=__doc__)
    p.add_argument("--version", action="version", version=f"%(prog)s {__version__}")
    sub = p.add_subparsers(dest="command", required=True)

    ing = sub.add_parser("ingest", help="harvest -> adapt -> validate -> (submit)")
    ing.add_argument("adapter", choices=sorted(ADAPTERS))
    ing.add_argument("--data-dir", required=True)
    ing.add_argument("--run-id", required=True)
    ing.add_argument("--engine", default=None, help="engine base URL")
    ing.add_argument("--token", default=None, help="scholar bearer token")
    ing.add_argument("--out", default=None, help="run-output dir (default DATA_DIR/_runs/RUN_ID)")
    ing.set_defaults(func=cmd_ingest)

    val = sub.add_parser("validate", help="validate an entities/claims NDJSON dir")
    val.add_argument("dir")
    val.set_defaults(func=cmd_validate)

    syn = sub.add_parser("synth", help="generate the synthetic fixture")
    syn.add_argument("--out", required=True, help="output dir (e.g. fixtures/synthetic)")
    syn.add_argument("--scale", type=int, default=1, help="scale factor (>=1)")
    syn.set_defaults(func=cmd_synth)

    return p


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())

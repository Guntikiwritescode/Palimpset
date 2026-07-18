"""The adapter interface and the interchange dataclasses.

The interface is adopted from Part 2 §5.3 and used now (ARCHITECTURE §3.1): it is
deliberately the same interface the Roman expansion reuses later. Every adapter
implements:

  - ``map_to_claims(source_record) -> list[Claim]``
  - ``register_types() -> list[RelationType]``
  - ``register_authorities() -> list[Authority]``
  - ``normalize_names(...)``
  - ``map_uncertainty(...)``  (native doubt -> fuzzy dates / confidence objects)

Adapters are **pure with respect to I/O**: harvesters fetch, adapters transform,
the submitter ships. They may hold in-memory registry state (e.g. the set of known
entity ids, needed to detect dangling relationship endpoints) but they never touch
a file, a socket, or a database.

The dataclasses below are lightweight mirrors of ``contracts/claim.schema.json``
v0.1.0. They are *shapes*, not validators — validation is the ``schema`` module's
job, and every emitted claim self-validates before it is submitted (ARCHITECTURE
Flow A step 4).
"""

from __future__ import annotations

import json
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any

SCHEMA_VERSION = "0.1.0"


# --------------------------------------------------------------------------- #
# Interchange value objects
# --------------------------------------------------------------------------- #
def entity_ref(authority: str, id_: Any) -> dict[str, str]:
    """An authority-qualified external id (``#/$defs/entityRef``)."""
    return {"authority": str(authority), "id": str(id_)}


def literal(kind: str, value: Any, authority: str | None = None) -> dict[str, Any]:
    """A claim-object literal (``#/$defs/literal``)."""
    obj: dict[str, Any] = {"kind": kind, "value": value}
    if authority is not None:
        obj["authority"] = authority
    return obj


def unscored() -> dict[str, str]:
    """Unscored confidence — absence, never a number (I5, ``#/$defs/confidence``)."""
    return {"kind": "unscored"}


def source_native_scalar(raw: int, scale: str) -> dict[str, Any]:
    """A source-native confidence scalar.

    ``point`` is ``raw / 100`` (SDFB ``max_certainty`` is a 0-100 integer);
    ``calibrated`` is always ``False`` — import-time confidence is never calibrated
    (§6.2). The source's own value is preserved verbatim under ``raw``.
    """
    return {
        "kind": "source_native_scalar",
        "scale": scale,
        "raw": raw,
        "point": raw / 100,
        "calibrated": False,
    }


@dataclass
class Claim:
    """An interchange claim (root of ``claim.schema.json``)."""

    subject: dict[str, str]
    predicate: str
    obj: dict[str, Any]  # serialized as "object" (Python keyword avoidance)
    confidence: dict[str, Any]
    method: str
    asserted_by: str
    support: list[dict[str, Any]]
    valid_time: dict[str, Any] | None = None
    method_detail: dict[str, Any] | None = None
    schema_version: str = SCHEMA_VERSION

    def to_dict(self) -> dict[str, Any]:
        out: dict[str, Any] = {
            "schema_version": self.schema_version,
            "subject": self.subject,
            "predicate": self.predicate,
            "object": self.obj,
        }
        if self.valid_time is not None:
            out["valid_time"] = self.valid_time
        out["confidence"] = self.confidence
        out["method"] = self.method
        if self.method_detail is not None:
            out["method_detail"] = self.method_detail
        out["asserted_by"] = self.asserted_by
        out["support"] = self.support
        return out


@dataclass
class EntityRecord:
    """An import ``kind=entities`` line (``#/$defs/entityRecord``)."""

    ref: dict[str, str]
    entity_type: str
    external_ids: list[dict[str, str]] = field(default_factory=list)
    schema_version: str = SCHEMA_VERSION

    def to_dict(self) -> dict[str, Any]:
        return {
            "schema_version": self.schema_version,
            "ref": self.ref,
            "entity_type": self.entity_type,
            "external_ids": self.external_ids,
        }


@dataclass(frozen=True)
class RelationType:
    """A relation type an adapter registers (``register_types``)."""

    slug: str
    display_name: str
    range: str  # "entity" | "literal"
    symmetric: bool = False


@dataclass(frozen=True)
class Authority:
    """A naming authority an adapter registers (``register_authorities``)."""

    slug: str
    display_name: str
    identity_bearing: bool  # True = 1:1 anchor; False = has-external-id claim only


# --------------------------------------------------------------------------- #
# Canonical serialization (byte-stable output for fixtures + golden tests)
# --------------------------------------------------------------------------- #
def to_ndjson_line(obj: Claim | EntityRecord | dict[str, Any]) -> str:
    """Serialize one interchange object to a canonical, byte-stable NDJSON line.

    ``sort_keys=True`` and tight separators make the output deterministic, so the
    synthetic fixture and the golden tests are reproducible byte-for-byte.
    """
    if isinstance(obj, (Claim, EntityRecord)):
        payload = obj.to_dict()
    else:
        payload = obj
    return json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


# --------------------------------------------------------------------------- #
# The adapter interface
# --------------------------------------------------------------------------- #
class Adapter(ABC):
    """Pure source -> interchange transform (ARCHITECTURE §3.1).

    Concrete adapters additionally expose ``map_to_entities`` — entities are
    created *only* by explicit entity records (I6), so an adapter that mints
    entities (SDFB does, from person rows) overrides it. Adapters that mint no
    entities (a pure relationship source) keep the default empty implementation.
    """

    name: str = "adapter"
    version: str = "0.0.0"

    @abstractmethod
    def map_to_claims(self, source_record: dict[str, Any]) -> list[Claim]:
        """Transform one source record into zero or more interchange claims."""

    def map_to_entities(self, source_record: dict[str, Any]) -> list[EntityRecord]:
        """Transform one source record into zero or more entity records.

        Default: no entities. Overridden by adapters whose source carries
        identity-bearing rows (e.g. SDFB person rows).
        """
        return []

    @abstractmethod
    def register_types(self) -> list[RelationType]:
        """The relation types this adapter emits."""

    @abstractmethod
    def register_authorities(self) -> list[Authority]:
        """The naming authorities this adapter references."""

    @abstractmethod
    def normalize_names(self, raw_name: str) -> str:
        """Normalize a raw display / alias name."""

    @abstractmethod
    def map_uncertainty(self, native: dict[str, Any]) -> Any:
        """Map a native uncertainty encoding to a fuzzy interval / confidence object."""

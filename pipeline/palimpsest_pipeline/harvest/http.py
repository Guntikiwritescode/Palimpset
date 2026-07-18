"""HTTP harvester: requests-based fetch with ETag caching + exponential backoff.

Used later by the correspSearch adapter (WP2b). This is a working fetcher with a
conditional-request cache and bounded retries; the correspSearch-specific paging
lives in that adapter's harvester when it is built.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Any, Callable

import requests

RETRYABLE_STATUS = {429, 500, 502, 503, 504}


@dataclass
class FetchResult:
    status: int
    url: str
    etag: str | None
    not_modified: bool
    content: bytes | None
    headers: dict[str, str] = field(default_factory=dict)


def fetch(
    url: str,
    *,
    etag: str | None = None,
    max_retries: int = 5,
    base_delay: float = 0.5,
    timeout: float = 30.0,
    session: requests.Session | None = None,
    sleep: Callable[[float], None] = time.sleep,
    **request_kwargs: Any,
) -> FetchResult:
    """GET ``url`` with an ``If-None-Match`` conditional and exponential backoff.

    Returns a :class:`FetchResult`. A 304 sets ``not_modified=True`` and carries no
    body (the caller reuses its cache). Retries only the retryable statuses and
    connection errors, backing off ``base_delay * 2**attempt``.
    """
    sess = session or requests.Session()
    headers = {"If-None-Match": etag} if etag else {}
    last_exc: Exception | None = None

    for attempt in range(max_retries + 1):
        try:
            resp = sess.get(url, headers=headers, timeout=timeout, **request_kwargs)
        except requests.RequestException as exc:
            last_exc = exc
            if attempt == max_retries:
                raise
            sleep(base_delay * (2 ** attempt))
            continue

        if resp.status_code in RETRYABLE_STATUS and attempt < max_retries:
            sleep(base_delay * (2 ** attempt))
            continue

        if resp.status_code == 304:
            return FetchResult(
                status=304, url=url, etag=etag, not_modified=True,
                content=None, headers=dict(resp.headers),
            )
        return FetchResult(
            status=resp.status_code, url=url,
            etag=resp.headers.get("ETag"), not_modified=False,
            content=resp.content, headers=dict(resp.headers),
        )

    # Exhausted retries on retryable statuses.
    if last_exc is not None:
        raise last_exc
    raise RuntimeError(f"fetch failed after {max_retries} retries: {url}")

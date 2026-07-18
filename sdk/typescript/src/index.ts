// PALIMPSEST TypeScript SDK — the explorer's only path to the engine API.
// Typed, fetch-based, tree-shakeable (openapi-fetch over the generated types).
// Do NOT hand-edit schema.d.ts; it is generated in CI and drift-gated.

import createClient, { type Client } from "openapi-fetch";
import type { paths, components } from "./schema";

export type Schemas = components["schemas"];

/** Create a typed PALIMPSEST client. Pass the engine base URL and an optional bearer token. */
export function createPalimpsestClient(baseUrl: string, token?: string): Client<paths> {
  return createClient<paths>({
    baseUrl,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

export type { paths, components };

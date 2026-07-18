/// <reference types="vitest" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Vite + Vitest share one config. The explorer is mock-backed for now (BUILD-CONTRACT
// §7 fixtures are synthetic); the API surface in src/api swaps mechanically to the
// generated SDK in sdk/typescript later.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
  preview: { port: 4173 },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    exclude: ["e2e/**", "node_modules/**"],
    css: false,
  },
});

import { defineConfig, devices } from "@playwright/test";

// E2E runs against the BUILT app served by `vite preview`, with the mock API
// (MSW worker) enabled. Chromium is preinstalled at /opt/pw-browsers; we never
// run `playwright install` here.
process.env.PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = "1";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [["list"]],
  use: {
    baseURL: "http://127.0.0.1:4173",
    trace: "off",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    // Preview the static app; the mock worker is bundled from public/. CI builds
    // the app in a prior step, so preview is enough — but build here too so the
    // suite is runnable standalone. Bind 127.0.0.1 (matching baseURL) so the
    // runner's localhost→::1 (IPv6) resolution can't strand the poller (the CI
    // failure this replaces).
    command: "pnpm run build && pnpm run preview",
    url: "http://127.0.0.1:4173",
    reuseExistingServer: !process.env.CI,
    timeout: 240_000,
    stdout: "pipe",
    stderr: "pipe",
  },
});

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
    // Build then preview the static app; the mock worker is bundled from public/.
    command: "pnpm run build && pnpm run preview",
    url: "http://127.0.0.1:4173",
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
});

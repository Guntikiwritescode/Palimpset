import { expect, test } from "@playwright/test";

// Q-6: the CI E2E asserts the INTERACTION against synthetic data — the visible tie
// count STRICTLY DECREASES as the slider is dragged from low to high — not any
// specific measured numbers. Exact counts are defined by the fixture itself.

async function possiblyCount(page: import("@playwright/test").Page): Promise<number> {
  const text = await page.getByTestId("possibly-count").textContent();
  return Number((text ?? "").trim());
}

// The first-run tour is a modal overlay; mark it dismissed so it never blocks the
// interaction under test (it is exercised separately by its own component behaviour).
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem("palimpsest.tour.dismissed.v1", "1");
  });
});

test("search → entity → drag the slider: the tie count strictly decreases", async ({ page }) => {
  await page.goto("/");

  // Search and land on Francis Bacon the philosopher (disambiguated by life dates, J2).
  await page.getByTestId("search-input").fill("Francis Bacon");
  const philosopher = page.getByTestId("search-result").filter({ hasText: "1561" }).first();
  await expect(philosopher).toBeVisible();
  await philosopher.click();

  // Entity page: wait for the network to load and the result line to show a count.
  const slider = page.getByTestId("confidence-slider");
  await expect(slider).toBeVisible();
  await expect(page.getByTestId("possibly-count")).toBeVisible();
  await expect.poll(() => possiblyCount(page)).toBeGreaterThan(0);

  // Sample the count as we drag the slider up through the semantic bands.
  const samples: number[] = [];
  samples.push(await possiblyCount(page)); // c = 0

  await slider.focus();
  for (let i = 0; i < 8; i++) await slider.press("ArrowUp"); // → 0.40
  samples.push(await possiblyCount(page));

  for (let i = 0; i < 6; i++) await slider.press("ArrowUp"); // → 0.70
  samples.push(await possiblyCount(page));

  for (let i = 0; i < 4; i++) await slider.press("ArrowUp"); // → 0.90
  samples.push(await possiblyCount(page));

  // Strictly decreasing across the drag (the interaction, not the numbers).
  for (let i = 1; i < samples.length; i++) {
    expect(samples[i], `sample ${i} (${samples.join(" → ")}) must be < previous`).toBeLessThan(
      samples[i - 1],
    );
  }

  // End jumps to the maximum threshold; the count must not increase.
  await slider.press("End");
  const atMax = await possiblyCount(page);
  expect(atMax).toBeLessThanOrEqual(samples[samples.length - 1]);
});

test("the certainly toggle highlights without emptying the graph (D2)", async ({ page }) => {
  await page.goto("/entity/1001?y0=1600&y1=1600");
  await expect(page.getByTestId("possibly-count")).toBeVisible();
  const before = await possiblyCount(page);
  expect(before).toBeGreaterThan(0);

  await page.getByTestId("mode-certainly").click();
  // The possibly count is unchanged — certainly is a highlight, not a filter.
  await expect.poll(() => possiblyCount(page)).toBe(before);
  await expect(page.getByTestId("certainly-count")).toHaveText("2");
});

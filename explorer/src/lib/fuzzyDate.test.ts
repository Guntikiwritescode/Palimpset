import { describe, expect, it } from "vitest";
import { formatFuzzyDate, formatLifeDates, formatValidTime } from "./fuzzyDate";
import type { FuzzyInterval, LifeDates } from "../api/types";

describe("fuzzy-date grammar (§13.3, D6)", () => {
  it("renders both bounds as a precise year", () => {
    expect(formatFuzzyDate({ earliest: "1561-01-01", latest: "1561-12-31" })).toBe("1561");
  });

  it("renders circa with an approximate marker and NO ±window (D6)", () => {
    const s = formatFuzzyDate({ earliest: "1561-01-01", latest: "1561-12-31", approximate: true });
    expect(s).toBe("c. 1561");
    expect(s).not.toMatch(/±/);
    expect(s).not.toMatch(/\d\)/); // no "(±5)" style window
  });

  it("renders earliest-only as 'after'", () => {
    expect(formatFuzzyDate({ earliest: "1561-01-01", latest: null })).toBe("after 1561");
  });

  it("renders latest-only as 'before'", () => {
    expect(formatFuzzyDate({ earliest: null, latest: "1626-12-31" })).toBe("before 1626");
  });

  it("renders unknown as 'date unknown', never a blank or invented range", () => {
    expect(formatFuzzyDate({ earliest: null, latest: null })).toBe("date unknown");
  });

  it("renders a genuine multi-year window as a range", () => {
    expect(formatFuzzyDate({ earliest: "1561-01-01", latest: "1563-12-31" })).toBe("1561–1563");
  });

  it("formats life dates as a range", () => {
    const ld: LifeDates = {
      bornEarliest: "1561-01-01",
      bornLatest: "1561-12-31",
      diedEarliest: "1626-01-01",
      diedLatest: "1626-12-31",
    };
    expect(formatLifeDates(ld)).toBe("1561–1626");
  });

  it("formats a valid-time interval as a range", () => {
    const fi: FuzzyInterval = {
      startEarliest: "1580-01-01",
      startLatest: null,
      endEarliest: null,
      endLatest: "1601-12-31",
      approximate: false,
      original: {},
    };
    expect(formatValidTime(fi)).toBe("after 1580–before 1601");
  });
});

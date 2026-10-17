import { describe, it, expect } from "vitest";
import {
  daysBetween,
  formatCurrency,
  formatShortDate,
  formatTime,
  formatWeekday,
  formatWeightKg,
  todayIn,
} from "./format";

// Newer ICU versions put a narrow no-break space before AM/PM; compare with plain spaces
const plain = (text: string) => text.replace(/\s/g, " ");

describe("formatCurrency", () => {
  it.each(["en", "es-MX"])("shows pesos with a plain $ sign in %s", (locale) => {
    expect(formatCurrency(1250, locale)).toBe("$1,250.00");
  });
});

describe("formatWeightKg", () => {
  it.each(["en", "es-MX"])("uses the locale's unit format in %s", (locale) => {
    expect(formatWeightKg(1.2, locale)).toBe("1.2 kg");
  });
});

describe("formatTime", () => {
  it("formats a local time per locale", () => {
    expect(plain(formatTime("21:00:00", "en"))).toBe("9:00 PM");
    expect(plain(formatTime("21:00:00", "es-MX"))).toBe("9:00 p.m.");
  });

  it("takes the time part of a local date-time", () => {
    expect(plain(formatTime("2026-10-17T12:00:00", "en"))).toBe("12:00 PM");
  });
});

describe("formatShortDate", () => {
  it("puts the weekday first in every locale", () => {
    expect(formatShortDate("2026-10-17", "en")).toBe("Sat 17");
    expect(formatShortDate("2026-10-17", "es-MX")).toBe("sáb 17");
  });
});

describe("formatWeekday", () => {
  it("names a day of the week per locale", () => {
    expect(formatWeekday("MONDAY", "en")).toBe("Monday");
    expect(formatWeekday("SATURDAY", "es-MX")).toBe("sábado");
  });
});

describe("todayIn", () => {
  it("uses the business's time zone, not the browser's", () => {
    // 03:00 UTC on the 18th is still 21:00 on the 17th in Mexico City
    expect(todayIn("America/Mexico_City", new Date("2026-10-18T03:00:00Z"))).toBe("2026-10-17");
  });
});

describe("daysBetween", () => {
  it("counts whole days between local dates and date-times", () => {
    expect(daysBetween("2026-10-17", "2026-10-17T12:00:00")).toBe(0);
    expect(daysBetween("2026-10-17", "2026-10-18")).toBe(1);
    expect(daysBetween("2026-10-31", "2026-11-03")).toBe(3);
  });
});

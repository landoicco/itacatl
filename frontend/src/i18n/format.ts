// Prices are always in the marketplace's currency, whatever language the UI is in
const CURRENCY = "MXN";

// narrowSymbol prints "$" instead of "MX$" in English, which is how prices read in Mexico
export function formatCurrency(amount: number, locale: string): string {
  return currencyFormat(locale).format(amount);
}

/** The symbol alone, e.g. next to a price input: "$" */
export function currencySymbol(locale: string): string {
  return currencyFormat(locale)
    .formatToParts(0)
    .find((part) => part.type === "currency")!.value;
}

function currencyFormat(locale: string) {
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency: CURRENCY,
    currencyDisplay: "narrowSymbol",
  });
}

/**
 * Text as searches compare it: without case or accents, so "maracuya" finds "Maracuyá" and
 * "VÁNILLA" finds "Vanilla". Mexican users often type without accents on a phone
 */
export function normalizeForSearch(text: string): string {
  return text
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase()
    .trim();
}

/*
 * Dates and times from the API are local to the business ("2026-10-17", "21:00:00",
 * "2026-10-17T21:00:00"), without an offset. They are formatted as wall-clock values in UTC, so
 * the browser's own time zone never shifts them: a shop that opens at noon shows 12:00 PM
 * wherever the customer is.
 */

const DAY_ORDER = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const;

function toUtc(date: string): number {
  const [year, month, day] = date.split("-").map(Number);
  return Date.UTC(year, month - 1, day);
}

/** "21:00:00" or "2026-10-17T21:00:00" → "9:00 PM" (en) / "9:00 p.m." (es-MX) */
export function formatTime(value: string, locale: string): string {
  const time = value.includes("T") ? value.split("T")[1] : value;
  const [hours, minutes] = time.split(":").map(Number);
  return new Intl.DateTimeFormat(locale, {
    hour: "numeric",
    minute: "2-digit",
    timeZone: "UTC",
  }).format(Date.UTC(2000, 0, 1, hours, minutes));
}

/** "2026-10-17" → "Sat 17" (en) / "sáb 17" (es-MX) */
export function formatShortDate(date: string, locale: string): string {
  const parts = new Intl.DateTimeFormat(locale, {
    weekday: "short",
    day: "numeric",
    timeZone: "UTC",
  }).formatToParts(toUtc(date));
  // Some locales put the day first ("17 Sat"); weekday first reads better on a tab
  const weekday = parts.find((p) => p.type === "weekday")?.value;
  const day = parts.find((p) => p.type === "day")?.value;
  return `${weekday} ${day}`;
}

/** "SATURDAY" → "Saturday" (en) / "sábado" (es-MX) */
export function formatWeekday(day: (typeof DAY_ORDER)[number], locale: string): string {
  // 1 January 2024 was a Monday
  const date = Date.UTC(2024, 0, 1 + DAY_ORDER.indexOf(day));
  return new Intl.DateTimeFormat(locale, { weekday: "long", timeZone: "UTC" }).format(date);
}

/** Today's date ("2026-10-17") in a business's time zone */
export function todayIn(timeZone: string, now: Date = new Date()): string {
  // en-CA formats dates as YYYY-MM-DD
  return new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
}

/** Whole days from one local date to another: 0 = same day, 1 = the next day */
export function daysBetween(from: string, to: string): number {
  return Math.round((toUtc(to.split("T")[0]) - toUtc(from.split("T")[0])) / 86_400_000);
}

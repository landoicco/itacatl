import { useTranslation } from "react-i18next";
import type { ManagedMenu } from "@/api/types";
import { daysBetween, formatShortDate, todayIn } from "@/i18n/format";

/*
 * Dates of the menus screens. Like the API's, they are local to the business: "2026-10-17" and
 * "21:00:00", without an offset, compared as text or computed in UTC so no time zone shifts them.
 */

/** "2026-10-17" plus n days */
export function addDays(date: string, days: number): string {
  const [year, month, day] = date.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

/** The later of two dates; ISO dates compare as text */
export const laterOf = (a: string, b: string) => (a > b ? a : b);

/** The API sends "12:00:00"; <input type="time"> shows and returns "12:00" */
export const hhmm = (time: string) => time.slice(0, 5);

/** Whether the menu's window has closed in the business's time zone, as the backend decides */
export function hasEnded(menu: ManagedMenu, timeZone: string, now: Date = new Date()): boolean {
  // sv-SE formats as "2026-10-17 11:30", which sorts like the API's "2026-10-17" and "21:00"
  const local = new Intl.DateTimeFormat("sv-SE", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(now);
  return `${menu.serviceDate} ${hhmm(menu.endsAt)}` <= local;
}

/**
 * Labels a menu's day relative to the business's today: "Today · Sat 17", "Wed 21". Takes the
 * zone while it may still be loading, so it can be called before a page's early returns
 */
export function useDayLabel(timeZone: string | undefined) {
  const { t, i18n } = useTranslation();
  const today = timeZone ? todayIn(timeZone) : "";
  return (date: string) => {
    const short = formatShortDate(date, i18n.language);
    const days = daysBetween(today, date);
    if (days === 0) return `${t("shop.today")} · ${short}`;
    if (days === 1) return `${t("shop.tomorrow")} · ${short}`;
    return short;
  };
}

import { useTranslation } from "react-i18next";
import type { OpeningStatus } from "@/api/types";
import { daysBetween, formatShortDate, formatTime, todayIn } from "@/i18n/format";

interface OpeningStatusBadgeProps {
  status: OpeningStatus;
  /** The business's zone: "today" and "tomorrow" are the business's, not the browser's */
  timeZone: string;
  /** Injectable for tests */
  now?: Date;
}

// The hour on a 12-hour clock. Passed as i18next's count, it picks Spanish "a la 1" vs "a las 2"
function hour12(dateTime: string): number {
  const hours = Number(dateTime.split("T")[1].split(":")[0]);
  return hours % 12 || 12;
}

function useOpeningStatusText(status: OpeningStatus, timeZone: string, now = new Date()) {
  const { t, i18n } = useTranslation();
  const locale = i18n.language;

  if (status.state === "OPEN" && status.closesAt) {
    return t("status.openUntil", {
      time: formatTime(status.closesAt, locale),
      count: hour12(status.closesAt),
    });
  }
  if (!status.opensAt) return t("status.closed");

  const time = formatTime(status.opensAt, locale);
  const count = hour12(status.opensAt);
  const days = daysBetween(todayIn(timeZone, now), status.opensAt);
  if (days <= 0) return t("status.opensToday", { time, count });
  if (days === 1) return t("status.opensTomorrow", { time, count });
  return t("status.opensOn", {
    day: formatShortDate(status.opensAt.split("T")[0], locale),
    time,
    count,
  });
}

export default function OpeningStatusBadge({ status, timeZone, now }: OpeningStatusBadgeProps) {
  const text = useOpeningStatusText(status, timeZone, now);
  const open = status.state === "OPEN";

  return (
    <span
      className={`inline-block rounded-full px-3 py-1 text-xs font-bold whitespace-nowrap ${
        open ? "bg-open text-on-open" : "bg-soon text-on-soon"
      }`}
    >
      {text}
    </span>
  );
}

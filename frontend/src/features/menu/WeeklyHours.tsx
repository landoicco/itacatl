import { useTranslation } from "react-i18next";
import type { BusinessHours, DayOfWeek } from "@/api/types";
import { formatTime, formatWeekday, todayIn } from "@/i18n/format";

const WEEK: DayOfWeek[] = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

// The business's weekday today, from its own date (getUTCDay: 0 = Sunday)
function todayWeekday(timeZone: string): DayOfWeek {
  const day = new Date(`${todayIn(timeZone)}T00:00:00Z`).getUTCDay();
  return WEEK[(day + 6) % 7];
}

interface WeeklyHoursProps {
  hours: BusinessHours[];
  timeZone: string;
}

export default function WeeklyHours({ hours, timeZone }: WeeklyHoursProps) {
  const { t, i18n } = useTranslation();
  const locale = i18n.language;
  const today = todayWeekday(timeZone);

  return (
    <table className="w-full border-collapse text-sm">
      <tbody>
        {WEEK.map((day) => {
          const windows = hours.filter((h) => h.dayOfWeek === day);
          const isToday = day === today;
          return (
            <tr key={day} className={isToday ? "font-bold text-accent" : undefined}>
              <th scope="row" className="py-1.5 text-left font-semibold capitalize">
                {formatWeekday(day, locale)}
              </th>
              <td className={`py-1.5 text-right ${windows.length ? "" : "text-muted"}`}>
                {windows.length
                  ? windows
                      .map(
                        (w) =>
                          `${formatTime(w.opensAt, locale)} – ${formatTime(w.closesAt, locale)}`
                      )
                      .join(", ")
                  : t("shop.closedDay")}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

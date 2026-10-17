import { useTranslation } from "react-i18next";
import { Link, useParams, useSearchParams } from "react-router";
import { useBusiness, useMenu } from "@/api/queries";
import type { MenuSummary } from "@/api/types";
import ErrorMessage from "@/components/ErrorMessage";
import OpeningStatusBadge from "@/components/OpeningStatusBadge";
import SiteHeader from "@/components/SiteHeader";
import SkeletonCards from "@/components/SkeletonCards";
import { daysBetween, formatShortDate, formatTime, todayIn } from "@/i18n/format";
import MenuItemCard from "./MenuItemCard";
import WeeklyHours from "./WeeklyHours";

interface ServiceDayTabsProps {
  menus: MenuSummary[];
  selectedId: number;
  timeZone: string;
  onSelect: (id: number) => void;
}

function ServiceDayTabs({ menus, selectedId, timeZone, onSelect }: ServiceDayTabsProps) {
  const { t, i18n } = useTranslation();
  const locale = i18n.language;
  const today = todayIn(timeZone);

  const label = (menu: MenuSummary) => {
    const days = daysBetween(today, menu.serviceDate);
    const date = formatShortDate(menu.serviceDate, locale);
    const day = days === 0 ? t("shop.today") : days === 1 ? t("shop.tomorrow") : null;
    // Two services on one day (split shifts) are told apart by their start time
    const sameDay = menus.filter((m) => m.serviceDate === menu.serviceDate).length > 1;
    const time = sameDay ? ` · ${formatTime(menu.startsAt, locale)}` : "";
    return `${day ? `${day} · ${date}` : date}${time}`;
  };

  return (
    <div role="tablist" aria-label={t("shop.serviceDays")} className="flex flex-wrap gap-2.5">
      {menus.map((menu) => (
        <button
          key={menu.id}
          role="tab"
          aria-selected={menu.id === selectedId}
          onClick={() => onSelect(menu.id)}
          className="min-h-12 cursor-pointer rounded-control border-theme bg-surface px-5 font-bold aria-selected:bg-accent aria-selected:text-on-accent aria-selected:shadow-pressed"
        >
          {label(menu)}
        </button>
      ))}
    </div>
  );
}

export default function ShopPage() {
  const { t, i18n } = useTranslation();
  const { slug = "" } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const business = useBusiness(slug);

  const menus = business.data?.upcomingMenus ?? [];
  // The menu in the URL if it is still upcoming, otherwise the next one
  const requested = Number(searchParams.get("menu"));
  const selectedId = menus.some((m) => m.id === requested) ? requested : menus[0]?.id;
  const selected = menus.find((m) => m.id === selectedId);
  const menu = useMenu(selectedId);

  return (
    <>
      <SiteHeader />
      <main className="mx-auto max-w-[1100px] px-5 pb-16 sm:px-6">
        <Link
          to="/"
          className="mt-2 inline-flex min-h-11 items-center gap-1.5 font-bold text-accent"
        >
          <svg
            aria-hidden="true"
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M15 18l-6-6 6-6" />
          </svg>
          {t("shop.back")}
        </Link>

        {business.isPending ? (
          <SkeletonCards count={2} height="h-64" />
        ) : business.error ? (
          <ErrorMessage error={business.error} onRetry={() => business.refetch()} />
        ) : (
          <div className="grid grid-cols-1 gap-8 pt-2 md:grid-cols-[340px_minmax(0,1fr)] md:items-start">
            <aside className="flex flex-col gap-4 rounded-card border-theme bg-surface p-6 shadow-card md:sticky md:top-6">
              <h1 className="text-display text-[34px] leading-[1.05]">{business.data.name}</h1>
              <div>
                <OpeningStatusBadge
                  status={business.data.openingStatus}
                  timeZone={business.data.timeZone}
                />
              </div>
              {business.data.description && (
                <p className="text-[15px] leading-relaxed text-muted">
                  {business.data.description}
                </p>
              )}
              <p className="text-sm font-bold">
                {[
                  business.data.acceptsPickup && t("shop.pickup"),
                  business.data.acceptsDineIn && t("shop.dineIn"),
                ]
                  .filter(Boolean)
                  .join(" · ")}
              </p>
              <section className="flex flex-col gap-1.5">
                <h2 className="text-display text-lg">{t("shop.hours")}</h2>
                <WeeklyHours hours={business.data.weeklyHours} timeZone={business.data.timeZone} />
              </section>
            </aside>

            <section aria-labelledby="menu-title" className="flex flex-col gap-5">
              {selected && selectedId !== undefined ? (
                <>
                  <div className="flex flex-wrap items-end justify-between gap-4">
                    <ServiceDayTabs
                      menus={menus}
                      selectedId={selectedId}
                      timeZone={business.data.timeZone}
                      onSelect={(id) => setSearchParams({ menu: String(id) }, { replace: true })}
                    />
                    <span className="text-sm text-muted">
                      {t("shop.served", {
                        from: formatTime(selected.startsAt, i18n.language),
                        to: formatTime(selected.endsAt, i18n.language),
                      })}
                    </span>
                  </div>
                  <h2 id="menu-title" className="text-display text-2xl">
                    {t("shop.flavors")}
                  </h2>
                  {menu.isPending ? (
                    <SkeletonCards count={4} height="h-28" />
                  ) : menu.error ? (
                    <ErrorMessage error={menu.error} onRetry={() => menu.refetch()} />
                  ) : (
                    <div className="grid grid-cols-[repeat(auto-fill,minmax(260px,1fr))] gap-3.5">
                      {menu.data.items.map((item) => (
                        <MenuItemCard key={item.id} item={item} />
                      ))}
                    </div>
                  )}
                </>
              ) : (
                <p id="menu-title" className="py-16 text-center text-muted">
                  {t("shop.noMenus")}
                </p>
              )}
            </section>
          </div>
        )}
      </main>
    </>
  );
}

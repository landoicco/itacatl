import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useBusinesses } from "@/api/queries";
import type { BusinessSummary } from "@/api/types";
import ErrorMessage from "@/components/ErrorMessage";
import SiteHeader from "@/components/SiteHeader";
import SkeletonCards from "@/components/SkeletonCards";
import { normalizeForSearch } from "@/i18n/format";
import ShopCard from "./ShopCard";

function matches(shop: BusinessSummary, query: string): boolean {
  const needle = normalizeForSearch(query);
  if (!needle) return true;
  return [shop.name, shop.description ?? "", ...shop.featuredItems].some((text) =>
    normalizeForSearch(text).includes(needle)
  );
}

export default function ShopListPage() {
  const { t } = useTranslation();
  const [query, setQuery] = useState("");
  const [openNow, setOpenNow] = useState(false);
  const { data, isPending, error, refetch } = useBusinesses();

  const shops = (data ?? []).filter(
    (shop) => matches(shop, query) && (!openNow || shop.openingStatus.state === "OPEN")
  );

  return (
    <>
      <SiteHeader>
        <label className="flex min-h-12 items-center gap-2 rounded-control border-theme bg-surface px-4 shadow-card">
          <svg
            aria-hidden="true"
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.2"
            strokeLinecap="round"
          >
            <circle cx="11" cy="11" r="7" />
            <path d="M20 20l-3.5-3.5" />
          </svg>
          <span className="sr-only">{t("home.searchLabel")}</span>
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t("home.searchLabel")}
            className="flex-1 bg-transparent text-[15px] outline-none placeholder:text-muted"
          />
        </label>
      </SiteHeader>

      <main className="mx-auto max-w-[1100px] px-5 pb-16 sm:px-6">
        <div className="flex flex-wrap items-end justify-between gap-6 pt-6 pb-2">
          <div className="flex flex-col gap-2">
            <h1 className="text-display text-[clamp(30px,4vw,44px)] leading-[1.05]">
              {t("home.title")}
            </h1>
            <p className="text-base text-muted">{t("home.subtitle")}</p>
          </div>
          <div role="group" aria-label={t("home.filters")} className="flex flex-wrap gap-2">
            {[
              { label: t("home.filterAll"), active: !openNow, value: false },
              { label: t("home.filterOpenNow"), active: openNow, value: true },
            ].map(({ label, active, value }) => (
              <button
                key={label}
                aria-pressed={active}
                onClick={() => setOpenNow(value)}
                className="min-h-10 cursor-pointer rounded-full border-theme bg-surface px-[18px] text-sm font-bold aria-pressed:bg-accent aria-pressed:text-on-accent aria-pressed:shadow-pressed"
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="pt-6">
          {isPending ? (
            <SkeletonCards />
          ) : error ? (
            <ErrorMessage error={error} onRetry={() => refetch()} />
          ) : shops.length === 0 ? (
            <p className="py-16 text-center text-muted">{t("home.empty")}</p>
          ) : (
            <div className="grid grid-cols-[repeat(auto-fill,minmax(280px,1fr))] gap-5">
              {shops.map((shop) => (
                <ShopCard key={shop.slug} shop={shop} />
              ))}
            </div>
          )}
        </div>
      </main>
    </>
  );
}

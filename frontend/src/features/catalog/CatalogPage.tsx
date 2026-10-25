import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router";
import { useCatalog } from "@/api/manage";
import type { CatalogItem } from "@/api/types";
import ErrorMessage from "@/components/ErrorMessage";
import SkeletonCards from "@/components/SkeletonCards";
import { formatCurrency } from "@/i18n/format";
import { flavorColor } from "@/theme/flavorColor";

// Case- and accent-insensitive, so "maracuya" finds "Maracuyá"
function normalize(text: string): string {
  return text
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase();
}

/** What the business knows how to make. Archived items are folded at the end, never deleted */
export default function CatalogPage() {
  const { t } = useTranslation();
  const { slug = "" } = useParams();
  const [query, setQuery] = useState("");
  const { data, isPending, error, refetch } = useCatalog(slug);

  if (isPending) return <SkeletonCards count={4} height="h-16" />;
  if (error) return <ErrorMessage error={error} onRetry={() => void refetch()} />;

  const needle = normalize(query.trim());
  const shown = data.filter((item) => normalize(item.name).includes(needle));
  const active = shown.filter((item) => !item.archived);
  const archived = shown.filter((item) => item.archived);

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-display text-[28px]">{t("catalog.title")}</h1>
      <label>
        <span className="sr-only">{t("catalog.search")}</span>
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={t("catalog.search")}
          className="min-h-12 w-full rounded-control border-theme bg-surface px-4 text-[15px] shadow-card outline-none placeholder:text-muted"
        />
      </label>

      {data.length === 0 ? (
        <p className="py-8 text-center text-muted">{t("catalog.empty")}</p>
      ) : (
        <ItemList items={active} />
      )}

      {archived.length > 0 && (
        <details className="flex flex-col gap-3">
          <summary className="cursor-pointer text-sm font-bold tracking-wide text-muted uppercase">
            {t("catalog.archived", { count: archived.length })}
          </summary>
          <div className="mt-3 opacity-70">
            <ItemList items={archived} />
          </div>
        </details>
      )}
    </div>
  );
}

function ItemList({ items }: { items: CatalogItem[] }) {
  const { t, i18n } = useTranslation();

  return (
    <ul className="grid gap-3 sm:grid-cols-2">
      {items.map((item) => (
        <li
          key={item.id}
          className="flex items-center gap-3 rounded-card border-theme bg-surface p-4 shadow-card"
        >
          <span
            aria-hidden="true"
            className="size-7 flex-none rounded-full"
            style={{ background: flavorColor(item.name) }}
          />
          <div className="min-w-0">
            {/* Item names are business content, shown as written */}
            <p className="truncate font-bold">{item.name}</p>
            <p className="text-sm text-muted">
              {t("catalog.suggestedPrice", {
                price: formatCurrency(item.suggestedPrice, i18n.language),
              })}
              {item.alcoholic && ` · ${t("menu.alcohol")}`}
            </p>
          </div>
        </li>
      ))}
    </ul>
  );
}

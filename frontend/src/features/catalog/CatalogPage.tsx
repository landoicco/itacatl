import { useState } from "react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router";
import { errorCode } from "@/api/errors";
import { useArchiveItem, useCatalog, useUnarchiveItem } from "@/api/manage";
import { useMembership } from "@/api/session";
import type { CatalogItem } from "@/api/types";
import ErrorMessage from "@/components/ErrorMessage";
import SkeletonCards from "@/components/SkeletonCards";
import { formatCurrency, normalizeForSearch } from "@/i18n/format";
import { flavorColor } from "@/theme/flavorColor";
import ItemSheet from "./ItemSheet";

/**
 * What the business knows how to make. Archived items are folded at the end, never deleted.
 * Owners create, edit and archive items; staff only read.
 */
export default function CatalogPage() {
  const { t } = useTranslation();
  const { slug = "" } = useParams();
  const canManage = useMembership(slug)?.role === "OWNER";
  const [query, setQuery] = useState("");
  // The item in the sheet: "new" for a new one, null when the sheet is closed
  const [editing, setEditing] = useState<CatalogItem | "new" | null>(null);
  const { data, isPending, error, refetch } = useCatalog(slug);
  const archive = useArchiveItem(slug);
  const unarchive = useUnarchiveItem(slug);

  if (isPending) return <SkeletonCards count={4} height="h-16" />;
  if (error) return <ErrorMessage error={error} onRetry={() => void refetch()} />;

  const needle = normalizeForSearch(query);
  const shown = data.filter((item) => normalizeForSearch(item.name).includes(needle));
  const active = shown.filter((item) => !item.archived);
  const archived = shown.filter((item) => item.archived);
  const actionError = archive.error ?? unarchive.error;

  const actions = (item: CatalogItem) =>
    canManage && (
      <div className="ml-auto flex flex-none gap-1.5">
        {item.archived ? (
          <ActionButton onClick={() => unarchive.mutate(item.id)}>
            {t("catalog.unarchive")}
          </ActionButton>
        ) : (
          <>
            <ActionButton onClick={() => setEditing(item)}>{t("catalog.edit")}</ActionButton>
            <ActionButton onClick={() => archive.mutate(item.id)}>
              {t("catalog.archive")}
            </ActionButton>
          </>
        )}
      </div>
    );

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-display text-[28px]">{t("catalog.title")}</h1>
        {canManage && (
          <button
            onClick={() => setEditing("new")}
            className="min-h-11 cursor-pointer rounded-control border-theme bg-accent px-5 font-bold text-on-accent shadow-pressed"
          >
            ＋ {t("catalog.newItem")}
          </button>
        )}
      </div>
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

      {actionError && (
        <p role="alert" className="rounded-card bg-soft px-4 py-3 text-sm font-bold text-low">
          {t(`apiErrors.${errorCode(actionError)}`)}
        </p>
      )}

      {data.length === 0 ? (
        <p className="py-8 text-center text-muted">{t("catalog.empty")}</p>
      ) : (
        <ItemList items={active} actions={actions} />
      )}

      {archived.length > 0 && (
        <details className="flex flex-col gap-3">
          <summary className="cursor-pointer text-sm font-bold tracking-wide text-muted uppercase">
            {t("catalog.archived", { count: archived.length })}
          </summary>
          <div className="mt-3">
            <ItemList items={archived} actions={actions} />
          </div>
        </details>
      )}

      <ItemSheet
        slug={slug}
        item={editing === "new" ? undefined : (editing ?? undefined)}
        open={editing !== null}
        onClose={() => setEditing(null)}
      />
    </div>
  );
}

function ActionButton({ onClick, children }: { onClick: () => void; children: string }) {
  return (
    <button
      onClick={onClick}
      className="min-h-9 cursor-pointer rounded-control bg-soft px-3 text-xs font-bold text-accent"
    >
      {children}
    </button>
  );
}

interface ItemListProps {
  items: CatalogItem[];
  actions: (item: CatalogItem) => ReactNode;
}

function ItemList({ items, actions }: ItemListProps) {
  const { t, i18n } = useTranslation();

  return (
    <ul className="grid gap-3 sm:grid-cols-2">
      {items.map((item) => (
        <li
          key={item.id}
          aria-label={item.name}
          className="flex items-center gap-3 rounded-card border-theme bg-surface p-4 shadow-card"
        >
          <span
            aria-hidden="true"
            className={`size-7 flex-none rounded-full ${item.archived ? "opacity-50" : ""}`}
            style={{ background: flavorColor(item.name) }}
          />
          <div className="min-w-0">
            {/* Item names are business content, shown as written */}
            <p className={`truncate font-bold ${item.archived ? "text-muted" : ""}`}>{item.name}</p>
            <p className="text-sm text-muted">
              {t("catalog.suggestedPrice", {
                price: formatCurrency(item.suggestedPrice, i18n.language),
              })}
              {item.alcoholic && ` · ${t("menu.alcohol")}`}
            </p>
          </div>
          {actions(item)}
        </li>
      ))}
    </ul>
  );
}

import { useTranslation } from "react-i18next";
import type { MenuItem } from "@/api/types";
import { formatCurrency } from "@/i18n/format";
import { flavorColor } from "@/theme/flavorColor";

// At or below this many portions, availability is highlighted
const LOW_STOCK = 5;

export default function MenuItemCard({ item }: { item: MenuItem }) {
  const { t, i18n } = useTranslation();

  const availability =
    item.available === 0 ? (
      <span className="text-[13px] font-bold text-muted">{t("menu.soldOut")}</span>
    ) : item.available <= LOW_STOCK ? (
      <span className="text-[13px] font-bold text-low">
        {t("menu.lowStock", { count: item.available })}
      </span>
    ) : (
      <span className="text-[13px] text-muted">{t("menu.left", { count: item.available })}</span>
    );

  return (
    <article
      className={`flex items-start gap-3.5 rounded-card border-theme bg-surface p-4 shadow-card ${
        item.available === 0 ? "opacity-60" : ""
      }`}
    >
      <span
        aria-hidden="true"
        className="size-11 flex-none rounded-full"
        style={{ background: flavorColor(item.name) }}
      />
      <div className="flex flex-1 flex-col gap-1">
        <div className="flex items-baseline justify-between gap-2">
          <h3 className="font-bold">{item.name}</h3>
          <span className="text-display text-lg">{formatCurrency(item.price, i18n.language)}</span>
        </div>
        {item.description && <p className="text-sm text-muted">{item.description}</p>}
        {availability}
        {item.alcoholic && (
          <span className="self-start rounded-full border border-muted px-2 py-0.5 text-[11px] font-bold text-muted">
            {t("menu.alcohol")}
          </span>
        )}
      </div>
    </article>
  );
}

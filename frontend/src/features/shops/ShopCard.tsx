import { Link } from "react-router";
import type { BusinessSummary } from "@/api/types";
import OpeningStatusBadge from "@/components/OpeningStatusBadge";
import { flavorColor } from "@/theme/flavorColor";

// Scoops and names shown on a card; the rest are counted
const SHOWN_ITEMS = 3;
const SHOWN_SCOOPS = 6;

export default function ShopCard({ shop }: { shop: BusinessSummary }) {
  const items = shop.featuredItems;
  const hidden = items.length - SHOWN_ITEMS;

  return (
    <Link
      to={`/shops/${shop.slug}`}
      className="flex flex-col gap-3.5 rounded-card border-theme bg-surface p-5 shadow-card transition-transform hover:-translate-y-0.5 focus-visible:-translate-y-0.5"
    >
      <div className="flex items-start justify-between gap-3">
        <h2 className="text-display text-[22px] leading-tight">{shop.name}</h2>
        <OpeningStatusBadge status={shop.openingStatus} timeZone={shop.timeZone} />
      </div>
      {shop.description && (
        <p className="line-clamp-3 text-[15px] leading-relaxed text-muted">{shop.description}</p>
      )}
      {items.length > 0 && (
        <div className="mt-auto flex flex-col gap-2">
          <div className="flex gap-1.5" aria-hidden="true">
            {items.slice(0, SHOWN_SCOOPS).map((name) => (
              <span
                key={name}
                className="size-6 rounded-full"
                style={{ background: flavorColor(name) }}
              />
            ))}
          </div>
          {/* Item names are business content, shown as written */}
          <p className="text-sm font-bold">
            {items.slice(0, SHOWN_ITEMS).join(" · ")}
            {hidden > 0 && ` +${hidden}`}
          </p>
        </div>
      )}
    </Link>
  );
}

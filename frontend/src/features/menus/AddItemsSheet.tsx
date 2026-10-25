import { useState } from "react";
import type { FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { errorCode } from "@/api/errors";
import { useAddMenuItem, useCatalog } from "@/api/manage";
import type { CatalogItem, ManagedMenu } from "@/api/types";
import Sheet from "@/components/Sheet";
import SkeletonCards from "@/components/SkeletonCards";
import { formatCurrency, normalizeForSearch } from "@/i18n/format";
import { flavorColor } from "@/theme/flavorColor";

interface AddItemsSheetProps {
  slug: string;
  menu: ManagedMenu;
  open: boolean;
  onClose: () => void;
}

/**
 * Picks items from the catalog for a menu. Stays open, so several can be added in a row; items
 * already in the menu are marked, archived ones are not listed (the API would refuse them).
 */
export default function AddItemsSheet({ slug, menu, open, onClose }: AddItemsSheetProps) {
  const { t } = useTranslation();
  return (
    <Sheet open={open} onClose={onClose} title={t("menus.add.title")}>
      <Picker slug={slug} menu={menu} />
    </Sheet>
  );
}

function Picker({ slug, menu }: { slug: string; menu: ManagedMenu }) {
  const { t } = useTranslation();
  const [query, setQuery] = useState("");
  const catalog = useCatalog(slug);
  const add = useAddMenuItem(slug, menu.id);

  if (catalog.isPending) return <SkeletonCards count={3} height="h-16" />;
  if (catalog.isError) {
    return <p role="alert">{t(`apiErrors.${errorCode(catalog.error)}`)}</p>;
  }

  const inMenu = new Set(menu.items.map((item) => item.itemId));
  const needle = normalizeForSearch(query);
  const items = catalog.data.filter(
    (item) => !item.archived && normalizeForSearch(item.name).includes(needle)
  );

  return (
    <div className="flex flex-col gap-3">
      <label>
        <span className="sr-only">{t("catalog.search")}</span>
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={t("catalog.search")}
          className="min-h-12 w-full rounded-control border-2 border-soft bg-surface px-4 text-[15px] outline-none placeholder:text-muted focus:border-accent"
        />
      </label>

      {add.error && (
        <p role="alert" className="rounded-card bg-soft px-4 py-3 text-sm font-bold text-low">
          {t(`apiErrors.${errorCode(add.error)}`)}
        </p>
      )}

      {items.length === 0 ? (
        <p className="py-6 text-center text-muted">{t("menus.add.none")}</p>
      ) : (
        <ul className="flex max-h-[50dvh] flex-col gap-2 overflow-y-auto">
          {items.map((item) => (
            <CatalogRow
              key={item.id}
              item={item}
              added={inMenu.has(item.id)}
              onAdd={(quantity) => add.mutateAsync({ itemId: item.id, quantity })}
            />
          ))}
        </ul>
      )}
    </div>
  );
}

interface CatalogRowProps {
  item: CatalogItem;
  added: boolean;
  /** Adds the item at its suggested price; the owner adjusts it in the editor if needed */
  onAdd: (quantity: number) => Promise<unknown>;
}

function CatalogRow({ item, added, onAdd }: CatalogRowProps) {
  const { t, i18n } = useTranslation();
  const [quantity, setQuantity] = useState("");
  // Until the server answers, the row still looks addable; a second tap would be refused as
  // ITEM_ALREADY_IN_MENU although the first one worked
  const [adding, setAdding] = useState(false);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (adding) return;
    setAdding(true);
    // The sheet shows the error; the row becomes addable again
    onAdd(Number(quantity))
      .catch(() => undefined)
      .finally(() => setAdding(false));
  };

  return (
    <li aria-label={item.name} className="flex items-center gap-3 rounded-card bg-surface p-3">
      <span
        aria-hidden="true"
        className="size-7 flex-none rounded-full"
        style={{ background: flavorColor(item.name) }}
      />
      <div className="min-w-0 flex-1">
        {/* Item names are business content, shown as written */}
        <p className="truncate font-bold">{item.name}</p>
        <p className="text-sm text-muted">
          {added
            ? t("menus.add.inMenu")
            : t("catalog.suggestedPrice", {
                price: formatCurrency(item.suggestedPrice, i18n.language),
              })}
        </p>
      </div>
      {!added && (
        // One form per row: the portions are required, and Enter adds the item
        <form onSubmit={submit} className="flex items-center gap-2">
          <input
            type="number"
            inputMode="numeric"
            min={0}
            step={1}
            required
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
            aria-label={t("menus.editor.quantity", { name: item.name })}
            placeholder={t("menus.add.portions")}
            className="min-h-10 w-20 rounded-control border-2 border-soft bg-surface px-3 text-center font-bold outline-none focus:border-accent"
          />
          <button
            type="submit"
            disabled={adding}
            className="min-h-10 cursor-pointer rounded-control border-theme bg-accent px-4 text-sm font-bold text-on-accent shadow-pressed disabled:opacity-60"
          >
            {t("menus.add.add")}
          </button>
        </form>
      )}
    </li>
  );
}

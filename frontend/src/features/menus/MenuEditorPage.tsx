import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import {
  useManagedMenu,
  usePublishMenu,
  useRemoveMenuItem,
  useRescheduleMenu,
  useUpdateMenuItem,
} from "@/api/manage";
import { useBusiness } from "@/api/queries";
import { useMembership } from "@/api/session";
import type { ManagedMenuItem, UpdateMenuItemRequest } from "@/api/types";
import ErrorMessage from "@/components/ErrorMessage";
import InlineError from "@/components/InlineError";
import SkeletonCards from "@/components/SkeletonCards";
import { currencySymbol, formatCurrency, formatTime } from "@/i18n/format";
import { itemColor } from "@/theme/itemColor";
import AddItemsSheet from "./AddItemsSheet";
import { hasEnded, hhmm, useDayLabel } from "./menuDates";
import MenuWindowSheet from "./MenuWindowSheet";

/**
 * One menu: its window, its status and its items with that day's price and portions. Owners
 * change them in place, each change saved on its own; staff only read. Ended menus are history.
 */
export default function MenuEditorPage() {
  const { t, i18n } = useTranslation();
  const { slug = "", menuId: menuIdParam = "" } = useParams();
  const menuId = Number(menuIdParam);
  const isOwner = useMembership(slug)?.role === "OWNER";
  const [rescheduling, setRescheduling] = useState(false);
  const [adding, setAdding] = useState(false);
  const menu = useManagedMenu(slug, menuId);
  const business = useBusiness(slug);
  const dayLabel = useDayLabel(business.data?.timeZone);
  // In the menu's scope, so a publish waits for pending item saves instead of racing them
  const publish = usePublishMenu(slug, menuId);
  const reschedule = useRescheduleMenu(slug, menuId);
  const update = useUpdateMenuItem(slug, menuId);
  const remove = useRemoveMenuItem(slug, menuId);

  if (menu.isPending || business.isPending) return <SkeletonCards count={3} height="h-20" />;
  if (menu.isError) return <ErrorMessage error={menu.error} onRetry={() => void menu.refetch()} />;
  if (business.isError) {
    return <ErrorMessage error={business.error} onRetry={() => void business.refetch()} />;
  }

  const { data } = menu;
  const ended = hasEnded(data, business.data.timeZone);
  // The backend refuses changes to an ended menu; the screen does not offer them
  const canChange = isOwner && !ended;
  const isDraft = data.status === "DRAFT";
  // Item saves report their errors in their own row (ItemRow)
  const actionError = remove.error ?? publish.error;

  return (
    <div className="flex flex-col gap-4">
      <Link to={`/manage/${slug}/menus`} className="text-sm font-bold text-muted">
        ‹ {t("manage.sections.menus")}
      </Link>

      <div className="flex flex-wrap items-start gap-3">
        <div>
          <h1 className="text-display text-[26px]">{dayLabel(data.serviceDate)}</h1>
          <p className="text-sm text-muted">
            {formatTime(data.startsAt, i18n.language)} – {formatTime(data.endsAt, i18n.language)}
            {canChange && isDraft && (
              <>
                {" · "}
                <button
                  onClick={() => setRescheduling(true)}
                  className="cursor-pointer font-bold text-accent underline"
                >
                  {t("menus.editor.change")}
                </button>
              </>
            )}
          </p>
        </div>
        <span
          className={`ml-auto rounded-control px-3 py-1 text-xs font-bold ${
            isDraft ? "bg-soft text-accent" : "bg-open text-on-open"
          }`}
        >
          {t(isDraft ? "menus.draft" : "menus.published")}
        </span>
      </div>

      {ended ? (
        <p className="rounded-card bg-soft px-4 py-3 text-sm font-bold text-muted">
          {t("menus.editor.ended")}
        </p>
      ) : (
        isDraft && (
          <div className="flex items-center gap-3 rounded-card bg-soft px-4 py-3">
            <p className="flex-1 text-sm font-bold text-accent">{t("menus.editor.hidden")}</p>
            {isOwner && (
              <button
                onClick={() => publish.mutate(menuId)}
                // A second tap would reach an already published menu: MENU_NOT_DRAFT
                disabled={publish.isPending}
                className="min-h-10 cursor-pointer rounded-control border-theme bg-accent px-4 text-sm font-bold text-on-accent shadow-pressed disabled:opacity-60"
              >
                {t("menus.publish")}
              </button>
            )}
          </div>
        )
      )}

      {actionError && <InlineError error={actionError} />}

      {data.items.length === 0 ? (
        <p className="py-8 text-center text-muted">{t("menus.editor.noItems")}</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {data.items.map((item) => (
            <ItemRow
              key={item.itemId}
              item={item}
              canChange={canChange}
              onSave={(change) => update.mutateAsync({ itemId: item.itemId, item: change })}
              onRemove={() => remove.mutate(item.itemId)}
            />
          ))}
        </ul>
      )}

      {canChange && (
        <button
          onClick={() => setAdding(true)}
          className="min-h-12 cursor-pointer rounded-control bg-soft font-bold text-accent"
        >
          ＋ {t("menus.add.title")}
        </button>
      )}

      <AddItemsSheet slug={slug} menu={data} open={adding} onClose={() => setAdding(false)} />
      <MenuWindowSheet
        title={t("menus.editor.change")}
        submitLabel={t("menus.form.save")}
        initial={{
          serviceDate: data.serviceDate,
          startsAt: hhmm(data.startsAt),
          endsAt: hhmm(data.endsAt),
        }}
        open={rescheduling}
        onClose={() => setRescheduling(false)}
        onSubmit={(when) => reschedule.mutateAsync(when)}
      />
    </div>
  );
}

// "" and "  " are not numbers here, unlike for Number()
const parse = (text: string) => (text.trim() === "" ? NaN : Number(text));

interface ItemRowProps {
  item: ManagedMenuItem;
  canChange: boolean;
  /** Saves price and portions together, as the API takes them */
  onSave: (change: UpdateMenuItemRequest) => Promise<unknown>;
  onRemove: () => void;
}

function ItemRow({ item, canChange, onSave, onRemove }: ItemRowProps) {
  const { t, i18n } = useTranslation();
  // What the inputs show, and the last values sent to the server (null after a failed save)
  const [price, setPrice] = useState(String(item.price));
  const [quantity, setQuantity] = useState(String(item.quantity));
  const [sent, setSent] = useState<UpdateMenuItemRequest | null>(item);
  const [pending, setPending] = useState(0);
  // This row's last failed save. One useMutation serves every row and only tracks its latest
  // call, so a later save in another row would hide this error
  const [error, setError] = useState<unknown>(null);

  // Follow the server only once every save has answered. Mid-way, an answer carries the values
  // of an earlier tap and would undo the later ones on screen. Saves to one menu run in order
  // (useUpdateMenuItem's scope), so the last answer holds the final values
  if (pending === 0 && (sent?.price !== item.price || sent?.quantity !== item.quantity)) {
    setSent(item);
    setPrice(String(item.price));
    setQuantity(String(item.quantity));
  }

  const revert = () => {
    setPrice(String(sent?.price ?? item.price));
    setQuantity(String(sent?.quantity ?? item.quantity));
  };

  // Saves what the inputs show, if it is valid and differs from what is saved. An empty field is
  // invalid, not 0: Number("") is 0, and a cleared price would put the item on sale for free
  const save = (next: { price?: string; quantity?: string }) => {
    const change = {
      price: parse(next.price ?? price),
      quantity: parse(next.quantity ?? quantity),
    };
    const valid =
      Number.isFinite(change.price) &&
      change.price >= 0 &&
      Number.isInteger(change.quantity) &&
      change.quantity >= 0;
    if (!valid) return revert();
    if (change.price === sent?.price && change.quantity === sent?.quantity) return;
    setSent(change);
    setPending((count) => count + 1);
    onSave(change)
      .then(() => setError(null))
      // A rejected save: forget what was sent, so the inputs return to the saved values
      .catch((rejected: unknown) => {
        setSent(null);
        setError(rejected);
      })
      .finally(() => setPending((count) => count - 1));
  };

  const step = (by: number) => {
    // From the last valid value when the field is empty or half typed
    const current = parse(quantity);
    const base = Number.isInteger(current) ? current : (sent?.quantity ?? item.quantity);
    const next = String(Math.max(0, base + by));
    setQuantity(next);
    save({ quantity: next });
  };

  const soldOut = Number(quantity) === 0;
  // 44 px: the smallest touch target the accessibility guidelines recommend
  const stepButton =
    "flex size-11 cursor-pointer items-center justify-center text-lg font-bold text-accent";

  return (
    <li
      aria-label={item.name}
      className="flex flex-col gap-3 rounded-card border-theme bg-surface p-4 shadow-card"
    >
      <div className="flex items-center gap-3">
        <span
          aria-hidden="true"
          className={`size-7 flex-none rounded-full ${item.archived ? "opacity-50" : ""}`}
          style={{ background: itemColor(item.name) }}
        />
        {/* Item names are business content, shown as written */}
        <span className={`font-bold ${item.archived ? "text-muted line-through" : ""}`}>
          {item.name}
        </span>
        {item.archived && (
          <span className="rounded-control bg-soon px-2 py-0.5 text-xs font-bold text-on-soon">
            {t("menus.editor.archived")}
          </span>
        )}
        {canChange && (
          <button
            onClick={onRemove}
            aria-label={t("menus.editor.remove", { name: item.name })}
            className="ml-auto flex size-9 cursor-pointer items-center justify-center rounded-full font-bold text-muted"
          >
            ✕
          </button>
        )}
      </div>

      {item.archived && <p className="text-sm text-muted">{t("menus.editor.archivedNote")}</p>}

      {error !== null && <InlineError error={error} compact />}

      {canChange ? (
        <div className="flex flex-wrap items-center gap-2">
          {/* The symbol is decoration; aria-label gives the input its exact name */}
          <span className="flex items-center gap-1 font-bold">
            <span aria-hidden="true">{currencySymbol(i18n.language)}</span>
            <input
              aria-label={t("menus.editor.price", { name: item.name })}
              type="number"
              inputMode="decimal"
              min={0}
              step={0.01}
              value={price}
              onChange={(event) => setPrice(event.target.value)}
              onBlur={() => save({})}
              className="min-h-10 w-20 rounded-control border-2 border-soft bg-surface px-3 outline-none focus:border-accent"
            />
          </span>
          <div className="flex items-center rounded-control bg-soft">
            <button
              onClick={() => step(-1)}
              disabled={soldOut}
              aria-label={t("menus.editor.less", { name: item.name })}
              className={`${stepButton} disabled:opacity-40`}
            >
              −
            </button>
            <label>
              <span className="sr-only">{t("menus.editor.quantity", { name: item.name })}</span>
              <input
                type="number"
                inputMode="numeric"
                min={0}
                step={1}
                value={quantity}
                onChange={(event) => setQuantity(event.target.value)}
                onBlur={() => save({})}
                className={`w-14 bg-transparent text-center font-bold outline-none ${soldOut ? "text-low" : ""}`}
              />
            </label>
            <button
              onClick={() => step(1)}
              aria-label={t("menus.editor.more", { name: item.name })}
              className={stepButton}
            >
              ＋
            </button>
          </div>
          {soldOut ? (
            <span className="ml-auto rounded-control bg-soft px-3 py-1 text-xs font-bold text-low">
              {t("menu.soldOut")}
            </span>
          ) : (
            <button
              onClick={() => {
                setQuantity("0");
                save({ quantity: "0" });
              }}
              className="ml-auto min-h-9 cursor-pointer rounded-control bg-soft px-3 text-xs font-bold text-accent"
            >
              {t("menu.soldOut")}
            </button>
          )}
        </div>
      ) : (
        <p className="text-sm text-muted">
          {formatCurrency(item.price, i18n.language)} ·{" "}
          {item.quantity === 0
            ? t("menu.soldOut")
            : t("menus.editor.portions", { count: item.quantity })}
        </p>
      )}
    </li>
  );
}

import { useState } from "react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import { errorCode } from "@/api/errors";
import {
  useCreateMenu,
  useDeleteMenu,
  useDuplicateMenu,
  useManagedMenus,
  usePublishMenu,
} from "@/api/manage";
import { useBusiness } from "@/api/queries";
import { useMembership } from "@/api/session";
import type { ManagedMenu, MenuWindow } from "@/api/types";
import ErrorMessage from "@/components/ErrorMessage";
import SkeletonCards from "@/components/SkeletonCards";
import { formatTime, todayIn } from "@/i18n/format";
import { flavorColor } from "@/theme/flavorColor";
import { addDays, hhmm, laterOf, useDayLabel } from "./menuDates";
import MenuWindowSheet from "./MenuWindowSheet";

// The sheet on screen: a new menu, or a duplicate of one
type SheetState = { kind: "new" } | { kind: "duplicate"; source: ManagedMenu } | null;

const SHOWN_SCOOPS = 8;

/**
 * A business's menus from today on, by day, drafts included. Owners create, duplicate, publish
 * and delete drafts here; the menu's items are edited on its own page.
 */
export default function MenusPage() {
  const { t } = useTranslation();
  const { slug = "" } = useParams();
  const canManage = useMembership(slug)?.role === "OWNER";
  const [sheet, setSheet] = useState<SheetState>(null);
  const menus = useManagedMenus(slug);
  // The public profile has the business's time zone, which "today" and "tomorrow" depend on
  const business = useBusiness(slug);
  const dayLabel = useDayLabel(business.data?.timeZone);
  const create = useCreateMenu(slug);
  const duplicate = useDuplicateMenu(slug);
  const publish = usePublishMenu(slug);
  const remove = useDeleteMenu(slug);

  // One check per query, so TypeScript narrows each one's data
  if (menus.isPending || business.isPending) return <SkeletonCards count={3} height="h-24" />;
  if (menus.isError)
    return <ErrorMessage error={menus.error} onRetry={() => void menus.refetch()} />;
  if (business.isError) {
    return <ErrorMessage error={business.error} onRetry={() => void business.refetch()} />;
  }

  const today = todayIn(business.data.timeZone);
  const actionError = publish.error ?? remove.error;

  // Menus arrive sorted by date and start time; a Map keeps that order per day
  const days = menus.data.reduce((byDay, menu) => {
    byDay.set(menu.serviceDate, [...(byDay.get(menu.serviceDate) ?? []), menu]);
    return byDay;
  }, new Map<string, ManagedMenu[]>());

  const confirmDelete = (menu: ManagedMenu) => {
    if (window.confirm(t("menus.confirmDelete", { day: dayLabel(menu.serviceDate) }))) {
      remove.mutate(menu.id);
    }
  };

  const sheetProps =
    sheet?.kind === "duplicate"
      ? {
          title: t("menus.duplicateTitle", { day: dayLabel(sheet.source.serviceDate) }),
          hint: t("menus.duplicateHint"),
          submitLabel: t("menus.form.createDraft"),
          // The next day with the same window, as repeating a menu is the common case; today
          // at the earliest, for an old menu
          initial: {
            serviceDate: laterOf(addDays(sheet.source.serviceDate, 1), today),
            startsAt: hhmm(sheet.source.startsAt),
            endsAt: hhmm(sheet.source.endsAt),
          },
          onSubmit: (when: MenuWindow) =>
            duplicate.mutateAsync({ menuId: sheet.source.id, window: when }),
        }
      : {
          title: t("menus.newMenu"),
          submitLabel: t("menus.form.createDraft"),
          initial: { serviceDate: today, startsAt: "", endsAt: "" },
          onSubmit: (when: MenuWindow) => create.mutateAsync(when),
        };

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-display text-[28px]">{t("menus.title")}</h1>
        {canManage && (
          <button
            onClick={() => setSheet({ kind: "new" })}
            className="min-h-11 cursor-pointer rounded-control border-theme bg-accent px-5 font-bold text-on-accent shadow-pressed"
          >
            ＋ {t("menus.newMenu")}
          </button>
        )}
      </div>

      {actionError && (
        <p role="alert" className="rounded-card bg-soft px-4 py-3 text-sm font-bold text-low">
          {t(`apiErrors.${errorCode(actionError)}`)}
        </p>
      )}

      {days.size === 0 && <p className="py-8 text-center text-muted">{t("menus.empty")}</p>}

      {[...days].map(([date, dayMenus]) => (
        <section key={date} aria-label={dayLabel(date)} className="flex flex-col gap-3">
          <h2 className="text-sm font-bold tracking-wide text-muted uppercase">{dayLabel(date)}</h2>
          {dayMenus.map((menu) => (
            <MenuCard key={menu.id} slug={slug} menu={menu}>
              {canManage && (
                <>
                  {menu.status === "DRAFT" && (
                    <ActionButton primary onClick={() => publish.mutate(menu.id)}>
                      {t("menus.publish")}
                    </ActionButton>
                  )}
                  <ActionButton onClick={() => setSheet({ kind: "duplicate", source: menu })}>
                    {t("menus.duplicate")}
                  </ActionButton>
                  {menu.status === "DRAFT" && (
                    <ActionButton onClick={() => confirmDelete(menu)}>
                      {t("menus.delete")}
                    </ActionButton>
                  )}
                </>
              )}
            </MenuCard>
          ))}
        </section>
      ))}

      <MenuWindowSheet {...sheetProps} open={sheet !== null} onClose={() => setSheet(null)} />
    </div>
  );
}

function MenuCard({
  slug,
  menu,
  children,
}: {
  slug: string;
  menu: ManagedMenu;
  children: ReactNode;
}) {
  const { t, i18n } = useTranslation();
  const hours = `${formatTime(menu.startsAt, i18n.language)} – ${formatTime(menu.endsAt, i18n.language)}`;
  const soldOut = menu.items.filter((item) => item.quantity === 0).length;

  return (
    <article
      aria-label={hours}
      className="flex flex-col gap-3 rounded-card border-theme bg-surface p-4 shadow-card"
    >
      <div className="flex items-center gap-3">
        <Link to={`/manage/${slug}/menus/${menu.id}`} className="text-display text-[19px]">
          {hours}
        </Link>
        {menu.status === "PUBLISHED" ? (
          <span className="rounded-control bg-open px-3 py-1 text-xs font-bold text-on-open">
            {t("menus.published")}
          </span>
        ) : (
          <span className="rounded-control bg-soft px-3 py-1 text-xs font-bold text-accent">
            {t("menus.draft")}
          </span>
        )}
      </div>

      <div className="flex items-center gap-3">
        {menu.items.length > 0 && (
          <div className="flex gap-1" aria-hidden="true">
            {menu.items.slice(0, SHOWN_SCOOPS).map((item) => (
              <span
                key={item.itemId}
                className="size-4 rounded-full"
                style={{ background: flavorColor(item.name) }}
              />
            ))}
          </div>
        )}
        <p className="text-sm text-muted">
          {menu.items.length === 0
            ? t("menus.emptyMenu")
            : [
                t("menus.items", { count: menu.items.length }),
                soldOut > 0 && t("menus.soldOut", { count: soldOut }),
                menu.status === "DRAFT" && t("menus.hidden"),
              ]
                .filter(Boolean)
                .join(" · ")}
        </p>
      </div>

      {children && <div className="flex flex-wrap gap-2">{children}</div>}
    </article>
  );
}

interface ActionButtonProps {
  onClick: () => void;
  primary?: boolean;
  children: string;
}

function ActionButton({ onClick, primary = false, children }: ActionButtonProps) {
  return (
    <button
      onClick={onClick}
      className={`min-h-10 cursor-pointer rounded-control px-4 text-sm font-bold ${
        primary ? "border-theme bg-accent text-on-accent shadow-pressed" : "bg-soft text-accent"
      }`}
    >
      {children}
    </button>
  );
}

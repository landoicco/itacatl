import { useTranslation } from "react-i18next";
import { Link, Navigate } from "react-router";
import { useCurrentUser } from "@/api/session";
import SiteHeader from "@/components/SiteHeader";

/** Where the panel starts: straight into the only business, or a choice when there are several */
export default function BusinessPickerPage() {
  const { t } = useTranslation();
  // RequireSession only renders this page once the user is known
  const { data: user } = useCurrentUser();
  const businesses = user?.businesses ?? [];

  if (businesses.length === 1) {
    return <Navigate to={`/manage/${businesses[0].slug}/menus`} replace />;
  }

  return (
    <>
      <SiteHeader />
      <main className="mx-auto flex max-w-[560px] flex-col gap-4 px-5 py-8">
        <h1 className="text-display text-[28px]">{t("manage.greeting", { name: user?.name })}</h1>
        {businesses.length === 0 ? (
          <p className="text-muted">{t("manage.noBusinesses")}</p>
        ) : (
          <>
            <p className="-mt-2 text-muted">{t("manage.pickBusiness")}</p>
            <ul className="flex flex-col gap-3">
              {businesses.map((business) => (
                <li key={business.slug}>
                  <Link
                    to={`/manage/${business.slug}/menus`}
                    className="flex items-center justify-between gap-3 rounded-card border-theme bg-surface p-5 shadow-card"
                  >
                    {/* Business names are content, shown as written */}
                    <span className="text-display text-[20px]">{business.name}</span>
                    <span className="rounded-control bg-soft px-3 py-1 text-xs font-bold text-accent">
                      {t(`manage.role.${business.role}`)}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </>
        )}
      </main>
    </>
  );
}

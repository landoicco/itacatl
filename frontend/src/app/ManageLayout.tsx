import { useTranslation } from "react-i18next";
import { Link, Navigate, NavLink, Outlet, useMatch, useNavigate, useParams } from "react-router";
import { useCurrentUser, useMembership, useSignOut } from "@/api/session";
import LanguageSwitcher from "@/components/LanguageSwitcher";

const SECTIONS = ["menus", "catalog"] as const;

/**
 * The frame of one business's panel: a header with the business switcher and sign-out, and the
 * sections as a tab bar at the bottom on a phone, where thumbs reach, or as links in the header
 * from the sm breakpoint up. Rendered inside RequireSession, so the user is known.
 */
export default function ManageLayout() {
  const { t } = useTranslation();
  const { slug = "" } = useParams();
  const sectionMatch = useMatch("/manage/:slug/:section/*");
  const navigate = useNavigate();
  const { data: user } = useCurrentUser();
  const membership = useMembership(slug);
  const signOut = useSignOut();

  // A business the user does not belong to: back to their own list instead of a page of 403s
  if (!membership) {
    return <Navigate to="/manage" replace />;
  }

  // Switching business keeps the section: from Sunny's catalog to Hilltop's catalog. Read from
  // the route's segments, since a slug may contain a section's name ("menus-y-mas")
  const section = SECTIONS.find((name) => name === sectionMatch?.params.section) ?? "menus";
  const tabClass = ({ isActive }: { isActive: boolean }) =>
    `flex min-h-12 flex-1 items-center justify-center font-bold sm:flex-none sm:px-3 ${
      isActive ? "text-accent" : "text-muted"
    }`;
  const tabs = SECTIONS.map((name) => (
    <NavLink key={name} to={`/manage/${slug}/${name}`} className={tabClass}>
      {t(`manage.sections.${name}`)}
    </NavLink>
  ));

  return (
    <div className="flex min-h-dvh flex-col pb-16 sm:pb-0">
      <header className="mx-auto flex w-full max-w-[1100px] flex-wrap items-center gap-3 px-5 py-4 sm:px-6">
        <Link to="/" className="text-display text-[24px] text-accent">
          itacatl
        </Link>
        <nav aria-label={t("manage.sectionsLabel")} className="hidden gap-1 sm:flex">
          {tabs}
        </nav>
        <label className="ml-auto">
          <span className="sr-only">{t("manage.switchBusiness")}</span>
          <select
            value={slug}
            onChange={(event) => navigate(`/manage/${event.target.value}/${section}`)}
            className="min-h-10 max-w-[180px] cursor-pointer rounded-control border-theme bg-surface px-3 text-sm font-bold shadow-card"
          >
            {/* Business names are content, shown as written */}
            {user?.businesses.map((business) => (
              <option key={business.slug} value={business.slug}>
                {business.name}
              </option>
            ))}
          </select>
        </label>
        <LanguageSwitcher />
        <button
          // Leave first: forgetting the user would make RequireSession send this page to the
          // sign-in form. The sign-out finishes after this layout unmounts, since the hook's own
          // onSuccess always runs
          onClick={() => {
            navigate("/");
            signOut.mutate();
          }}
          className="min-h-10 cursor-pointer rounded-control bg-soft px-4 text-sm font-bold text-accent"
        >
          {t("auth.signOut")}
        </button>
      </header>

      <main className="mx-auto w-full max-w-[1100px] flex-1 px-5 pb-8 sm:px-6">
        <Outlet />
      </main>

      <nav
        aria-label={t("manage.sectionsLabel")}
        className="fixed inset-x-0 bottom-0 flex border-t-2 border-soft bg-surface sm:hidden"
      >
        {tabs}
      </nav>
    </div>
  );
}

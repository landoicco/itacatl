import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import SiteHeader from "@/components/SiteHeader";

export default function NotFoundPage() {
  const { t } = useTranslation();

  return (
    <>
      <SiteHeader />
      <main className="mx-auto flex max-w-[1100px] flex-col items-center gap-6 px-5 py-24 text-center">
        <h1 className="text-display text-4xl">{t("notFound.title")}</h1>
        <Link
          to="/"
          className="inline-flex min-h-11 items-center rounded-control border-theme bg-accent px-6 font-bold text-on-accent shadow-pressed"
        >
          {t("notFound.home")}
        </Link>
      </main>
    </>
  );
}

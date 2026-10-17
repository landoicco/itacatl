import { useTranslation } from "react-i18next";
import { SUPPORTED_LOCALES } from "@/i18n";

export default function LanguageSwitcher() {
  const { t, i18n } = useTranslation();

  return (
    <div
      role="group"
      aria-label={t("header.language")}
      className="flex rounded-full bg-soft p-[3px] text-[13px] font-bold"
    >
      {SUPPORTED_LOCALES.map((locale) => (
        <button
          key={locale}
          lang={locale}
          aria-pressed={i18n.resolvedLanguage === locale}
          onClick={() => i18n.changeLanguage(locale)}
          className="min-h-9 min-w-11 cursor-pointer rounded-full uppercase text-muted aria-pressed:bg-surface aria-pressed:text-ink"
        >
          {/* Locale codes are shown as-is, so they read the same in every language */}
          {locale.split("-")[0]}
        </button>
      ))}
    </div>
  );
}

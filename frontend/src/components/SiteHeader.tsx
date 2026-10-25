import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import LanguageSwitcher from "./LanguageSwitcher";
import PapelPicado from "./PapelPicado";

interface SiteHeaderProps {
  /** Page-specific controls shown between the logo and the language switcher, e.g. search */
  children?: ReactNode;
}

export default function SiteHeader({ children }: SiteHeaderProps) {
  const { t } = useTranslation();

  return (
    <>
      <PapelPicado />
      <header className="py-4">
        <div className="mx-auto flex max-w-[1100px] flex-wrap items-center gap-x-6 gap-y-3 px-5 sm:px-6">
          {/* The brand name is the same in every language */}
          <Link to="/" className="text-display text-[28px] text-accent">
            itacatl
          </Link>
          {/* On a phone the controls drop below the logo row; on desktop they sit between */}
          <div className="order-last w-full sm:order-none sm:w-auto sm:flex-1">{children}</div>
          <div className="ml-auto flex items-center gap-3 sm:ml-0">
            {/* /manage sends a visitor without a session to sign in first; the link costs no
                request, unlike asking the API who is signed in on every marketplace page */}
            <Link to="/manage" className="text-sm font-bold text-accent">
              {t("header.businessSignIn")}
            </Link>
            <LanguageSwitcher />
          </div>
        </div>
      </header>
    </>
  );
}

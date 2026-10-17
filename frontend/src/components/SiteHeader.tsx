import type { ReactNode } from "react";
import { Link } from "react-router";
import LanguageSwitcher from "./LanguageSwitcher";
import PapelPicado from "./PapelPicado";

interface SiteHeaderProps {
  /** Page-specific controls shown between the logo and the language switcher, e.g. search */
  children?: ReactNode;
}

export default function SiteHeader({ children }: SiteHeaderProps) {
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
          <div className="ml-auto sm:ml-0">
            <LanguageSwitcher />
          </div>
        </div>
      </header>
    </>
  );
}

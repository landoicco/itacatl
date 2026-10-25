import { useEffect, useId, useRef } from "react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

interface SheetProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
}

/**
 * A modal sheet: slides up from the bottom on a phone, centered from the sm breakpoint up. Built
 * on the native <dialog> with showModal(), which traps focus, closes on Escape, puts the sheet in
 * the top layer and makes the page behind it inert, so none of that is reimplemented here.
 */
export default function Sheet({ open, onClose, title, children }: SheetProps) {
  const { t } = useTranslation();
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const element = dialog.current;
    if (!element) return;
    if (open && !element.open) element.showModal();
    if (!open && element.open) element.close();
  }, [open]);

  return (
    <dialog
      ref={dialog}
      aria-labelledby={titleId}
      // Escape closes the dialog by itself; tell the parent so its state follows
      onClose={onClose}
      // A click on the backdrop lands on the dialog element itself, outside its content
      onClick={(event) => event.target === event.currentTarget && onClose()}
      className="m-0 mt-auto w-full max-w-none rounded-t-card bg-page p-0 text-ink backdrop:bg-ink/45 sm:m-auto sm:max-w-[480px] sm:rounded-card"
    >
      <div className="flex flex-col gap-4 p-5">
        <div className="flex items-center justify-between gap-3">
          <h2 id={titleId} className="text-display text-[22px]">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label={t("sheet.close")}
            className="flex size-10 cursor-pointer items-center justify-center rounded-full bg-soft font-bold text-accent"
          >
            ✕
          </button>
        </div>
        {/* Mounted only while open, so a form inside starts fresh every time */}
        {open && children}
      </div>
    </dialog>
  );
}

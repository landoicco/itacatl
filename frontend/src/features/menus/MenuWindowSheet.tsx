import { useState } from "react";
import type { FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { errorCode } from "@/api/errors";
import type { ManagedMenu, MenuWindow } from "@/api/types";
import Sheet from "@/components/Sheet";

interface MenuWindowSheetProps {
  title: string;
  /** Explains what saving does, e.g. what a duplicate copies */
  hint?: string;
  submitLabel: string;
  initial: MenuWindow;
  open: boolean;
  onClose: () => void;
  /** Saves the window; the sheet closes once the promise resolves */
  onSubmit: (window: MenuWindow) => Promise<ManagedMenu>;
}

/** When a menu is served: a date and a window, for a new menu, a duplicate or a reschedule */
export default function MenuWindowSheet({ title, open, onClose, ...form }: MenuWindowSheetProps) {
  return (
    <Sheet open={open} onClose={onClose} title={title}>
      <WindowForm {...form} onSaved={onClose} />
    </Sheet>
  );
}

const inputClass =
  "min-h-12 rounded-control border-2 border-soft bg-surface px-4 text-[15px] font-semibold outline-none focus:border-accent";

type WindowFormProps = Pick<
  MenuWindowSheetProps,
  "hint" | "submitLabel" | "initial" | "onSubmit"
> & {
  onSaved: () => void;
};

function WindowForm({ hint, submitLabel, initial, onSubmit, onSaved }: WindowFormProps) {
  const { t } = useTranslation();
  const [when, setWhen] = useState(initial);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const set = (field: keyof MenuWindow) => (event: { target: { value: string } }) =>
    setWhen({ ...when, [field]: event.target.value });

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    onSubmit(when).then(onSaved, (rejected: unknown) => {
      setError(rejected);
      setSaving(false);
    });
  };

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      {hint && <p className="-mt-2 text-sm text-muted">{hint}</p>}
      {/* The window's rules (it ends after it starts, it has not ended) are the backend's */}
      {error !== null && (
        <p role="alert" className="rounded-card bg-soft px-4 py-3 text-sm font-bold text-low">
          {t(`apiErrors.${errorCode(error)}`)}
        </p>
      )}
      <label className="flex flex-col gap-1.5 text-sm font-bold">
        {t("menus.form.date")}
        <input
          type="date"
          value={when.serviceDate}
          onChange={set("serviceDate")}
          required
          className={inputClass}
        />
      </label>
      <div className="grid grid-cols-2 gap-3">
        <label className="flex flex-col gap-1.5 text-sm font-bold">
          {t("menus.form.opens")}
          <input
            type="time"
            value={when.startsAt}
            onChange={set("startsAt")}
            required
            className={inputClass}
          />
        </label>
        <label className="flex flex-col gap-1.5 text-sm font-bold">
          {t("menus.form.closes")}
          <input
            type="time"
            value={when.endsAt}
            onChange={set("endsAt")}
            required
            className={inputClass}
          />
        </label>
      </div>
      <button
        type="submit"
        disabled={saving}
        className="min-h-12 cursor-pointer rounded-control border-theme bg-accent font-bold text-on-accent shadow-pressed disabled:opacity-60"
      >
        {saving ? t("menus.form.saving") : submitLabel}
      </button>
    </form>
  );
}

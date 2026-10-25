import { useTranslation } from "react-i18next";
import { errorCode } from "@/api/errors";

interface InlineErrorProps {
  error: unknown;
  /** Plain text, for tight spots such as a row; a highlighted box otherwise */
  compact?: boolean;
}

/**
 * A failed action, translated from its API code, shown where the action happened: above a list
 * or inside a form. For a screen that could not load at all, ErrorMessage offers a retry instead.
 */
export default function InlineError({ error, compact = false }: InlineErrorProps) {
  const { t } = useTranslation();
  return (
    <p
      role="alert"
      className={`text-sm font-bold text-low ${compact ? "" : "rounded-card bg-soft px-4 py-3"}`}
    >
      {t(`apiErrors.${errorCode(error)}`)}
    </p>
  );
}

import { useTranslation } from "react-i18next";
import { errorCode } from "@/api/errors";

interface ErrorMessageProps {
  error: unknown;
  onRetry?: () => void;
}

/** Shows an API error in the user's language, from its code; never the backend's English text */
export default function ErrorMessage({ error, onRetry }: ErrorMessageProps) {
  const { t } = useTranslation();
  return (
    <div role="alert" className="flex flex-col items-center gap-4 py-16 text-center">
      <p className="text-lg text-muted">{t(`apiErrors.${errorCode(error)}`)}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="min-h-11 cursor-pointer rounded-control border-theme bg-accent px-6 font-bold text-on-accent shadow-pressed"
        >
          {t("apiErrors.retry")}
        </button>
      )}
    </div>
  );
}

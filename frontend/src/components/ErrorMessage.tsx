import { useTranslation } from "react-i18next";
import { ApiError } from "@/api/client";

// Codes with their own message; any other error falls back to UNKNOWN_ERROR
const KNOWN_CODES = ["BUSINESS_NOT_FOUND", "MENU_NOT_FOUND", "NETWORK_ERROR"] as const;
type KnownCode = (typeof KNOWN_CODES)[number];

function isKnownCode(code: string): code is KnownCode {
  return (KNOWN_CODES as readonly string[]).includes(code);
}

interface ErrorMessageProps {
  error: unknown;
  onRetry?: () => void;
}

/** Shows an API error in the user's language, from its code; never the backend's English text */
export default function ErrorMessage({ error, onRetry }: ErrorMessageProps) {
  const { t } = useTranslation();
  const code = error instanceof ApiError && isKnownCode(error.code) ? error.code : "UNKNOWN_ERROR";

  return (
    <div role="alert" className="flex flex-col items-center gap-4 py-16 text-center">
      <p className="text-lg text-muted">{t(`apiErrors.${code}`)}</p>
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

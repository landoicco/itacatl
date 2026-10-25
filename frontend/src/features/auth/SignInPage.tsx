import type { FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link, Navigate, useSearchParams } from "react-router";
import { errorCode } from "@/api/errors";
import { useCurrentUser, useSignIn } from "@/api/session";
import SiteHeader from "@/components/SiteHeader";

const DEFAULT_NEXT = "/manage";

// Only paths of this app: "//evil.example" is a URL on another site, and following it from a
// sign-in page would be an open redirect
function safeNext(next: string | null): string {
  return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\")
    ? next
    : DEFAULT_NEXT;
}

export default function SignInPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const next = safeNext(params.get("next"));
  const { data: user } = useCurrentUser();
  const signIn = useSignIn();

  // Signed in, either just now (useSignIn stores the user) or before, e.g. a bookmarked /login
  if (user) {
    return <Navigate to={next} replace />;
  }

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    signIn.mutate({ email: String(form.get("email")), password: String(form.get("password")) });
  };

  const inputClass =
    "min-h-12 rounded-control border-2 border-soft bg-surface px-4 text-[15px] font-semibold outline-none focus:border-accent";

  return (
    <>
      <SiteHeader />
      <main className="mx-auto flex max-w-[420px] flex-col gap-5 px-5 py-10">
        <form
          onSubmit={submit}
          className="flex flex-col gap-4 rounded-card border-theme bg-surface p-6 shadow-card"
        >
          <div>
            <h1 className="text-display text-[26px]">{t("auth.title")}</h1>
            <p className="mt-1 text-sm text-muted">{t("auth.subtitle")}</p>
          </div>

          {signIn.error && (
            <p role="alert" className="rounded-card bg-soft px-4 py-3 text-sm font-bold text-low">
              {t(`apiErrors.${errorCode(signIn.error)}`)}
            </p>
          )}

          <label className="flex flex-col gap-1.5 text-sm font-bold">
            {t("auth.email")}
            <input
              name="email"
              type="email"
              autoComplete="username"
              required
              className={inputClass}
            />
          </label>
          <label className="flex flex-col gap-1.5 text-sm font-bold">
            {t("auth.password")}
            <input
              name="password"
              type="password"
              autoComplete="current-password"
              required
              className={inputClass}
            />
          </label>

          <button
            type="submit"
            disabled={signIn.isPending}
            className="min-h-12 cursor-pointer rounded-control border-theme bg-accent font-bold text-on-accent shadow-pressed disabled:opacity-60"
          >
            {signIn.isPending ? t("auth.submitting") : t("auth.submit")}
          </button>
        </form>
        <Link to="/" className="text-center text-sm font-bold text-muted">
          ← {t("auth.backToMarketplace")}
        </Link>
      </main>
    </>
  );
}

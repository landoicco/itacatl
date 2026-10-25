import { Navigate, Outlet, useLocation } from "react-router";
import { useCurrentUser } from "@/api/session";
import ErrorMessage from "@/components/ErrorMessage";
import SkeletonCards from "@/components/SkeletonCards";

/**
 * Guards the owner panel: without a session it sends the visitor to the sign-in page, which
 * brings them back here afterwards. Only a convenience for the user: the API checks every call,
 * so a forged client still gets 401 and 403.
 */
export default function RequireSession() {
  const location = useLocation();
  const { data: user, isPending, error, refetch } = useCurrentUser();

  if (isPending) {
    return (
      <main className="mx-auto max-w-[1100px] px-5 py-8">
        <SkeletonCards count={2} height="h-24" />
      </main>
    );
  }
  if (error) {
    return <ErrorMessage error={error} onRetry={() => void refetch()} />;
  }
  if (!user) {
    const next = location.pathname + location.search;
    return <Navigate to={`/login?next=${encodeURIComponent(next)}`} replace />;
  }
  return <Outlet />;
}

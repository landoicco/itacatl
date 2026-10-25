import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query";
import { Outlet, ScrollRestoration } from "react-router";
import type { RouteObject } from "react-router";
import { ApiError } from "@/api/client";
import { queryKeys } from "@/api/queries";
import BusinessPickerPage from "@/features/account/BusinessPickerPage";
import SignInPage from "@/features/auth/SignInPage";
import ShopListPage from "@/features/shops/ShopListPage";
import ShopPage from "@/features/menu/ShopPage";
import NotFoundPage from "./NotFoundPage";
import RequireSession from "./RequireSession";

// Shared by the app and by tests, which mount them in a memory router
export const routes: RouteObject[] = [
  {
    // Every page starts at the top, and Back restores the previous scroll position
    element: (
      <>
        <ScrollRestoration />
        <Outlet />
      </>
    ),
    children: [
      { index: true, element: <ShopListPage /> },
      // ?menu=<id> selects a service day, so a link to tomorrow's menu can be shared
      { path: "shops/:slug", element: <ShopPage /> },
      // ?next=<path> is where to go after signing in
      { path: "login", element: <SignInPage /> },
      {
        path: "manage",
        element: <RequireSession />,
        children: [{ index: true, element: <BusinessPickerPage /> }],
      },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
];

// A 4xx will not change by retrying: a missing menu, a denied request, a broken rule
const isClientError = (error: unknown) =>
  error instanceof ApiError && error.status >= 400 && error.status < 500;

export function createQueryClient() {
  // A session that expires while the panel is open makes any call answer 401. Forgetting the
  // user sends RequireSession to the sign-in page, wherever the 401 came from
  const endSessionOn401 = (error: unknown) => {
    if (error instanceof ApiError && error.code === "AUTHENTICATION_REQUIRED") {
      queryClient.setQueryData(queryKeys.currentUser, null);
    }
  };

  const queryClient = new QueryClient({
    queryCache: new QueryCache({ onError: endSessionOn401 }),
    mutationCache: new MutationCache({ onError: endSessionOn401 }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // Network errors and 5xx might pass on a second try
        retry: (failures, error) => !isClientError(error) && failures < 2,
      },
    },
  });
  return queryClient;
}

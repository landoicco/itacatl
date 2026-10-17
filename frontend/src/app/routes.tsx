import { QueryClient } from "@tanstack/react-query";
import { Outlet, ScrollRestoration } from "react-router";
import type { RouteObject } from "react-router";
import { ApiError } from "@/api/client";
import ShopListPage from "@/features/shops/ShopListPage";
import ShopPage from "@/features/menu/ShopPage";
import NotFoundPage from "./NotFoundPage";

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
      { path: "*", element: <NotFoundPage /> },
    ],
  },
];

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // A missing shop or menu will not appear by retrying; network errors might
        retry: (failures, error) =>
          !(error instanceof ApiError && error.status === 404) && failures < 2,
      },
    },
  });
}

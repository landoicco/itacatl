import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { QueryClient } from "@tanstack/react-query";
import { api } from "./client";
import { queryKeys } from "./queries";
import type {
  AddMenuItemRequest,
  ItemRequest,
  ManagedMenu,
  MenuWindow,
  UpdateMenuItemRequest,
} from "./types";

/*
 * Hooks of the management API, for one business at a time. Every menu change answers with the
 * whole menu, so mutations write it into the cache instead of refetching it; lists that may have
 * changed are invalidated, and so is the public data customers see.
 */

export function useCatalog(slug: string) {
  return useQuery({ queryKey: queryKeys.catalog(slug), queryFn: () => api.getCatalog(slug) });
}

export function useManagedMenus(slug: string) {
  return useQuery({
    queryKey: queryKeys.managedMenus(slug),
    queryFn: () => api.getManagedMenus(slug),
  });
}

export function useManagedMenu(slug: string, menuId: number) {
  return useQuery({
    queryKey: queryKeys.managedMenu(slug, menuId),
    queryFn: () => api.getManagedMenu(slug, menuId),
  });
}

// What customers see may have changed: the marketplace's lists and menus
function invalidatePublic(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: queryKeys.businesses });
  void queryClient.invalidateQueries({ queryKey: ["menus"] });
}

function storeMenu(queryClient: QueryClient, slug: string, menu: ManagedMenu) {
  queryClient.setQueryData(queryKeys.managedMenu(slug, menu.id), menu);
  void queryClient.invalidateQueries({ queryKey: queryKeys.managedMenus(slug), exact: true });
  invalidatePublic(queryClient);
}

/** A change to the catalog: the catalog and every menu showing its items may be stale */
function useCatalogMutation<Args>(slug: string, mutationFn: (args: Args) => Promise<unknown>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.catalog(slug) });
      void queryClient.invalidateQueries({ queryKey: ["manage", slug, "menus"] });
      invalidatePublic(queryClient);
    },
  });
}

/**
 * A change that answers with one menu. Changes to the same menu (`menuId`) share a scope, so
 * TanStack Query runs them one after another: three quick taps on "+" reach the server, and come
 * back, in order, and an older answer never overwrites a newer one in the cache.
 */
function useMenuMutation<Args>(
  slug: string,
  mutationFn: (args: Args) => Promise<ManagedMenu>,
  menuId?: number
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    scope: menuId === undefined ? undefined : { id: `menu:${slug}:${menuId}` },
    onSuccess: (menu) => storeMenu(queryClient, slug, menu),
  });
}

export const useCreateItem = (slug: string) =>
  useCatalogMutation(slug, (item: ItemRequest) => api.createItem(slug, item));

export const useUpdateItem = (slug: string) =>
  useCatalogMutation(slug, ({ itemId, item }: { itemId: number; item: ItemRequest }) =>
    api.updateItem(slug, itemId, item)
  );

export const useArchiveItem = (slug: string) =>
  useCatalogMutation(slug, (itemId: number) => api.archiveItem(slug, itemId));

export const useUnarchiveItem = (slug: string) =>
  useCatalogMutation(slug, (itemId: number) => api.unarchiveItem(slug, itemId));

export const useCreateMenu = (slug: string) =>
  useMenuMutation(slug, (window: MenuWindow) => api.createMenu(slug, window));

export const useDuplicateMenu = (slug: string) =>
  useMenuMutation(slug, ({ menuId, window }: { menuId: number; window: MenuWindow }) =>
    api.duplicateMenu(slug, menuId, window)
  );

export const useRescheduleMenu = (slug: string, menuId: number) =>
  useMenuMutation(slug, (window: MenuWindow) => api.rescheduleMenu(slug, menuId, window), menuId);

export const usePublishMenu = (slug: string) =>
  useMenuMutation(slug, (menuId: number) => api.publishMenu(slug, menuId));

export const useAddMenuItem = (slug: string, menuId: number) =>
  useMenuMutation(slug, (item: AddMenuItemRequest) => api.addMenuItem(slug, menuId, item), menuId);

export const useUpdateMenuItem = (slug: string, menuId: number) =>
  useMenuMutation(
    slug,
    ({ itemId, item }: { itemId: number; item: UpdateMenuItemRequest }) =>
      api.updateMenuItem(slug, menuId, itemId, item),
    menuId
  );

export const useRemoveMenuItem = (slug: string, menuId: number) =>
  useMenuMutation(slug, (itemId: number) => api.removeMenuItem(slug, menuId, itemId), menuId);

export function useDeleteMenu(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (menuId: number) => api.deleteMenu(slug, menuId),
    onSuccess: (_, menuId) => {
      queryClient.removeQueries({ queryKey: queryKeys.managedMenu(slug, menuId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.managedMenus(slug), exact: true });
    },
  });
}

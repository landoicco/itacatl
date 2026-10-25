import type {
  AddMenuItemRequest,
  BusinessDetail,
  BusinessSummary,
  CatalogItem,
  CurrentUser,
  FieldError,
  ItemRequest,
  ManagedMenu,
  MenuDetail,
  MenuWindow,
  SignInRequest,
  UpdateMenuItemRequest,
} from "./types";

// Same-origin path, so one build runs everywhere: each environment routes /api to the backend
// (Vite proxy in dev, nginx in docker compose, CloudFront in AWS)
const API_BASE = "/api";

/**
 * A failed API call. `code` is the backend's stable error code (RFC 9457 Problem Details), which
 * the UI translates; `message` is for developers only.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  /** Per-field problems of a VALIDATION_FAILED error; empty otherwise */
  readonly fieldErrors: FieldError[];

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

type Method = "GET" | "POST" | "PUT" | "DELETE";

// The backend sets this cookie on every response and checks it against a header on every change
// (docs/security.md#csrf-in-a-single-page-app). Read on each request: it is replaced on sign-in
function csrfToken(): string | undefined {
  const cookie = document.cookie.split("; ").find((pair) => pair.startsWith("XSRF-TOKEN="));
  return cookie && decodeURIComponent(cookie.slice("XSRF-TOKEN=".length));
}

async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  const token = method === "GET" ? undefined : csrfToken();
  if (token) {
    headers["X-XSRF-TOKEN"] = token;
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (cause) {
    throw new ApiError(0, "NETWORK_ERROR", `Cannot reach the API: ${String(cause)}`);
  }

  if (!response.ok) {
    // Problem Details carry a code; anything else (a proxy error page) gets a generic one
    const problem = await response.json().catch(() => null);
    throw new ApiError(
      response.status,
      typeof problem?.code === "string" ? problem.code : "UNKNOWN_ERROR",
      problem?.detail ?? `${response.status} ${response.statusText}`,
      Array.isArray(problem?.errors) ? problem.errors : []
    );
  }
  // 204 No Content (sign-out, deleting a draft) has no body to parse
  return (response.status === 204 ? undefined : response.json()) as Promise<T>;
}

const get = <T>(path: string) => request<T>("GET", path);
const slugPath = (slug: string) => `/manage/businesses/${encodeURIComponent(slug)}`;

export const api = {
  getBusinesses: () => get<BusinessSummary[]>("/businesses"),
  getBusiness: (slug: string) => get<BusinessDetail>(`/businesses/${encodeURIComponent(slug)}`),
  getMenu: (id: number) => get<MenuDetail>(`/menus/${id}`),

  /** The signed-in user, or null without a session */
  getCurrentUser: () =>
    get<CurrentUser>("/auth/me").catch((error: unknown) => {
      if (error instanceof ApiError && error.status === 401) {
        return null;
      }
      throw error;
    }),
  signIn: (credentials: SignInRequest) => request<CurrentUser>("POST", "/auth/login", credentials),
  signOut: () => request<void>("POST", "/auth/logout"),

  getCatalog: (slug: string) => get<CatalogItem[]>(`${slugPath(slug)}/items`),
  createItem: (slug: string, item: ItemRequest) =>
    request<CatalogItem>("POST", `${slugPath(slug)}/items`, item),
  updateItem: (slug: string, itemId: number, item: ItemRequest) =>
    request<CatalogItem>("PUT", `${slugPath(slug)}/items/${itemId}`, item),
  archiveItem: (slug: string, itemId: number) =>
    request<CatalogItem>("POST", `${slugPath(slug)}/items/${itemId}/archive`),
  unarchiveItem: (slug: string, itemId: number) =>
    request<CatalogItem>("POST", `${slugPath(slug)}/items/${itemId}/unarchive`),

  /** Menus served on `from` or later; today in the business's zone when omitted */
  getManagedMenus: (slug: string, from?: string) =>
    get<ManagedMenu[]>(`${slugPath(slug)}/menus${from ? `?from=${from}` : ""}`),
  getManagedMenu: (slug: string, menuId: number) =>
    get<ManagedMenu>(`${slugPath(slug)}/menus/${menuId}`),
  createMenu: (slug: string, window: MenuWindow) =>
    request<ManagedMenu>("POST", `${slugPath(slug)}/menus`, window),
  duplicateMenu: (slug: string, menuId: number, window: MenuWindow) =>
    request<ManagedMenu>("POST", `${slugPath(slug)}/menus/${menuId}/duplicate`, window),
  rescheduleMenu: (slug: string, menuId: number, window: MenuWindow) =>
    request<ManagedMenu>("PUT", `${slugPath(slug)}/menus/${menuId}`, window),
  publishMenu: (slug: string, menuId: number) =>
    request<ManagedMenu>("POST", `${slugPath(slug)}/menus/${menuId}/publish`),
  deleteMenu: (slug: string, menuId: number) =>
    request<void>("DELETE", `${slugPath(slug)}/menus/${menuId}`),
  addMenuItem: (slug: string, menuId: number, item: AddMenuItemRequest) =>
    request<ManagedMenu>("POST", `${slugPath(slug)}/menus/${menuId}/items`, item),
  updateMenuItem: (slug: string, menuId: number, itemId: number, item: UpdateMenuItemRequest) =>
    request<ManagedMenu>("PUT", `${slugPath(slug)}/menus/${menuId}/items/${itemId}`, item),
  removeMenuItem: (slug: string, menuId: number, itemId: number) =>
    request<ManagedMenu>("DELETE", `${slugPath(slug)}/menus/${menuId}/items/${itemId}`),
};

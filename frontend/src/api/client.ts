import type { BusinessDetail, BusinessSummary, MenuDetail } from "./types";

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

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

async function getJson<T>(path: string): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { headers: { Accept: "application/json" } });
  } catch (cause) {
    throw new ApiError(0, "NETWORK_ERROR", `Cannot reach the API: ${String(cause)}`);
  }

  if (!response.ok) {
    // Problem Details carry a code; anything else (a proxy error page) gets a generic one
    const problem = await response.json().catch(() => null);
    throw new ApiError(
      response.status,
      typeof problem?.code === "string" ? problem.code : "UNKNOWN_ERROR",
      problem?.detail ?? `${response.status} ${response.statusText}`
    );
  }
  return response.json() as Promise<T>;
}

export const api = {
  getBusinesses: () => getJson<BusinessSummary[]>("/businesses"),
  getBusiness: (slug: string) => getJson<BusinessDetail>(`/businesses/${encodeURIComponent(slug)}`),
  getMenu: (id: number) => getJson<MenuDetail>(`/menus/${id}`),
};

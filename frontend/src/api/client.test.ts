import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { api, ApiError } from "./client";

function respond(status: number, body: unknown, contentType = "application/json") {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(typeof body === "string" ? body : JSON.stringify(body), {
        status,
        headers: { "Content-Type": contentType },
      })
    )
  );
}

describe("api client", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns the parsed body on success", async () => {
    respond(200, [{ slug: "sunny-scoops" }]);

    await expect(api.getBusinesses()).resolves.toEqual([{ slug: "sunny-scoops" }]);
    expect(fetch).toHaveBeenCalledWith("/api/businesses", expect.anything());
  });

  it("turns a Problem Detail into an ApiError with its code", async () => {
    respond(
      404,
      { status: 404, detail: "No business with slug nope", code: "BUSINESS_NOT_FOUND" },
      "application/problem+json"
    );

    const error = await api.getBusiness("nope").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 404, code: "BUSINESS_NOT_FOUND" });
  });

  it("uses a generic code when the error body is not a Problem Detail", async () => {
    respond(502, "<html>Bad Gateway</html>", "text/html");

    await expect(api.getBusinesses()).rejects.toMatchObject({ status: 502, code: "UNKNOWN_ERROR" });
  });

  it("reports a network failure as NETWORK_ERROR", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    await expect(api.getBusinesses()).rejects.toMatchObject({ status: 0, code: "NETWORK_ERROR" });
  });

  it("encodes the slug in the URL", async () => {
    respond(200, {});

    await api.getBusiness("a/b");

    expect(fetch).toHaveBeenCalledWith("/api/businesses/a%2Fb", expect.anything());
  });
});

describe("api client, changes", () => {
  const sentRequest = () => vi.mocked(fetch).mock.calls[0][1] as RequestInit;
  const sentHeaders = () => sentRequest().headers as Record<string, string>;

  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=token%3D1; path=/";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = "XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
  });

  it("sends the CSRF token from the cookie, decoded, with a JSON body", async () => {
    respond(201, { id: 1 });

    await api.createMenu("sunny-scoops", {
      serviceDate: "2026-10-18",
      startsAt: "12:00",
      endsAt: "20:00",
    });

    expect(fetch).toHaveBeenCalledWith(
      "/api/manage/businesses/sunny-scoops/menus",
      expect.anything()
    );
    expect(sentRequest().method).toBe("POST");
    expect(sentHeaders()).toMatchObject({
      "X-XSRF-TOKEN": "token=1",
      "Content-Type": "application/json",
    });
    expect(JSON.parse(sentRequest().body as string)).toEqual({
      serviceDate: "2026-10-18",
      startsAt: "12:00",
      endsAt: "20:00",
    });
  });

  it("does not send the token on reads", async () => {
    respond(200, []);

    await api.getCatalog("sunny-scoops");

    expect(sentHeaders()).not.toHaveProperty("X-XSRF-TOKEN");
  });

  it("resolves an empty 204 response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));

    await expect(api.deleteMenu("sunny-scoops", 3)).resolves.toBeUndefined();
    expect(sentRequest().method).toBe("DELETE");
  });

  it("keeps the field errors of a validation failure", async () => {
    respond(
      400,
      {
        status: 400,
        code: "VALIDATION_FAILED",
        errors: [{ field: "name", code: "NotBlank" }],
      },
      "application/problem+json"
    );

    const error = await api
      .createItem("sunny-scoops", {
        name: "",
        description: null,
        suggestedPrice: 40,
        alcoholic: false,
      })
      .catch((e: unknown) => e);

    expect(error).toMatchObject({
      code: "VALIDATION_FAILED",
      fieldErrors: [{ field: "name", code: "NotBlank" }],
    });
  });

  it("answers null for the current user without a session", async () => {
    respond(401, { status: 401, code: "AUTHENTICATION_REQUIRED" }, "application/problem+json");

    await expect(api.getCurrentUser()).resolves.toBeNull();
  });
});

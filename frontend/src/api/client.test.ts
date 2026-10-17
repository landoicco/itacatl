import { describe, it, expect, vi, afterEach } from "vitest";
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

import { fireEvent, screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderRoute } from "@/test/renderRoute";
import { NOW, server } from "@/test/server";

describe("ShopListPage", () => {
  beforeEach(() => {
    // Only Date is faked: real timers keep promises and React Query working
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(NOW);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("lists every shop with its opening status and next menu's items", async () => {
    renderRoute("/");

    const sunny = await screen.findByRole("link", { name: /Sunny Scoops/ });
    expect(within(sunny).getByText(/Open until 9:00/)).toBeInTheDocument();
    // The first three items, then how many more
    expect(
      within(sunny).getByText(/Chocolate ice cream · Lime sorbet · Passion fruit sorbet \+1/)
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /The Frozen Corner/ })).toHaveAttribute(
      "href",
      "/shops/the-frozen-corner"
    );
  });

  it("searches names and items without caring about case or accents", async () => {
    renderRoute("/");
    await screen.findByText("Sunny Scoops");

    fireEvent.change(screen.getByRole("searchbox"), { target: { value: "VÁNILLA" } });

    expect(screen.getByText("The Frozen Corner")).toBeInTheDocument();
    expect(screen.queryByText("Sunny Scoops")).not.toBeInTheDocument();
  });

  it("filters the shops that are open now", async () => {
    renderRoute("/");
    await screen.findByText("Sunny Scoops");

    fireEvent.click(screen.getByRole("button", { name: "Open now" }));

    expect(screen.getByText("Sunny Scoops")).toBeInTheDocument();
    expect(screen.queryByText("The Frozen Corner")).not.toBeInTheDocument();
  });

  it("shows a translated error with a retry when the API fails", async () => {
    server.use(
      http.get("/api/businesses", () => HttpResponse.json({ status: 500 }, { status: 500 }))
    );
    renderRoute("/");

    expect(await screen.findByRole("alert")).toHaveTextContent("Something went wrong.");
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
  });
});

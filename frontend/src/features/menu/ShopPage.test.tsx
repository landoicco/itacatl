import { fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderRoute } from "@/test/renderRoute";
import { NOW } from "@/test/server";

describe("ShopPage", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(NOW);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows the shop, its weekly hours and today's menu", async () => {
    renderRoute("/shops/sunny-scoops");

    expect(await screen.findByRole("heading", { name: "Sunny Scoops" })).toBeInTheDocument();
    expect(screen.getByText("Pickup · Dine-in")).toBeInTheDocument();
    // The shop's today is Saturday, highlighted in the weekly hours
    expect(screen.getByRole("rowheader", { name: "Saturday" }).closest("tr")).toHaveClass(
      "text-accent"
    );

    expect(screen.getByRole("tab", { name: "Today · Sat 17" })).toHaveAttribute(
      "aria-selected",
      "true"
    );
    expect(await screen.findByText("Lime sorbet")).toBeInTheDocument();
    expect(screen.getByText("Only 3 left")).toBeInTheDocument();
    expect(screen.getByText("Sold out")).toBeInTheDocument();
    expect(screen.getByText("Contains alcohol")).toBeInTheDocument();
  });

  it("switches to another service day and keeps it in the URL", async () => {
    const { router } = renderRoute("/shops/sunny-scoops");

    fireEvent.click(await screen.findByRole("tab", { name: "Tomorrow · Sun 18" }));

    expect(await screen.findByText("Coconut ice cream")).toBeInTheDocument();
    expect(router.state.location.search).toBe("?menu=2");
  });

  it("opens the service day of a shared link", async () => {
    renderRoute("/shops/sunny-scoops?menu=2");

    expect(await screen.findByRole("tab", { name: "Tomorrow · Sun 18" })).toHaveAttribute(
      "aria-selected",
      "true"
    );
    expect(await screen.findByText("Coconut ice cream")).toBeInTheDocument();
  });

  it("explains in the user's language that a shop does not exist", async () => {
    renderRoute("/shops/nope");

    expect(await screen.findByRole("alert")).toHaveTextContent("This shop does not exist.");
  });

  it("shows the not found page for unknown URLs", async () => {
    renderRoute("/nowhere");

    expect(await screen.findByRole("heading", { name: "Page not found" })).toBeInTheDocument();
  });
});

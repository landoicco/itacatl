import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderRoute } from "@/test/renderRoute";
import { NOW, signInAs } from "@/test/server";

const day = (name: string) => screen.getByRole("region", { name });

describe("MenusPage", () => {
  beforeEach(() => {
    // Saturday 17 October, 11:30 in Mexico City; only Date is faked
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(NOW);
    signInAs("ana@example.com");
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("lists menus by day in the business's time zone, drafts included", async () => {
    renderRoute("/manage/sunny-scoops/menus");

    expect(await screen.findByRole("region", { name: "Today · Sat 17" })).toHaveTextContent(
      "Published"
    );
    expect(day("Today · Sat 17")).toHaveTextContent("2 items · 1 sold out");
    expect(day("Tomorrow · Sun 18")).toHaveTextContent("Draft1 item · not visible to customers");
    expect(day("Wed 21")).toHaveTextContent("Empty · add items before publishing");
    expect(within(day("Today · Sat 17")).getByRole("link")).toHaveAttribute(
      "href",
      "/manage/sunny-scoops/menus/201"
    );
  });

  it("publishes a draft, and explains why an empty one cannot be", async () => {
    renderRoute("/manage/sunny-scoops/menus");

    fireEvent.click(
      within(await screen.findByRole("region", { name: "Tomorrow · Sun 18" })).getByRole("button", {
        name: "Publish",
      })
    );
    await waitFor(() => expect(day("Tomorrow · Sun 18")).toHaveTextContent("Published"));

    fireEvent.click(within(day("Wed 21")).getByRole("button", { name: "Publish" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Add at least one item before publishing."
    );
  });

  it("duplicates a menu to the next day with the same window", async () => {
    renderRoute("/manage/sunny-scoops/menus");

    fireEvent.click(
      within(await screen.findByRole("region", { name: "Today · Sat 17" })).getByRole("button", {
        name: "Duplicate",
      })
    );
    const sheet = screen.getByRole("dialog", { name: "Duplicate the menu of Today · Sat 17" });
    expect(within(sheet).getByLabelText("Date")).toHaveValue("2026-10-18");
    expect(within(sheet).getByLabelText("Opens")).toHaveValue("11:00");
    expect(within(sheet).getByLabelText("Closes")).toHaveValue("21:00");

    fireEvent.click(within(sheet).getByRole("button", { name: "Create draft" }));

    // Tomorrow now has the original draft and the copy, which brought both items
    await waitFor(() =>
      expect(within(day("Tomorrow · Sun 18")).getAllByRole("article")).toHaveLength(2)
    );
    expect(day("Tomorrow · Sun 18")).toHaveTextContent("2 items · 1 sold out");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("keeps the new menu sheet open with the backend's rule error", async () => {
    renderRoute("/manage/sunny-scoops/menus");
    fireEvent.click(await screen.findByRole("button", { name: /New menu/ }));

    const sheet = screen.getByRole("dialog", { name: "New menu" });
    expect(within(sheet).getByLabelText("Date")).toHaveValue("2026-10-17");
    fireEvent.change(within(sheet).getByLabelText("Opens"), { target: { value: "20:00" } });
    fireEvent.change(within(sheet).getByLabelText("Closes"), { target: { value: "12:00" } });
    fireEvent.click(within(sheet).getByRole("button", { name: "Create draft" }));

    expect(await within(sheet).findByRole("alert")).toHaveTextContent(
      "The closing time must be after the opening time."
    );
  });

  it("deletes a draft after confirming", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    renderRoute("/manage/sunny-scoops/menus");

    fireEvent.click(
      within(await screen.findByRole("region", { name: "Wed 21" })).getByRole("button", {
        name: "Delete",
      })
    );

    await waitFor(() => expect(screen.queryByRole("region", { name: "Wed 21" })).toBeNull());
    expect(window.confirm).toHaveBeenCalledWith(
      "Delete the draft for Wed 21? This cannot be undone."
    );
  });

  it("offers no changes to staff", async () => {
    signInAs("sofia@example.com");
    renderRoute("/manage/sunny-scoops/menus");

    await screen.findByRole("region", { name: "Today · Sat 17" });
    expect(screen.queryByRole("button", { name: /New menu/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Publish" })).not.toBeInTheDocument();
  });

  it("does not offer to delete a published menu", async () => {
    renderRoute("/manage/sunny-scoops/menus");

    const today = await screen.findByRole("region", { name: "Today · Sat 17" });
    expect(within(today).queryByRole("button", { name: "Delete" })).not.toBeInTheDocument();
    expect(within(today).queryByRole("button", { name: "Publish" })).not.toBeInTheDocument();
  });
});

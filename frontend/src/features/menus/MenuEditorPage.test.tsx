import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderRoute } from "@/test/renderRoute";
import { NOW, server, signInAs } from "@/test/server";

// Tomorrow's draft (202) has Lime sorbet, 20 portions at $40; today's published menu (201) has
// Lime sorbet and a sold-out Maracuyá sorbet
const row = (name: string) => screen.getByRole("listitem", { name });

describe("MenuEditorPage", () => {
  beforeEach(() => {
    // Saturday 17 October, 11:30 in Mexico City; only Date is faked
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(NOW);
    signInAs("ana@example.com");
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows a draft and publishes it", async () => {
    renderRoute("/manage/sunny-scoops/menus/202");

    expect(await screen.findByRole("heading", { name: "Tomorrow · Sun 18" })).toBeInTheDocument();
    expect(screen.getByText("Customers can't see this menu yet.")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(await screen.findByText("Published")).toBeInTheDocument();
    expect(screen.queryByText("Customers can't see this menu yet.")).not.toBeInTheDocument();
  });

  it("saves portions with every tap, in order", async () => {
    renderRoute("/manage/sunny-scoops/menus/202");
    const more = await screen.findByRole("button", { name: "One more portion of Lime sorbet" });

    fireEvent.click(more);
    fireEvent.click(more);
    fireEvent.click(more);

    // The three PUTs run one after another, so the last answer is the last tap
    const portions = within(row("Lime sorbet")).getByLabelText("Portions of Lime sorbet");
    expect(portions).toHaveValue(23);
    await waitFor(() => expect(portions).toHaveValue(23));
  });

  it("saves the price when leaving the field, and marks an item sold out in one tap", async () => {
    renderRoute("/manage/sunny-scoops/menus/202");
    const price = await screen.findByLabelText("Price of Lime sorbet");

    fireEvent.change(price, { target: { value: "38" } });
    fireEvent.blur(price);
    fireEvent.click(within(row("Lime sorbet")).getByRole("button", { name: "Sold out" }));

    expect(await within(row("Lime sorbet")).findByText("Sold out")).toBeInTheDocument();
    expect(screen.getByLabelText("Portions of Lime sorbet")).toHaveValue(0);
    expect(screen.getByLabelText("Price of Lime sorbet")).toHaveValue(38);
  });

  it("puts back the saved value when the backend rejects a change", async () => {
    server.use(
      http.put("/api/manage/businesses/:slug/menus/:id/items/:itemId", () =>
        HttpResponse.json(
          { status: 422, code: "MENU_ENDED" },
          { status: 422, headers: { "Content-Type": "application/problem+json" } }
        )
      )
    );
    renderRoute("/manage/sunny-scoops/menus/202");

    fireEvent.click(await screen.findByRole("button", { name: "One more portion of Lime sorbet" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This menu has already ended, so it can't be changed."
    );
    expect(screen.getByLabelText("Portions of Lime sorbet")).toHaveValue(20);
  });

  it("adds items from the catalog at their suggested price", async () => {
    renderRoute("/manage/sunny-scoops/menus/202");
    fireEvent.click(await screen.findByRole("button", { name: /Add from catalog/ }));

    const sheet = screen.getByRole("dialog", { name: "Add from catalog" });
    expect(await within(sheet).findByRole("listitem", { name: "Lime sorbet" })).toHaveTextContent(
      "Already in this menu"
    );
    // Archived items cannot be offered, so they are not listed
    expect(within(sheet).queryByRole("listitem", { name: "Old flavor" })).toBeNull();

    fireEvent.change(within(sheet).getByRole("searchbox"), { target: { value: "maracuya" } });
    const maracuya = within(sheet).getByRole("listitem", { name: "Maracuyá sorbet" });
    expect(within(sheet).queryByRole("listitem", { name: "Lime sorbet" })).toBeNull();
    fireEvent.change(within(maracuya).getByLabelText("Portions of Maracuyá sorbet"), {
      target: { value: "15" },
    });
    fireEvent.click(within(maracuya).getByRole("button", { name: "Add" }));

    // The sheet stays open to add more, and the menu has the item behind it
    expect(await within(maracuya).findByText("Already in this menu")).toBeInTheDocument();
    const added = screen.getAllByRole("listitem", { name: "Maracuyá sorbet" })[0];
    expect(within(added).getByLabelText("Price of Maracuyá sorbet")).toHaveValue(45);
    expect(within(added).getByLabelText("Portions of Maracuyá sorbet")).toHaveValue(15);
  });

  it("removes an item", async () => {
    renderRoute("/manage/sunny-scoops/menus/201");

    fireEvent.click(await screen.findByRole("button", { name: "Remove Lime sorbet" }));

    await waitFor(() => expect(screen.queryByRole("listitem", { name: "Lime sorbet" })).toBeNull());
    expect(row("Maracuyá sorbet")).toBeInTheDocument();
  });

  it("reschedules a draft", async () => {
    renderRoute("/manage/sunny-scoops/menus/202");
    fireEvent.click(await screen.findByRole("button", { name: "Change date and time" }));

    const sheet = screen.getByRole("dialog", { name: "Change date and time" });
    expect(within(sheet).getByLabelText("Date")).toHaveValue("2026-10-18");
    fireEvent.change(within(sheet).getByLabelText("Date"), { target: { value: "2026-10-20" } });
    fireEvent.click(within(sheet).getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("heading", { name: "Tue 20" })).toBeInTheDocument();
  });

  it("does not offer to reschedule a published menu", async () => {
    renderRoute("/manage/sunny-scoops/menus/201");

    await screen.findByRole("heading", { name: "Today · Sat 17" });
    expect(screen.queryByRole("button", { name: "Change date and time" })).not.toBeInTheDocument();
    // Its items still change: stock runs out during the day
    expect(screen.getByLabelText("Portions of Lime sorbet")).toBeInTheDocument();
  });

  it("shows an ended menu as history, without controls", async () => {
    server.use(
      http.get("/api/manage/businesses/:slug/menus/:id", () =>
        HttpResponse.json({
          id: 200,
          serviceDate: "2026-10-16",
          startsAt: "11:00:00",
          endsAt: "21:00:00",
          status: "PUBLISHED",
          items: [{ itemId: 101, name: "Lime sorbet", price: 40, quantity: 5, archived: false }],
        })
      )
    );
    renderRoute("/manage/sunny-scoops/menus/200");

    expect(
      await screen.findByText("This menu has ended. It stays as history and can't be changed.")
    ).toBeInTheDocument();
    expect(row("Lime sorbet")).toHaveTextContent("5 portions");
    expect(screen.queryByRole("button", { name: "Remove Lime sorbet" })).not.toBeInTheDocument();
  });

  it("lets staff read a menu without changing it", async () => {
    signInAs("sofia@example.com");
    renderRoute("/manage/sunny-scoops/menus/201");

    expect(await screen.findByRole("listitem", { name: "Maracuyá sorbet" })).toHaveTextContent(
      "Sold out"
    );
    expect(screen.queryByLabelText("Portions of Lime sorbet")).not.toBeInTheDocument();
  });
});

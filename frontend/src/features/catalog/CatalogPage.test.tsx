import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { renderRoute } from "@/test/renderRoute";
import { server, signInAs } from "@/test/server";

describe("Catalog and the panel around it", () => {
  beforeEach(() => {
    signInAs("ana@example.com");
  });

  it("lists the business's items, with archived ones folded at the end", async () => {
    renderRoute("/manage/sunny-scoops/catalog");

    expect(await screen.findByRole("heading", { name: "Catalog" })).toBeInTheDocument();
    expect(screen.getByText("Lime sorbet")).toBeInTheDocument();
    expect(screen.getByText(/Suggested .*60.* · Contains alcohol/)).toBeInTheDocument();
    expect(screen.getByText("Archived (1)").closest("details")).not.toHaveAttribute("open");
  });

  it("finds items without accents", async () => {
    renderRoute("/manage/sunny-scoops/catalog");
    await screen.findByText("Lime sorbet");

    fireEvent.change(screen.getByRole("searchbox"), { target: { value: "maracuya" } });

    expect(screen.getByText("Maracuyá sorbet")).toBeInTheDocument();
    expect(screen.queryByText("Lime sorbet")).not.toBeInTheDocument();
  });

  it("switches business and stays in the same section", async () => {
    const { router } = renderRoute("/manage/sunny-scoops/catalog");
    await screen.findByText("Lime sorbet");

    fireEvent.change(screen.getByLabelText("Switch business"), {
      target: { value: "hilltop-paletas" },
    });

    expect(await screen.findByText("Your catalog is empty.")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/manage/hilltop-paletas/catalog");
  });

  it("shows the sections as tabs", async () => {
    renderRoute("/manage/sunny-scoops/catalog");
    await screen.findByText("Lime sorbet");

    // The phone's tab bar; the same links sit in the header on wider screens
    const tabBar = screen.getAllByRole("navigation", { name: "Sections" }).at(-1)!;
    expect(within(tabBar).getByRole("link", { name: "Catalog" })).toHaveClass("text-accent");
    expect(within(tabBar).getByRole("link", { name: "Menus" })).toHaveAttribute(
      "href",
      "/manage/sunny-scoops/menus"
    );
  });

  it("sends a business the user does not belong to back to their list", async () => {
    const { router } = renderRoute("/manage/the-frozen-corner/catalog");

    await screen.findByRole("heading", { name: "Hi, Ana" });
    expect(router.state.location.pathname).toBe("/manage");
  });

  it("goes to sign-in when the session expires, and remembers where it was", async () => {
    server.use(
      http.get("/api/manage/businesses/:slug/items", () =>
        HttpResponse.json(
          { status: 401, code: "AUTHENTICATION_REQUIRED" },
          { status: 401, headers: { "Content-Type": "application/problem+json" } }
        )
      )
    );
    const { router } = renderRoute("/manage/sunny-scoops/catalog");

    await screen.findByRole("heading", { name: "Business sign-in" });
    expect(router.state.location.search).toBe("?next=%2Fmanage%2Fsunny-scoops%2Fcatalog");
  });

  it("creates an item, keeping the form when the server rejects a field", async () => {
    renderRoute("/manage/sunny-scoops/catalog");
    fireEvent.click(await screen.findByRole("button", { name: /New item/ }));

    const sheet = screen.getByRole("dialog", { name: "New item" });
    // Only spaces: the browser accepts it, Bean Validation's NotBlank does not
    fireEvent.change(within(sheet).getByLabelText("Name"), { target: { value: "   " } });
    fireEvent.change(within(sheet).getByLabelText("Suggested price"), { target: { value: "50" } });
    fireEvent.click(within(sheet).getByRole("button", { name: "Save item" }));

    expect(await within(sheet).findByText("This field is required.")).toBeInTheDocument();
    // A screen reader announces the error with the field, not only next to it
    expect(within(sheet).getByLabelText("Name")).toHaveAccessibleDescription(
      "This field is required."
    );
    expect(within(sheet).getByLabelText("Name")).toHaveAttribute("aria-invalid", "true");

    fireEvent.change(within(sheet).getByLabelText("Name"), { target: { value: "Guava sorbet" } });
    fireEvent.click(within(sheet).getByRole("button", { name: "Save item" }));

    expect(await screen.findByRole("listitem", { name: "Guava sorbet" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("edits an item's suggested price", async () => {
    renderRoute("/manage/sunny-scoops/catalog");
    const lime = await screen.findByRole("listitem", { name: "Lime sorbet" });
    fireEvent.click(within(lime).getByRole("button", { name: "Edit" }));

    const sheet = screen.getByRole("dialog", { name: "Edit item" });
    expect(within(sheet).getByLabelText("Name")).toHaveValue("Lime sorbet");
    fireEvent.change(within(sheet).getByLabelText("Suggested price"), { target: { value: "38" } });
    fireEvent.click(within(sheet).getByRole("button", { name: "Save item" }));

    await waitFor(() =>
      expect(screen.getByRole("listitem", { name: "Lime sorbet" })).toHaveTextContent("38.00")
    );
  });

  it("archives and unarchives an item", async () => {
    renderRoute("/manage/sunny-scoops/catalog");
    const lime = await screen.findByRole("listitem", { name: "Lime sorbet" });

    fireEvent.click(within(lime).getByRole("button", { name: "Archive" }));
    expect(await screen.findByText("Archived (2)")).toBeInTheDocument();

    const archived = screen.getByRole("listitem", { name: "Lime sorbet" });
    fireEvent.click(within(archived).getByRole("button", { name: "Unarchive" }));
    expect(await screen.findByText("Archived (1)")).toBeInTheDocument();
  });

  it("lets staff read the catalog without changing it", async () => {
    signInAs("sofia@example.com");
    renderRoute("/manage/sunny-scoops/catalog");

    await screen.findByText("Lime sorbet");
    expect(screen.queryByRole("button", { name: /New item/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
  });

  it("signs out to the marketplace", async () => {
    const { router } = renderRoute("/manage/sunny-scoops/catalog");
    await screen.findByText("Lime sorbet");

    fireEvent.click(screen.getByRole("button", { name: "Sign out" }));

    await waitFor(() => expect(router.state.location.pathname).toBe("/"));
  });
});

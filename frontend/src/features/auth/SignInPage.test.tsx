import { fireEvent, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { renderRoute } from "@/test/renderRoute";
import { PASSWORD, signInAs } from "@/test/server";

function signIn(email: string, password = PASSWORD) {
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: email } });
  fireEvent.change(screen.getByLabelText("Password"), { target: { value: password } });
  fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
}

describe("Signing in to the owner panel", () => {
  beforeEach(() => {
    signInAs(null);
  });

  it("sends a visitor without a session to the sign-in page, and back afterwards", async () => {
    const { router } = renderRoute("/manage");

    expect(await screen.findByRole("heading", { name: "Business sign-in" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
    expect(router.state.location.search).toBe("?next=%2Fmanage");

    signIn("ana@example.com");

    // Ana has two shops, so the panel asks which one
    expect(await screen.findByRole("heading", { name: "Hi, Ana" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/manage");
    expect(screen.getByRole("link", { name: /Sunny Scoops/ })).toHaveAttribute(
      "href",
      "/manage/sunny-scoops/menus"
    );
    expect(screen.getAllByText("Owner")).toHaveLength(2);
  });

  it("shows one message for any wrong credentials", async () => {
    renderRoute("/login");
    await screen.findByRole("heading", { name: "Business sign-in" });

    signIn("ana@example.com", "wrong");

    expect(await screen.findByRole("alert")).toHaveTextContent("Email or password is incorrect.");
  });

  it("goes straight into the only business", async () => {
    const { router } = renderRoute("/login");
    await screen.findByRole("heading", { name: "Business sign-in" });

    signIn("luis@example.com");

    await waitFor(() =>
      expect(router.state.location.pathname).toBe("/manage/the-frozen-corner/menus")
    );
  });

  it("never follows a next link to another site", async () => {
    const { router } = renderRoute("/login?next=//evil.example");
    await screen.findByRole("heading", { name: "Business sign-in" });

    signIn("ana@example.com");

    await screen.findByRole("heading", { name: "Hi, Ana" });
    expect(router.state.location.pathname).toBe("/manage");
  });

  it("skips the form when already signed in", async () => {
    signInAs("ana@example.com");
    const { router } = renderRoute("/login");

    await screen.findByRole("heading", { name: "Hi, Ana" });
    expect(router.state.location.pathname).toBe("/manage");
  });
});

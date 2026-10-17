import { render, screen } from "@testing-library/react";
import { describe, it, expect, afterEach } from "vitest";
import i18n from "@/i18n";
import OpeningStatusBadge from "./OpeningStatusBadge";

const ZONE = "America/Mexico_City";
// Saturday 17 October 2026, 11:30 in Mexico City
const NOW = new Date("2026-10-17T17:30:00Z");

function renderBadge(status: Parameters<typeof OpeningStatusBadge>[0]["status"]) {
  render(<OpeningStatusBadge status={status} timeZone={ZONE} now={NOW} />);
}

// Matches the badge itself, not its containers; ICU may use a narrow no-break space before AM/PM
const text = (content: string) => (_: string, node: Element | null) =>
  node?.tagName === "SPAN" && node.textContent?.replace(/\s/g, " ") === content;

describe("OpeningStatusBadge", () => {
  afterEach(async () => {
    await i18n.changeLanguage("en");
  });

  it("says until when an open shop stays open", () => {
    renderBadge({ state: "OPEN", closesAt: "2026-10-17T21:00:00", opensAt: null });
    expect(screen.getByText(text("Open until 9:00 PM"))).toBeInTheDocument();
  });

  it("says today, tomorrow or the day of the next opening, in the shop's time zone", () => {
    renderBadge({ state: "CLOSED", closesAt: null, opensAt: "2026-10-17T12:00:00" });
    renderBadge({ state: "CLOSED", closesAt: null, opensAt: "2026-10-18T12:00:00" });
    renderBadge({ state: "CLOSED", closesAt: null, opensAt: "2026-10-20T12:00:00" });

    expect(screen.getByText(text("Opens at 12:00 PM"))).toBeInTheDocument();
    expect(screen.getByText(text("Opens tomorrow at 12:00 PM"))).toBeInTheDocument();
    expect(screen.getByText(text("Opens Tue 20 at 12:00 PM"))).toBeInTheDocument();
  });

  it("has no date when there is no opening ahead", () => {
    renderBadge({ state: "CLOSED", closesAt: null, opensAt: null });
    expect(screen.getByText("Closed for now")).toBeInTheDocument();
  });

  it("uses Spanish 'a la' for one o'clock and 'a las' otherwise", async () => {
    await i18n.changeLanguage("es-MX");

    renderBadge({ state: "CLOSED", closesAt: null, opensAt: "2026-10-17T13:00:00" });
    renderBadge({ state: "CLOSED", closesAt: null, opensAt: "2026-10-18T12:00:00" });

    expect(screen.getByText(text("Abre a la 1:00 p.m."))).toBeInTheDocument();
    expect(screen.getByText(text("Abre mañana a las 12:00 p.m."))).toBeInTheDocument();
  });
});

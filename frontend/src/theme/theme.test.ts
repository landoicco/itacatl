import { describe, it, expect } from "vitest";
import { itemColor } from "./itemColor";
import { applyTheme, resolveTheme, THEMES } from "./theme";

describe("itemColor", () => {
  it("is stable for the same item, ignoring case and spaces", () => {
    expect(itemColor("Lime sorbet")).toBe(itemColor("  lime SORBET "));
  });

  it("always points to one of the theme's palette variables", () => {
    for (const name of ["Lime sorbet", "Chocolate ice cream", "Nieve de maracuyá", ""]) {
      expect(itemColor(name)).toMatch(/^var\(--theme-item-[0-7]\)$/);
    }
  });

  it("spreads different items over the palette", () => {
    const names = ["Lime", "Strawberry", "Chocolate", "Mango", "Coconut", "Vanilla", "Pecan"];
    expect(new Set(names.map(itemColor)).size).toBeGreaterThan(3);
  });
});

describe("theme", () => {
  it("defaults to pastel", () => {
    expect(resolveTheme("")).toBe("pastel");
  });

  it("accepts a known theme from the URL in development", () => {
    expect(resolveTheme("?theme=market")).toBe("market");
  });

  it("ignores unknown themes", () => {
    expect(resolveTheme("?theme=neon")).toBe("pastel");
  });

  it("sets the theme on the html element", () => {
    applyTheme("market");
    expect(document.documentElement.dataset.theme).toBe("market");
  });

  it("declares decorations for every theme", () => {
    for (const theme of Object.values(THEMES)) {
      expect(theme.decorations).toHaveProperty("papelPicado");
    }
  });
});

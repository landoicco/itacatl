/**
 * Themes are the visual part of a vertical's configuration: the deployment picks one at build
 * time, like the default locale. Colors, fonts and shapes live in themes.css; this registry only
 * holds what CSS cannot express, such as decorative components. See docs/theming.md.
 */
export const THEMES = {
  pastel: { decorations: { papelPicado: false } },
  market: { decorations: { papelPicado: true } },
} as const;

export type Theme = keyof typeof THEMES;

function isTheme(value: unknown): value is Theme {
  return typeof value === "string" && value in THEMES;
}

const DEFAULT_THEME: Theme = isTheme(import.meta.env.VITE_THEME)
  ? import.meta.env.VITE_THEME
  : "pastel";

/**
 * The deployment's theme. In development, `?theme=market` overrides it, to compare themes or
 * take screenshots; production builds ignore the parameter, because a theme is not a user setting.
 */
export function resolveTheme(search = window.location.search): Theme {
  if (import.meta.env.DEV) {
    const requested = new URLSearchParams(search).get("theme");
    if (isTheme(requested)) return requested;
  }
  return DEFAULT_THEME;
}

export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme;
}

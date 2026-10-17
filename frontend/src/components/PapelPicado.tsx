import { THEMES, resolveTheme } from "@/theme/theme";

const FLAGS = 16;

/**
 * The papel picado banner of the market theme: a row of cut paper flags. Only themes that
 * declare it render it; its colors come from the theme's palette.
 */
export default function PapelPicado() {
  if (!THEMES[resolveTheme()].decorations.papelPicado) return null;

  return (
    <div aria-hidden="true" className="grid h-[30px] grid-cols-8 sm:grid-cols-16">
      {Array.from({ length: FLAGS }, (_, i) => (
        <div
          key={i}
          // Hidden past the 8th flag on a phone, so each flag keeps a readable width
          className={i >= 8 ? "hidden sm:block" : undefined}
          style={{
            background: `var(--theme-scoop-${i % 4})`,
            clipPath: "polygon(0 0, 100% 0, 100% 70%, 50% 100%, 0 70%)",
          }}
        />
      ))}
    </div>
  );
}

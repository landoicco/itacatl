// Must match the number of --theme-scoop-N variables in themes.css
const PALETTE_SIZE = 8;

/**
 * A stable color for a flavor scoop, from the theme's palette. Items have no color of their own,
 * so the name is hashed (FNV-1a): the same flavor always gets the same color, in every visit and
 * every theme slot, without storing anything.
 */
export function flavorColor(name: string): string {
  let hash = 0x811c9dc5;
  for (const char of name.trim().toLowerCase()) {
    hash ^= char.codePointAt(0)!;
    hash = Math.imul(hash, 0x01000193);
  }
  const slot = (hash >>> 0) % PALETTE_SIZE;
  return `var(--theme-scoop-${slot})`;
}

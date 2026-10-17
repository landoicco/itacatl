/// <reference types="vite/client" />

interface ImportMetaEnv {
  // Default UI locale of the deployment, inlined at build time (e.g. "es-MX")
  readonly VITE_DEFAULT_LOCALE?: string;
  // Visual theme of the deployment's vertical, inlined at build time (e.g. "pastel")
  readonly VITE_THEME?: string;
}

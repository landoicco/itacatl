import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import "./theme/fonts";
import "./i18n";
import { applyTheme, resolveTheme } from "./theme/theme";
import App from "./app/App";

// Before the first render, so the page never flashes another theme
applyTheme(resolveTheme());

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>
);

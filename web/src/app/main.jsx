import { createRoot } from "react-dom/client";
import { App } from "./App.jsx";
// Manrope is bundled, so the app looks the same offline and in tests.
import "@fontsource/manrope/400.css";
import "@fontsource/manrope/500.css";
import "@fontsource/manrope/600.css";
import "@fontsource/manrope/700.css";
import "./global.css";
import { AppTheme } from "./AppTheme.jsx";

createRoot(document.getElementById("root")).render(
  <AppTheme>
    <App />
  </AppTheme>,
);

import { isDemo } from "../shared/app-location.js";
import { createRoot } from "react-dom/client";
import { App } from "./App.jsx";
// Manrope is bundled, so the app looks the same offline and in tests.
import "@fontsource/manrope/400.css";
import "@fontsource/manrope/500.css";
import "@fontsource/manrope/600.css";
import "@fontsource/manrope/700.css";
import "./global.css";
import { AppTheme } from "./AppTheme.jsx";

if (isDemo) {
  // Only this client is controlled: the worker scope is /demo/, never the landing page.
  const registration = await navigator.serviceWorker.register(
    "./demo-worker.js",
    { scope: "./", updateViaCache: "none" },
  );
  await navigator.serviceWorker.ready;
  if (!navigator.serviceWorker.controller)
    await new Promise((resolve) =>
      navigator.serviceWorker.addEventListener("controllerchange", resolve, {
        once: true,
      }),
    );
  registration.update().catch(() => {});
}

createRoot(document.getElementById("root")).render(
  <AppTheme>
    <App />
  </AppTheme>,
);

import { createRoot } from "react-dom/client";
import { App } from "./App.jsx";
import "./global.css";
import { AppTheme } from "./AppTheme.jsx";

createRoot(document.getElementById("root")).render(
  <AppTheme>
    <App />
  </AppTheme>,
);

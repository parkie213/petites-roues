import { createTheme, MantineProvider } from "@mantine/core";
import "@mantine/core/styles.layer.css";

const libraryPage =
  location.pathname === "/" ||
  /^\/(notes|quizzes)(\/|$)/.test(location.pathname);

const theme = createTheme({
  primaryColor: "coral",
  primaryShade: libraryPage ? 6 : 4,
  autoContrast: true,
  defaultRadius: "md",
  fontFamily: 'Manrope, "Segoe UI", system-ui, sans-serif',
  colors: {
    coral: [
      "#fff1ed",
      "#fce0d8",
      "#f7beaf",
      "#f19781",
      "#eb765c",
      "#e55e44",
      "#d94b3a",
      "#bd3b2b",
      "#a73124",
      "#90291e",
    ],
    navy: [
      "#edf3f9",
      "#d9e3ef",
      "#afc5de",
      "#83a5cd",
      "#608abd",
      "#4779b4",
      "#356aa8",
      "#285b94",
      "#1e487a",
      "#142d4e",
    ],
    sage: [
      "#f0f7f2",
      "#dfede3",
      "#c4dfcd",
      "#a5ceb4",
      "#7fb98a",
      "#6da784",
      "#508e69",
      "#3c7453",
      "#2e5d42",
      "#244b35",
    ],
    dark: [
      "#e8e6e3",
      "#c8cdd3",
      "#9aa0a8",
      "#56616f",
      "#2a2e35",
      "#252a31",
      "#22272d",
      "#1d2026",
      "#14161a",
      "#101216",
    ],
  },
  headings: { fontWeight: "600" },
});

/** Provides the shared Mantine theme for the application and portalled controls. */
export function AppTheme({ children }) {
  return (
    <MantineProvider
      theme={theme}
      forceColorScheme={libraryPage ? "light" : "dark"}
    >
      {children}
    </MantineProvider>
  );
}

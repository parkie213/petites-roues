import { createTheme, MantineProvider } from "@mantine/core";
import "@mantine/core/styles.layer.css";

const theme = createTheme({
  primaryColor: "sage",
  primaryShade: 4,
  autoContrast: true,
  defaultRadius: "md",
  fontFamily: '"Segoe UI", system-ui, sans-serif',
  colors: {
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
    <MantineProvider theme={theme} forceColorScheme="dark">
      {children}
    </MantineProvider>
  );
}

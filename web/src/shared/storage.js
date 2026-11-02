import { useEffect, useState } from "react";

export function useStoredState(key, fallback) {
  const [storageError, setStorageError] = useState("");
  const [value, setValue] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem(key)) ?? fallback;
    } catch {
      return fallback;
    }
  });
  useEffect(() => {
    try {
      localStorage.setItem(key, JSON.stringify(value));
      setStorageError("");
    } catch {
      setStorageError(
        "Browser storage is unavailable; copy your attempt before leaving.",
      );
    }
  }, [key, value]);
  return [value, setValue, storageError];
}

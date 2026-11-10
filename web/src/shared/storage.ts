import { useEffect, useState } from "react";

/**
 * State kept in local storage under `key`, starting from `fallback` when nothing is stored.
 * Answers the value, its setter and a message for when storage refuses the write.
 */
export function useStoredState<T>(key: string, fallback: T) {
  const [storageError, setStorageError] = useState("");
  const [value, setValue] = useState<T>(() => {
    try {
      return JSON.parse(localStorage.getItem(key) ?? "null") ?? fallback;
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
  return [value, setValue, storageError] as const;
}

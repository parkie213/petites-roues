/** Serialize writes and replace waiting positions with the latest one. Failed writes retry on the next save. */
export function createProgressWriter(
  send: (position: number) => Promise<unknown>,
  onError: (error: unknown) => void,
) {
  let saved: number | null = null;
  let pending: number | null = null;
  let inFlight: Promise<void> | null = null;
  function flush(): Promise<void> {
    if (inFlight) return inFlight;
    inFlight = Promise.resolve()
      .then(async () => {
        while (pending !== null) {
          const position = pending;
          pending = null;
          if (position === saved) continue;
          try {
            await send(position);
            saved = position;
          } catch (error) {
            onError(error);
          }
        }
      })
      .finally(() => {
        inFlight = null;
        if (pending !== null) flush();
      });
    return inFlight;
  }
  return {
    remember(position: number) {
      saved = position;
    },
    save(position: number) {
      if (!Number.isFinite(position) || position < 0) return Promise.resolve();
      pending = Math.round(position * 1000) / 1000;
      return flush();
    },
  };
}

/** Where a title was left, as the server last saved it. */
export interface SavedProgress {
  positionSeconds: number;
  updatedAt?: string | null;
}

/** The tab's own copy of its latest position, `pending` until the server has it. */
export interface ProgressCheckpoint {
  positionSeconds: number;
  updatedAt: string;
  pending: boolean;
}

/** Timestamp links override resume state; absent, blank and invalid parameters use the saved position. */
export function initialPlaybackPosition(search: string, savedPosition: number) {
  const value = new URLSearchParams(search).get("t");
  const timestamp = value?.trim() ? Number(value) : NaN;
  return Number.isFinite(timestamp) && timestamp >= 0
    ? timestamp
    : savedPosition;
}

/** A reload can finish before its final keepalive request; prefer a newer unsaved tab checkpoint. */
export function resumePosition(
  saved: SavedProgress,
  checkpoint: ProgressCheckpoint | null | undefined,
) {
  if (
    checkpoint?.pending &&
    Number.isFinite(checkpoint.positionSeconds) &&
    checkpoint.positionSeconds >= 0 &&
    Number.isFinite(Date.parse(checkpoint.updatedAt)) &&
    (!saved.updatedAt ||
      Date.parse(checkpoint.updatedAt) > Date.parse(saved.updatedAt))
  )
    return checkpoint.positionSeconds;
  return saved.positionSeconds;
}

/** Session storage may be disabled; progress on the server must still work in that case. */
export function readProgressCheckpoint(
  storage: Storage,
  key: string,
): ProgressCheckpoint | null {
  try {
    return JSON.parse(storage.getItem(key) ?? "null");
  } catch {
    return null;
  }
}

export function writeProgressCheckpoint(
  storage: Storage,
  key: string,
  value: ProgressCheckpoint,
) {
  try {
    storage.setItem(key, JSON.stringify(value));
  } catch {
    /* Server saving remains available. */
  }
}

/** Serialize writes and replace waiting positions with the latest one. Failed writes retry on the next save. */
export function createProgressWriter(send, onError) {
  let saved = null;
  let pending = null;
  let inFlight = null;
  function flush() {
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
    remember(position) {
      saved = position;
    },
    save(position) {
      if (!Number.isFinite(position) || position < 0) return Promise.resolve();
      pending = Math.round(position * 1000) / 1000;
      return flush();
    },
  };
}

/** Timestamp links override resume state; absent, blank and invalid parameters use the saved position. */
export function initialPlaybackPosition(search, savedPosition) {
  const value = new URLSearchParams(search).get("t");
  const timestamp = value?.trim() ? Number(value) : NaN;
  return Number.isFinite(timestamp) && timestamp >= 0
    ? timestamp
    : savedPosition;
}

/** A reload can finish before its final keepalive request; prefer a newer unsaved tab checkpoint. */
export function resumePosition(saved, checkpoint) {
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
export function readProgressCheckpoint(storage, key) {
  try {
    return JSON.parse(storage.getItem(key));
  } catch {
    return null;
  }
}

export function writeProgressCheckpoint(storage, key, value) {
  try {
    storage.setItem(key, JSON.stringify(value));
  } catch {
    /* Server saving remains available. */
  }
}

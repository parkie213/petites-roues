import { request } from "../shared/api.ts";
import type { CueView } from "../shared/types.ts";
import type { Split, Times } from "./subtitle-edit.ts";

/**
 * The subtitle editor's writes to a title's review queue at [api]. Each queues an edit for review
 * and answers with the title's lines as they now are (except a timing edit, whose caller already
 * knows them).
 */

/** The title as an edit leaves it: its lines, in the player's flat view. */
export interface Edited {
  cues: CueView[];
}

const json = (method: string, body?: unknown): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  ...(body !== undefined && { body: JSON.stringify(body) }),
});

const line = (api: string, id: string) =>
  `${api}/cues/${encodeURIComponent(id)}`;

/** Moves the line [id] to [times], with the neighbours joined to it, as one timing edit. */
export function saveTimings(
  api: string,
  id: string,
  times: Times,
  moved: readonly (Times & { id: string })[],
) {
  return request(api + "/timings", json("POST", [{ id, ...times }, ...moved]));
}

/** Replaces the line [id] with [body]. */
export const saveLine = (api: string, id: string, body: unknown) =>
  request<Edited>(line(api, id), json("PUT", body));

/** Adds [body] as a new line. */
export const addLine = (api: string, body: unknown) =>
  request<Edited>(api + "/cues", json("POST", body));

/** Splits the line [id] in two, as [split] says. */
export const splitLine = (api: string, id: string, split: Split) =>
  request<Edited>(line(api, id) + "/split", json("POST", split));

/** Deletes the line [id]. */
export const deleteLine = (api: string, id: string) =>
  request<Edited>(line(api, id), json("DELETE"));

/** Leaves [text] for the reviewer on the line [id]. */
export const commentOn = (api: string, id: string, text: string) =>
  request<Edited>(line(api, id) + "/comment", json("POST", { text }));

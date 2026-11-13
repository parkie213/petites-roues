import { sendCommands } from "../shared/api.ts";
import type { CueView } from "../shared/types.ts";
import type { Split, Times } from "./subtitle-edit.ts";

/** Every subtitle action uses the same atomic typed-command acceptance protocol. */
export interface Edited {
  cues: CueView[];
}
export function saveTimings(
  api: string,
  id: string,
  times: Times,
  moved: readonly (Times & { id: string })[],
) {
  return sendCommands<Edited>(api, [
    { type: "timing", edits: [{ id, ...times }, ...moved] },
  ]);
}
export const saveLine = (api: string, id: string, body: unknown) =>
  sendCommands<Edited>(api, [
    { type: "edit-video-cue", cueId: id, edit: body },
  ]);
export const addLine = (api: string, body: unknown) =>
  sendCommands<Edited>(api, [{ type: "add-video-cue", edit: body }]);
export const splitLine = (api: string, id: string, split: Split) =>
  sendCommands<Edited>(api, [{ type: "split-video-cue", cueId: id, split }]);
export const deleteLine = (api: string, id: string) =>
  sendCommands<Edited>(api, [{ type: "delete-video-cue", cueId: id }]);
export const commentOn = (api: string, id: string, text: string) =>
  sendCommands<Edited>(api, [{ type: "comment", cueId: id, text }]);

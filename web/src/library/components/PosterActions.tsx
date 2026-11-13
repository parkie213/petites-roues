import { ActionIcon, Menu, Text } from "@mantine/core";
import { Copy, Ellipsis } from "lucide-react";
import { clock } from "../../player/quiz.js";
import { responseError, titlePath } from "../../shared/api.ts";
import type { PosterEntry, PosterSlot } from "../../shared/types.ts";
import type { LinkActions } from "../browser.ts";
import { openHref } from "../cards.js";
import { unlinkMedia } from "../media-links.js";

/**
 * Lists every copy of a slot (repository · reference duration) so a specific one can be opened.
 * Different cuts of a video have different durations.
 */
export function PosterCopies({
  slot,
  repositoryNames,
}: {
  slot: PosterSlot;
  repositoryNames: Map<string, string>;
}) {
  if (slot.copies.length < 2) return null;
  return (
    <Menu position="bottom-end" withinPortal>
      <Menu.Target>
        <ActionIcon
          variant="subtle"
          color="gray"
          size="sm"
          aria-label={`${slot.copies.length} copies of ${slot.label}`}
          title={`${slot.copies.length} copies`}
        >
          <Copy size={14} />
        </ActionIcon>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Label>{slot.copies.length} copies</Menu.Label>
        {slot.copies.map((copy) => {
          const href = openHref(copy);
          return (
            <Menu.Item
              key={copy.id}
              component={href ? "a" : "button"}
              href={href ?? undefined}
              disabled={!href}
            >
              <Text size="sm">
                {repositoryNames.get(copy.repositoryId) ?? "Standalone"}
              </Text>
              <Text size="xs" c="dimmed">
                {[
                  copy.durationSeconds != null && clock(copy.durationSeconds),
                  copy.unavailable?.title,
                ]
                  .filter(Boolean)
                  .join(" · ") || "Available"}
              </Text>
            </Menu.Item>
          );
        })}
      </Menu.Dropdown>
    </Menu>
  );
}

/** Forgets a title's resume position: its comic page or its video position. */
async function clearProgress(entry: PosterEntry) {
  const url = titlePath(entry.id) + "/progress";
  const response = await fetch(url, { method: "DELETE" });
  if (!response.ok) throw await responseError(response);
}

/**
 * A card's own actions; shown only when there is something to do: link media without a study
 * document to one by hand, unlink a title's hand-linked media, or clear progress.
 */
export function PosterActions({
  slot,
  onChanged,
  onLink,
}: LinkActions & { slot: PosterSlot }) {
  const entry = slot.entry;
  // Entries for bare or hand-linked media carry their owning repository and path.
  const unlinked = Boolean(entry.fingerprint);
  const handLinked = entry.linkedByHand;
  const progress = (entry.progress ?? 0) > 0;
  if (!unlinked && !handLinked && !progress) return null;
  const done = (promise: Promise<unknown>) =>
    promise.then(
      () => onChanged(),
      (error) => onChanged(error),
    );
  return (
    <Menu position="bottom-end" withinPortal>
      <Menu.Target>
        <ActionIcon
          variant="subtle"
          color="gray"
          size="sm"
          aria-label={`More options for ${slot.label}`}
          title="More options"
        >
          <Ellipsis size={14} />
        </ActionIcon>
      </Menu.Target>
      <Menu.Dropdown>
        {unlinked && (
          <Menu.Item
            onClick={() =>
              onLink({
                media: {
                  repositoryId: entry.mediaRepositoryId!,
                  relativePath: entry.mediaPath!,
                  kind: entry.type,
                  name: slot.label,
                },
              })
            }
          >
            Link to study document…
          </Menu.Item>
        )}
        {handLinked && (
          <Menu.Item
            onClick={() =>
              done(
                unlinkMedia({
                  repositoryId: entry.mediaRepositoryId!,
                  relativePath: entry.mediaPath!,
                }),
              )
            }
          >
            Unlink {entry.type === "comic" ? "CBZ" : "video"} (linked by hand)
          </Menu.Item>
        )}
        {progress && (
          <Menu.Item onClick={() => done(clearProgress(entry))}>
            Clear progress
          </Menu.Item>
        )}
      </Menu.Dropdown>
    </Menu>
  );
}

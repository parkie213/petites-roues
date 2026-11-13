import { isDemo } from "../../shared/app-location.js";
import { ActionIcon, Menu, Text } from "@mantine/core";
import { MoreHorizontal, RefreshCw, Trash2 } from "lucide-react";
import type { ContentRepository } from "../../shared/types.ts";

/** Actions on one registered folder; the library owns refresh jobs and removal confirmation. */
export function RepositoryMenu({
  repo,
  busy,
  refresh,
  onRemove,
}: {
  repo: Pick<ContentRepository, "id" | "name" | "localPath">;
  busy: boolean;
  refresh: (id: string) => void;
  onRemove: (id: string) => void;
}) {
  return (
    <Menu shadow="md" width={260} position="bottom-end">
      <Menu.Target>
        <ActionIcon
          variant="default"
          size="lg"
          aria-label={"Options for " + repo.name}
        >
          <MoreHorizontal size={19} />
        </ActionIcon>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Label>Folder</Menu.Label>
        <Text
          size="xs"
          c="dimmed"
          px="sm"
          pb="sm"
          style={{ overflowWrap: "anywhere" }}
        >
          {repo.localPath}
        </Text>
        <Menu.Item
          disabled={isDemo || busy}
          leftSection={<RefreshCw size={15} />}
          onClick={() => refresh(repo.id)}
        >
          Refresh folder
        </Menu.Item>
        <Menu.Divider />
        <Menu.Item
          disabled={isDemo || busy}
          color="red"
          leftSection={<Trash2 size={15} />}
          onClick={() => onRemove(repo.id)}
        >
          Remove from library…
        </Menu.Item>
      </Menu.Dropdown>
    </Menu>
  );
}

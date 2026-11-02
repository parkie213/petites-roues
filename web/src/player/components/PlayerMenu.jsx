import { useState } from "react";
import { Button, Popover, Stack } from "@mantine/core";
import { ChevronDown } from "lucide-react";

/**
 * A mixed-control popover with managed focus and fullscreen-aware portals.
 * Keep child effects mounted while hidden so temporary comparison tracks survive dismissal.
 */
export function PlayerMenu({ label, children, color }) {
  const [opened, setOpened] = useState(false);
  return (
    <Popover
      keepMounted
      keepMountedMode="display-none"
      opened={opened}
      onChange={setOpened}
      width={300}
      position="bottom-end"
      shadow="md"
      trapFocus
      returnFocus
      portalProps={{ target: document.fullscreenElement || document.body }}
    >
      <Popover.Target>
        <Button
          size="xs"
          variant={color ? "light" : "default"}
          color={color}
          rightSection={<ChevronDown size={13} />}
          aria-expanded={opened}
          onClick={() => setOpened((value) => !value)}
        >
          {label}
        </Button>
      </Popover.Target>
      <Popover.Dropdown
        style={{ maxHeight: "min(70dvh, 500px)", overflowY: "auto" }}
        onClick={(event) => {
          if (
            event.target.closest("button") &&
            !event.target.closest(".keep-menu-open")
          )
            setOpened(false);
        }}
      >
        <Stack gap="sm">{children}</Stack>
      </Popover.Dropdown>
    </Popover>
  );
}

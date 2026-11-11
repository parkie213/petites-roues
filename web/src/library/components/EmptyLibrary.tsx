import { Button, Paper, Stack, Text, Title } from "@mantine/core";
import { Plus } from "lucide-react";

/** A library with no folders yet: what a folder may hold, and the way to add one. */
export function EmptyLibrary({
  working,
  onAdd,
}: {
  working: boolean;
  onAdd: () => void;
}) {
  return (
    <Paper withBorder p={{ base: "xl", sm: 64 }} radius="lg">
      <Stack align="center" gap="md" ta="center">
        <Title order={2}>There's nothing here</Title>
        <Text c="dimmed" maw={460}>
          Add a folder of videos, comics or study documents, or one holding all
          three. Videos link to their study documents by fingerprint, wherever
          each one lives.
        </Text>
        <Button
          mt="sm"
          leftSection={<Plus size={17} />}
          disabled={working}
          onClick={onAdd}
        >
          Add folder
        </Button>
      </Stack>
    </Paper>
  );
}

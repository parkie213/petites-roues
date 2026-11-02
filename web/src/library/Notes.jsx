import { useEffect, useState } from "react";
import {
  Alert,
  Anchor,
  Box,
  Container,
  Group,
  Loader,
  Stack,
  Text,
  Title,
  Typography,
  UnstyledButton,
} from "@mantine/core";
import { ArrowLeft, NotebookText, TriangleAlert } from "lucide-react";
import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { request } from "../shared/api.js";
import { findNote, noteGroups, notePath } from "./notes.js";
import "./Notes.css";

/**
 * The study notes: the list at /notes, or one note at /notes/{repository}/{id} rendered from its
 * Markdown (GitHub-flavoured, so tables work; raw HTML is not rendered). Notes are read from the
 * repositories' files each time the page loads.
 */
export function Notes({ repository, id }) {
  const [notes, setNotes] = useState(null);
  const [error, setError] = useState("");
  useEffect(() => {
    request("/api/notes").then(setNotes, (e) => setError(e.message));
  }, []);
  const note = id && notes ? findNote(notes, repository, id) : null;
  useEffect(() => {
    document.title = note ? `${note.title} · Notes` : "Notes";
  }, [note]);
  return (
    <Box component="main" className="mantine-library">
      <Container size="md" py={{ base: 28, sm: 48 }}>
        <Stack gap="lg">
          <Anchor href={id ? "/notes" : "/"} c="dimmed" size="sm">
            <Group gap={6}>
              <ArrowLeft size={16} />
              {id ? "Notes" : "Library"}
            </Group>
          </Anchor>
          {error && (
            <Alert color="red" icon={<TriangleAlert size={18} />} role="alert">
              {error}
            </Alert>
          )}
          {!notes && !error && <Loader size="sm" />}
          {notes && !id && <NoteList notes={notes} />}
          {notes && id && !note && (
            <Text>
              This note is gone: its file may have been renamed or removed.
            </Text>
          )}
          {note && (
            <Typography className="note-page">
              <Markdown remarkPlugins={[remarkGfm]}>{note.text}</Markdown>
            </Typography>
          )}
        </Stack>
      </Container>
    </Box>
  );
}

function NoteList({ notes }) {
  return (
    <Stack gap="lg">
      <Title order={1} size={32} lts={-1}>
        Notes
      </Title>
      {!notes.length && (
        <Text c="dimmed">
          No notes yet: add Markdown files to a notes/ folder at the root of a
          repository.
        </Text>
      )}
      {noteGroups(notes).map((group) => (
        <Stack key={group.name ?? ""} gap={4}>
          {group.name && (
            <Title order={2} size="h5" c="dimmed">
              {group.name}
            </Title>
          )}
          {group.notes.map((note) => (
            <UnstyledButton
              key={note.id}
              component="a"
              href={notePath(note)}
              className="note-link"
            >
              <Group gap="sm" wrap="nowrap">
                <NotebookText size={18} />
                <Text>{note.title}</Text>
              </Group>
            </UnstyledButton>
          ))}
        </Stack>
      ))}
    </Stack>
  );
}

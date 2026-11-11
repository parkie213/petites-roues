import { Chip, Group } from "@mantine/core";
import type { LibraryLanguage } from "../language.ts";

/** A choice of one of the library's [languages], or all of them (null). */
export function LanguageChips({
  languages,
  value,
  onChange,
}: {
  languages: readonly LibraryLanguage[];
  value: string | null;
  onChange: (tag: string | null) => void;
}) {
  return (
    <Chip.Group value={value ?? ""} onChange={(tag) => onChange(tag || null)}>
      <Group gap="xs" role="group" aria-label="Language">
        <Chip value="" size="sm" variant="light">
          All languages
        </Chip>
        {languages.map((item) => (
          <Chip
            key={item.tag}
            value={item.tag}
            size="sm"
            variant="light"
            lang={item.tag}
          >
            {item.label}
          </Chip>
        ))}
      </Group>
    </Chip.Group>
  );
}

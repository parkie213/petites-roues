import type { CatalogueItem } from "../shared/types.ts";

/** Navigation within the indexed repository folders; discovery is a separate library action. */
export type NavigateFolder = (repository: string, parts: string[]) => void;

/** Which side of a manual media link the person is choosing for. */
export type LinkRequest =
  | {
      title: {
        id: string;
        type: CatalogueItem["type"];
        name: string | null | undefined;
      };
    }
  | {
      media: {
        repositoryId: string;
        relativePath: string;
        kind: CatalogueItem["type"];
        name: string;
      };
    };

/** Linking and unlinking report failure to the library, or ask it to reload after success. */
export interface LinkActions {
  onLink: (link: LinkRequest) => void;
  onChanged: (failure?: Error | null) => void;
}

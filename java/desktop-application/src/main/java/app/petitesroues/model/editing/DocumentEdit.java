package app.petitesroues.model.editing;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * One player or reader edit, stored in the review queue and part of the title's served copy until
 * an agent writes it into the document file and completes its batch. Every identity the edit
 * introduces (new cue IDs) is fixed here when the edit is first accepted, so replaying the queue
 * over an accepted copy, as a rebuild of the served copy does, always produces the same document.
 *
 * <p>{@link #kind()} and {@link #cueIds()} describe the edit to reviewers; they are derived from
 * the command and not stored separately in the JSON.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DocumentEdit.Timing.class, name = "timing"),
  @JsonSubTypes.Type(value = DocumentEdit.Suggestion.class, name = "suggestion"),
  @JsonSubTypes.Type(value = DocumentEdit.Comment.class, name = "comment"),
  @JsonSubTypes.Type(value = DocumentEdit.ReviewRequest.class, name = "review-request"),
  @JsonSubTypes.Type(value = DocumentEdit.EditVideoCue.class, name = "edit-video-cue"),
  @JsonSubTypes.Type(value = DocumentEdit.AddVideoCue.class, name = "add-video-cue"),
  @JsonSubTypes.Type(value = DocumentEdit.SplitVideoCue.class, name = "split-video-cue"),
  @JsonSubTypes.Type(value = DocumentEdit.MergeVideoCues.class, name = "merge-video-cues"),
  @JsonSubTypes.Type(value = DocumentEdit.DeleteVideoCue.class, name = "delete-video-cue"),
  @JsonSubTypes.Type(value = DocumentEdit.EditComicText.class, name = "edit-comic-text"),
  @JsonSubTypes.Type(value = DocumentEdit.CreateComicBox.class, name = "create-comic-box"),
  @JsonSubTypes.Type(value = DocumentEdit.PlaceComicBox.class, name = "place-comic-box"),
  @JsonSubTypes.Type(value = DocumentEdit.DeleteComicBox.class, name = "delete-comic-box")
})
public sealed interface DocumentEdit {
  /**
   * Queue kind shown to reviewers: timing, suggestion, comment, review-request, edit, add, split,
   * merge, delete, annotate.
   */
  String kind();

  /** False for feedback that the agent acts on but that leaves the document unchanged. */
  default boolean changesDocument() {
    return true;
  }

  /** Affected cue identities, including IDs that the edit removes. */
  List<String> cueIds();

  /** Proposed hearing for a suggestion, otherwise {@code null}. */
  default String suggestion() {
    return null;
  }

  record Timing(List<UpdateCueTiming> edits) implements DocumentEdit {
    public String kind() {
      return "timing";
    }

    public List<String> cueIds() {
      return edits.stream().map(UpdateCueTiming::id).toList();
    }
  }

  record Suggestion(String cueId, String text) implements DocumentEdit {
    public String kind() {
      return "suggestion";
    }

    public List<String> cueIds() {
      return List.of(cueId);
    }

    @Override
    public String suggestion() {
      return text;
    }
  }

  /**
   * Free-text feedback on a video or comic cue for the reviewing agent, such as a better
   * translation. It changes nothing in the document; the agent decides what to do with it.
   */
  record Comment(String cueId, String text) implements DocumentEdit {
    public String kind() {
      return "comment";
    }

    public List<String> cueIds() {
      return List.of(cueId);
    }

    @Override
    public String suggestion() {
      return text;
    }

    @Override
    public boolean changesDocument() {
      return false;
    }
  }

  /**
   * Asks the reviewing agent to check a cue's machine-made material, such as first-pass notes or a
   * cue the notes model could not annotate; {@code text} says what to check. Like a comment, it
   * changes nothing in the document. Players do not count it as their own pending work.
   */
  record ReviewRequest(String cueId, String text) implements DocumentEdit {
    public String kind() {
      return "review-request";
    }

    public List<String> cueIds() {
      return List.of(cueId);
    }

    @Override
    public String suggestion() {
      return text;
    }

    @Override
    public boolean changesDocument() {
      return false;
    }
  }

  record EditVideoCue(String cueId, VideoCueEdit edit) implements DocumentEdit {
    public String kind() {
      return "edit";
    }

    public List<String> cueIds() {
      return List.of(cueId);
    }
  }

  /** {@code cueId} is the server-assigned identity of the new cue. */
  record AddVideoCue(String cueId, VideoCueEdit edit) implements DocumentEdit {
    public String kind() {
      return "add";
    }

    public List<String> cueIds() {
      return List.of(cueId);
    }
  }

  /** {@code secondId} is the server-assigned identity of the second part. */
  record SplitVideoCue(String cueId, String secondId, SplitCue split) implements DocumentEdit {
    public String kind() {
      return "split";
    }

    public List<String> cueIds() {
      return List.of(cueId, secondId);
    }
  }

  /** {@code ids} in playback order; the first identity survives. */
  record MergeVideoCues(List<String> ids) implements DocumentEdit {
    public String kind() {
      return "merge";
    }

    public List<String> cueIds() {
      return ids;
    }
  }

  record DeleteVideoCue(String cueId) implements DocumentEdit {
    public String kind() {
      return "delete";
    }

    public List<String> cueIds() {
      return List.of(cueId);
    }
  }

  /** Replaces the source text of the comic line {@code lineId}, in whichever box holds it. */
  record EditComicText(String lineId, ComicTextEdit edit) implements DocumentEdit {
    public String kind() {
      return "edit";
    }

    public List<String> cueIds() {
      return List.of(lineId);
    }
  }

  /**
   * Adds a box. {@code boxId} and {@code lineId} are the server-assigned identities of the new box
   * and of the one line it starts with.
   */
  record CreateComicBox(String boxId, String lineId, ComicBoxCreate create)
      implements DocumentEdit {
    public String kind() {
      return "annotate";
    }

    public List<String> cueIds() {
      return List.of(boxId, lineId);
    }
  }

  /** Moves or reorders the box {@code boxId}, with all its lines. */
  record PlaceComicBox(String boxId, ComicBoxUpdate update) implements DocumentEdit {
    public String kind() {
      return "annotate";
    }

    public List<String> cueIds() {
      return List.of(boxId);
    }
  }

  /** Removes the box {@code boxId} and all its lines. */
  record DeleteComicBox(String boxId) implements DocumentEdit {
    public String kind() {
      return "annotate";
    }

    public List<String> cueIds() {
      return List.of(boxId);
    }
  }
}

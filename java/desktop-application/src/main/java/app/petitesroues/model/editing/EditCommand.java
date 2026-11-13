package app.petitesroues.model.editing;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/** Public edit inputs. New identities belong to the server and cannot be supplied here. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EditCommand.Timing.class, name = "timing"),
  @JsonSubTypes.Type(value = EditCommand.Suggestion.class, name = "suggestion"),
  @JsonSubTypes.Type(value = EditCommand.Comment.class, name = "comment"),
  @JsonSubTypes.Type(value = EditCommand.ReviewRequest.class, name = "review-request"),
  @JsonSubTypes.Type(value = EditCommand.EditVideoCue.class, name = "edit-video-cue"),
  @JsonSubTypes.Type(value = EditCommand.AddVideoCue.class, name = "add-video-cue"),
  @JsonSubTypes.Type(value = EditCommand.SplitVideoCue.class, name = "split-video-cue"),
  @JsonSubTypes.Type(value = EditCommand.MergeVideoCues.class, name = "merge-video-cues"),
  @JsonSubTypes.Type(value = EditCommand.DeleteVideoCue.class, name = "delete-video-cue"),
  @JsonSubTypes.Type(value = EditCommand.EditComicText.class, name = "edit-comic-text"),
  @JsonSubTypes.Type(value = EditCommand.CreateComicBox.class, name = "create-comic-box"),
  @JsonSubTypes.Type(value = EditCommand.PlaceComicBox.class, name = "place-comic-box"),
  @JsonSubTypes.Type(value = EditCommand.DeleteComicBox.class, name = "delete-comic-box")
})
public sealed interface EditCommand {
  record Timing(List<UpdateCueTiming> edits) implements EditCommand {}

  record Suggestion(String cueId, String text) implements EditCommand {}

  record Comment(String cueId, String text) implements EditCommand {}

  record ReviewRequest(String cueId, String text) implements EditCommand {}

  record EditVideoCue(String cueId, VideoCueEdit edit) implements EditCommand {}

  record AddVideoCue(VideoCueEdit edit) implements EditCommand {}

  record SplitVideoCue(String cueId, SplitCue split) implements EditCommand {}

  record MergeVideoCues(List<String> ids) implements EditCommand {}

  record DeleteVideoCue(String cueId) implements EditCommand {}

  record EditComicText(String lineId, ComicTextEdit edit) implements EditCommand {}

  record CreateComicBox(ComicBoxCreate create) implements EditCommand {}

  record PlaceComicBox(String boxId, ComicBoxUpdate update) implements EditCommand {}

  record DeleteComicBox(String boxId) implements EditCommand {}
}

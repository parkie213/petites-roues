package app.petitesroues.model.document;

/**
 * The media-specific part of a {@link StudyDocument}: timed cues for a video, or pages of boxes for
 * a comic. Sealed so that code depending on the kind of title can switch over every kind and the
 * compiler reports a kind it does not handle.
 */
public sealed interface StudyBody permits VideoDocument, ComicDocument {}

package app.petitesroues.model.library;

/**
 * What the skeleton endpoint is asked for: a new study document for media already in the index.
 * Absent optional fields take the defaults the service documents.
 *
 * @param media absolute path of an indexed video or CBZ
 * @param title display title; required
 * @param id the document ID, or null to derive it from the title
 * @param series optional series name
 * @param volume a comic's volume (tome) number; refused for a video
 * @param sourceLanguage BCP 47 tag of the media's language, default {@code fr}
 * @param translationLanguage default {@code en}
 * @param notesLanguage default {@code en}
 * @param leftToRight whether a comic reads left to right; default right to left
 * @param ignoreTrackLanguage whether a video whose first audio track is tagged with another
 *     language than {@code sourceLanguage} is accepted anyway
 */
public record TitleSkeletonRequest(
    String media,
    String title,
    String id,
    String series,
    Integer volume,
    String sourceLanguage,
    String translationLanguage,
    String notesLanguage,
    Boolean leftToRight,
    Boolean ignoreTrackLanguage) {}

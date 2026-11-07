"""Language rules the preparation scripts share with the app."""

# Primary language subtags of scripts written without spaces between words. Mirrors
# ChunkSeparator.java and chunkSeparator in web/src/player/languages.js.
UNSPACED = {"zh", "yue", "wuu", "ja", "th", "lo", "km", "my"}


def chunk_separator(tag):
    """What joins a line's phrase chunks back into its text: nothing for languages written
    without word spaces, a single space otherwise."""
    return "" if (tag or "").replace("_", "-").split("-")[0].lower() in UNSPACED else " "


def describe_join(tag):
    """How chunks of this language join, in words for a model's instructions."""
    return "nothing (no spaces between them)" if chunk_separator(tag) == "" else "single spaces"

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


# ISO 639-2 codes media files tag audio tracks with, terminology and bibliographic ("fre"), for
# the languages a title is likely to be in. Mirrors AudioLanguages.java, which has Java's full list.
THREE_LETTER = {
    "ara": "ar", "ben": "bn", "bul": "bg", "cat": "ca", "ces": "cs", "cze": "cs", "chi": "zh",
    "zho": "zh", "dan": "da", "deu": "de", "ger": "de", "ell": "el", "gre": "el", "eng": "en",
    "est": "et", "eus": "eu", "baq": "eu", "fas": "fa", "per": "fa", "fin": "fi", "fra": "fr",
    "fre": "fr", "gle": "ga", "glg": "gl", "heb": "he", "hin": "hi", "hrv": "hr", "hun": "hu",
    "ind": "id", "isl": "is", "ice": "is", "ita": "it", "jpn": "ja", "kor": "ko", "lav": "lv",
    "lit": "lt", "msa": "ms", "may": "ms", "nld": "nl", "dut": "nl", "nob": "nb", "nor": "no",
    "pol": "pl", "por": "pt", "ron": "ro", "rum": "ro", "rus": "ru", "slk": "sk", "slo": "sk",
    "slv": "sl", "spa": "es", "srp": "sr", "swe": "sv", "tha": "th", "tur": "tr", "ukr": "uk",
    "vie": "vi", "cym": "cy", "wel": "cy",
}


def primary(tag):
    """The BCP 47 primary subtag of a document language or an audio track's tag ("fre" → "fr");
    empty for an untagged or undetermined track."""
    code = (tag or "").replace("_", "-").split("-")[0].strip().lower()
    if code in {"und", "zxx", "mis", "mul"}:
        return ""
    return THREE_LETTER.get(code, code)

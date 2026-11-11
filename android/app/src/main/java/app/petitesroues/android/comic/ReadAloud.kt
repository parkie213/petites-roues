package app.petitesroues.android.comic

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Reads source-language text with the phone's text-to-speech voice, standing in for the desktop
 * server's Piper voice. One utterance at a time: new text interrupts, and toggling the text already
 * playing stops it. Text is tidied for the voice first (see [spoken]).
 */
class ReadAloud(context: Context, language: String) {
  /** The text being read, or null. */
  var speaking by mutableStateOf<String?>(null)
    private set

  var problem by mutableStateOf<String?>(null)
    private set

  private val locale = Locale.forLanguageTag(language)
  private val main = Handler(Looper.getMainLooper())
  private var ready = false
  private var pending: String? = null
  private val tts: TextToSpeech =
    TextToSpeech(context.applicationContext) { status ->
      main.post { initialised(status, locale) }
    }

  private fun initialised(status: Int, locale: Locale) {
    if (status != TextToSpeech.SUCCESS) {
      problem = "Text to speech is unavailable"
      return
    }
    // A comic without a study document has no language and nothing to read aloud.
    if (locale.language.isEmpty()) return
    val result = tts.setLanguage(locale)
    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
      problem = "No ${locale.displayLanguage} voice is installed"
      return
    }
    tts.setOnUtteranceProgressListener(
      object : UtteranceProgressListener() {
        override fun onStart(id: String) {}

        override fun onDone(id: String) = finished(id)

        override fun onStop(id: String, interrupted: Boolean) = finished(id)

        @Deprecated("Required override") override fun onError(id: String) = finished(id)
      }
    )
    ready = true
    pending?.let(::say)
    pending = null
  }

  private fun finished(text: String) {
    main.post { if (speaking == text) speaking = null }
  }

  /** Reads [text], or stops if it is already being read. */
  fun toggle(text: String) = if (speaking == text) stop() else say(text)

  /** Reads [text] from the start, interrupting anything else. */
  fun say(text: String) {
    val voiced = spoken(text, locale)
    if (voiced.isEmpty()) return
    speaking = text
    if (!ready) pending = text else tts.speak(voiced, TextToSpeech.QUEUE_FLUSH, null, text)
  }

  fun stop() {
    pending = null
    speaking = null
    if (ready) tts.stop()
  }

  fun shutdown() = tts.shutdown()
}

private val ellipsis = Regex("""\s*(?:\.{2,}|…)+\s*""")

/**
 * [text] as the voice should read it. Comic lettering in capitals is lowercased, since voices spell
 * out capitalised words as abbreviations ("NEZUKO"), and ellipses become a comma pause rather than
 * "point point point". Text with any lowercase letter keeps its case.
 */
internal fun spoken(text: String, locale: Locale): String {
  val cased = if (text.none { it.isLowerCase() }) text.lowercase(locale) else text
  return cased.replace(ellipsis, ", ").trim().trim(',').trim()
}

package app.petitesroues.speech;

import io.github.jvoiceproject.piperjni.PiperJNI;
import io.github.jvoiceproject.piperjni.PiperVoice;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Owns lazily loaded JNI voices, one per requested language. Piper handles synthesis locking and
 * native resource cleanup; this adapter selects models and encodes their PCM output as in-memory
 * WAV audio. Native libraries and models are not loaded until speech is requested.
 */
public final class PiperSynthesizer implements AutoCloseable {
  private final Path voices;
  private final Map<String, PiperVoice> loaded = new HashMap<>();
  private boolean closed;

  /** Uses the model directory without loading native libraries or opening any models. */
  public PiperSynthesizer(Path voices) {
    this.voices = voices;
  }

  /** Returns mono, 16-bit WAV audio without creating a child process or an audio file. */
  public byte[] synthesize(String language, String text) throws IOException {
    var voice = voice(language);
    int sampleRate = voice.getSampleRate();
    short[] samples = voice.textToAudio(text);
    var pcm = ByteBuffer.allocate(samples.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    pcm.asShortBuffer().put(samples);
    var format = new AudioFormat(sampleRate, 16, 1, true, false);
    var wav = new ByteArrayOutputStream();
    try (var audio =
        new AudioInputStream(new ByteArrayInputStream(pcm.array()), format, samples.length)) {
      AudioSystem.write(audio, AudioFileFormat.Type.WAVE, wav);
    }
    return wav.toByteArray();
  }

  private synchronized PiperVoice voice(String language) throws IOException {
    if (closed) throw new IllegalStateException("Speech synthesis is closed");
    var existing = loaded.get(language);
    if (existing != null) return existing;
    Path model = model(voices, language);
    Path config = Path.of(model + ".json");
    if (!Files.isRegularFile(config))
      throw new SpeechUnavailableException("No Piper voice configuration at " + config);
    try {
      PiperJNI.loadLibrary();
      var voice = PiperJNI.loadVoice(model, config);
      loaded.put(language, voice);
      return voice;
    } catch (IOException | LinkageError failure) {
      throw new SpeechUnavailableException("Could not load Piper voice at " + model, failure);
    }
  }

  /** First model by file name starting with the language followed by '_' or '-'. */
  static Path model(Path voices, String language) throws IOException {
    if (!Files.isDirectory(voices))
      throw new SpeechUnavailableException("No Piper voices directory at " + voices);
    try (var files = Files.list(voices)) {
      return files
          .filter(
              file -> {
                String name = file.getFileName().toString();
                return name.endsWith(".onnx")
                    && (name.startsWith(language + "_") || name.startsWith(language + "-"));
              })
          .sorted()
          .findFirst()
          .orElseThrow(
              () ->
                  new SpeechUnavailableException(
                      "No Piper voice for '" + language + "' in " + voices));
    }
  }

  /**
   * Prevents new voice loads and closes all owned models. Piper stops active synthesis between
   * native chunks and waits for its native calls to return before freeing the model; it cannot
   * forcibly interrupt a stuck native call. Repeated close is harmless.
   */
  @Override
  public void close() {
    List<PiperVoice> current;
    synchronized (this) {
      closed = true;
      current = List.copyOf(loaded.values());
      loaded.clear();
    }
    current.forEach(PiperVoice::close);
  }
}

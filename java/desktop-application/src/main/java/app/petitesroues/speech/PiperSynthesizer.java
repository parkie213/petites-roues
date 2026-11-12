package app.petitesroues.speech;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectWriter;

/**
 * Owns reusable Piper voices, lazily opening one per requested language. Failed processes are
 * discarded and recreated on a later request. Shutdown prevents new process creation and closes
 * in-flight voices without waiting for their synthesis monitors. Has no document or HTTP
 * dependency.
 */
public final class PiperSynthesizer implements AutoCloseable {
  private final Path executable;
  private final Path voices;
  private final ObjectWriter json;
  private final Duration timeout;
  private final Map<String, PiperVoice> running = new HashMap<>();
  private boolean closed;

  /** The writer must produce compact, single-line JSON for Piper's request protocol. */
  public PiperSynthesizer(Path executable, Path voices, ObjectWriter json) {
    this(executable, voices, json, Duration.ofSeconds(30));
  }

  PiperSynthesizer(Path executable, Path voices, ObjectWriter json, Duration timeout) {
    this.executable = executable;
    this.voices = voices;
    this.json = json;
    this.timeout = timeout;
  }

  /**
   * Synthesizes one utterance in memory; errors discard this voice without retrying the utterance.
   */
  public byte[] synthesize(String language, String text) throws IOException {
    var voice = voice(language);
    try {
      return voice.synthesize(text);
    } catch (IOException | RuntimeException failure) {
      synchronized (this) {
        running.remove(language, voice);
      }
      voice.close();
      throw failure;
    }
  }

  private synchronized PiperVoice voice(String language) throws IOException {
    if (closed) throw new IllegalStateException("Speech synthesis is closed");
    var existing = running.get(language);
    if (existing != null && existing.isUsable()) return existing;
    if (existing != null) {
      running.remove(language);
      existing.close();
    }
    if (!Files.isExecutable(executable))
      throw new SpeechUnavailableException("Piper is not installed at " + executable);
    var voice = new PiperVoice(executable, model(voices, language), json, timeout);
    running.put(language, voice);
    return voice;
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

  /** Stops process admission before releasing every owned voice; repeated close is harmless. */
  @Override
  public void close() {
    List<PiperVoice> current;
    synchronized (this) {
      closed = true;
      current = List.copyOf(running.values());
      running.clear();
    }
    current.forEach(PiperVoice::close);
  }
}

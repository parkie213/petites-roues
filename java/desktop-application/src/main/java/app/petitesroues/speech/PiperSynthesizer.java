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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Owns lazily loaded JNI voices, one per requested model. Piper handles synthesis locking and
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
    return synthesize(language, null, text);
  }

  /**
   * Uses the selected installed filename, or the existing language default when selection is null.
   */
  public byte[] synthesize(String language, String selectedVoice, String text) throws IOException {
    var voice = voice(language, selectedVoice);
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

  private synchronized PiperVoice voice(String language, String selectedVoice) throws IOException {
    if (closed) throw new IllegalStateException("Speech synthesis is closed");
    var installed = installedVoices().getOrDefault(language, List.of());
    if (installed.isEmpty())
      throw new SpeechUnavailableException("No Piper voice for '" + language + "' in " + voices);
    String filename = selectedVoice == null ? installed.getFirst() : selectedVoice;
    if (!installed.contains(filename))
      throw new SpeechUnavailableException("Selected Piper voice is not installed: " + filename);
    Path model = voices.resolve(filename);
    String key = model.getFileName().toString();
    var existing = loaded.get(key);
    if (existing != null) return existing;
    Path config = Path.of(model + ".json");
    try {
      PiperJNI.loadLibrary();
      var voice = PiperJNI.loadVoice(model, config);
      loaded.put(key, voice);
      return voice;
    } catch (IOException | LinkageError failure) {
      throw new SpeechUnavailableException("Could not load Piper voice at " + model, failure);
    }
  }

  /**
   * Lists model/configuration pairs without loading native code. Filenames provide language tags;
   * incomplete downloads and names without a language boundary are omitted. IDs are filenames,
   * never caller-supplied paths. The returned lists follow the default filename ordering.
   */
  public Map<String, List<String>> installedVoices() throws IOException {
    Map<String, List<String>> result = new TreeMap<>();
    if (!Files.isDirectory(voices)) return result;
    try (var files = Files.list(voices)) {
      for (Path file : files.sorted().toList()) {
        String name = file.getFileName().toString();
        if (!Files.isRegularFile(file)
            || !name.endsWith(".onnx")
            || !Files.isRegularFile(Path.of(file + ".json"))) continue;
        String language = name.split("[_-]", 2)[0];
        if (!language.matches("[a-z]{2,3}") || name.equals(language + ".onnx")) continue;
        result.computeIfAbsent(language, ignored -> new ArrayList<>()).add(name);
      }
    }
    return result;
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

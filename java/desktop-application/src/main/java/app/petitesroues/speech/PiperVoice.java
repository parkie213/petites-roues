package app.petitesroues.speech;

import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.databind.ObjectWriter;

/**
 * An opened Piper process for one model. Exchanges are serialized and read one bounded RIFF/WAV
 * frame from stdout per JSON request. The synthesizer owns creation, replacement and close; an
 * exchange never starts or restarts a process. Audio is never written to disk.
 */
final class PiperVoice implements AutoCloseable {
  private static final int MAX_WAV_BYTES = 32 * 1024 * 1024;
  private static final System.Logger LOG = System.getLogger(PiperVoice.class.getName());

  private final Process process;
  private final BufferedWriter input;
  private final InputStream output;
  private final ObjectWriter json;
  private final Duration timeout;
  // A failed exchange may leave unread bytes; queued callers must never reuse that stream.
  private volatile boolean failed;

  /** Opens the process; Piper itself loads the model before answering its first request. */
  PiperVoice(Path piper, Path model, ObjectWriter json, Duration timeout) throws IOException {
    process =
        new ProcessBuilder(
                piper.toString(),
                "--model",
                model.toString(),
                "--json-input",
                "--output_file",
                "-",
                "--quiet")
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    input =
        new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    output = process.getInputStream();
    this.json = json;
    this.timeout = timeout;
  }

  boolean isUsable() {
    return !failed && process.isAlive();
  }

  /** Bounds stdin writing and stdout reading once this serialized exchange is admitted. */
  synchronized byte[] synthesize(String text) throws IOException {
    if (!isUsable()) throw new IOException("Piper voice can no longer be used");
    var exchange =
        new FutureTask<>(
            () -> {
              input.write(json.writeValueAsString(Map.of("text", text)));
              input.newLine();
              input.flush();
              return readWav(output);
            });
    Thread.ofVirtual().name("piper-exchange").start(exchange);
    try {
      return exchange.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException expired) {
      failed = true;
      exchange.cancel(true);
      throw new IOException("Piper did not answer within " + timeout.toMillis() + " ms", expired);
    } catch (InterruptedException interrupted) {
      failed = true;
      exchange.cancel(true);
      Thread.currentThread().interrupt();
      throw new InterruptedIOException("Interrupted while waiting for Piper");
    } catch (ExecutionException failure) {
      failed = true;
      if (failure.getCause() instanceof IOException io) throw io;
      throw new IOException("Piper exchange failed", failure.getCause());
    }
  }

  /** Reads exactly one WAV, rejecting malformed, oversized and truncated responses before reuse. */
  static byte[] readWav(InputStream input) throws IOException {
    var header = input.readNBytes(12);
    if (header.length != 12) throw new EOFException("Piper ended before the WAV header");
    var buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
    if (buffer.getInt(0) != 0x46464952 || buffer.getInt(8) != 0x45564157)
      throw new IOException("Piper did not return RIFF/WAV audio");
    long size = Integer.toUnsignedLong(buffer.getInt(4)) + 8;
    if (size < 44 || size > MAX_WAV_BYTES)
      throw new IOException("Piper WAV size must be between 44 bytes and 32 MiB");
    var wav = new byte[(int) size];
    System.arraycopy(header, 0, wav, 0, header.length);
    if (input.readNBytes(wav, header.length, wav.length - header.length)
        != wav.length - header.length)
      throw new EOFException("Piper ended before the WAV was complete");
    return wav;
  }

  /**
   * Terminates this process independently of the exchange monitor, reaps it and closes its pipes.
   */
  @Override
  public void close() {
    failed = true;
    process.destroyForcibly();
    if (process.onExit().completeOnTimeout(null, 2, TimeUnit.SECONDS).join() == null)
      LOG.log(System.Logger.Level.WARNING, "Piper did not exit after termination");
    try {
      input.close();
    } catch (IOException ignored) {
      // The process has been terminated; its stdin may already be broken.
    }
    try {
      output.close();
    } catch (IOException ignored) {
      // The process has been terminated; its stdout may already be closed.
    }
  }
}

package app.petitesroues.speech;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * One long-running Piper process for one voice model. Piper loads its model once and then turns
 * each JSON line it reads into the named WAV file, printing that file's path when done, so a
 * request costs only synthesis. Requests are serialised because Piper handles one line at a time.
 * Nothing is kept between requests: each WAV is read and deleted immediately. A process that exits
 * or stops answering is discarded and started again on the next request.
 */
final class PiperVoice implements AutoCloseable {
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private final List<String> command;
  private Process process;
  private BufferedWriter input;
  private BufferedReader output;
  private Path directory;

  PiperVoice(Path piper, Path model) {
    this.command =
        List.of(piper.toString(), "--model", model.toString(), "--json-input", "--quiet");
  }

  /** Returns WAV audio for the text, starting Piper first if it is not already running. */
  synchronized byte[] synthesize(String text) throws IOException {
    start();
    Path file = directory.resolve(UUID.randomUUID() + ".wav");
    try {
      input.write(JSON.writeValueAsString(Map.of("text", text, "output_file", file.toString())));
      input.newLine();
      input.flush();
      String written = readLine();
      if (!file.toString().equals(written))
        throw new IOException("Piper stopped unexpectedly: " + written);
      return Files.readAllBytes(file);
    } catch (IOException | RuntimeException failure) {
      stop();
      throw failure;
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Override
  public synchronized void close() {
    stop();
  }

  private void start() throws IOException {
    if (process != null && process.isAlive()) return;
    stop();
    directory = Files.createTempDirectory("petites-roues-speech");
    process =
        new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    input =
        new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    output =
        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
  }

  /** Waits for Piper's next line; a hung process is stopped so the blocked read ends too. */
  private String readLine() throws IOException {
    var line =
        new FutureTask<>(
            () -> {
              try {
                return output.readLine();
              } catch (IOException failure) {
                return null;
              }
            });
    Thread.ofVirtual().start(line);
    try {
      return line.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    } catch (TimeoutException timeout) {
      throw new IOException("Piper did not answer within " + TIMEOUT.toSeconds() + " seconds");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new InterruptedIOException("Interrupted while waiting for Piper");
    } catch (ExecutionException failure) {
      throw new IOException("Piper could not be read", failure.getCause());
    }
  }

  private void stop() {
    if (process != null) process.destroyForcibly();
    process = null;
    input = null;
    output = null;
    if (directory != null) {
      try (var files = Files.list(directory)) {
        for (var file : files.toList()) Files.deleteIfExists(file);
        Files.deleteIfExists(directory);
      } catch (IOException ignored) {
        // Temporary files only; the operating system cleans its temp directory eventually.
      }
      directory = null;
    }
  }
}

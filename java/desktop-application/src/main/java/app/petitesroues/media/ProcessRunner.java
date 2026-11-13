package app.petitesroues.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs local tools with separate stdout and stderr and a deadline for execution and output reads.
 * The caller owns the executor; each invocation owns and cleans up its child process.
 */
public class ProcessRunner {
  private final ExecutorService executor;
  private final Duration timeout;

  public ProcessRunner(ExecutorService executor, Duration timeout) {
    this.executor = executor;
    this.timeout = timeout;
  }

  /**
   * Returns stdout on success; failures report stderr, timeout or interruption as an IOException.
   */
  public String run(List<String> command) throws IOException {
    long deadline = System.nanoTime() + timeout.toNanos();
    Process process;
    try {
      process = new ProcessBuilder(command).start();
    } catch (IOException failure) {
      throw new IOException("Could not start " + command.getFirst(), failure);
    }
    try {
      process.getOutputStream().close();
      var output =
          executor.submit(
              () -> new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
      var errors =
          executor.submit(
              () -> new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
      try {
        if (!process.waitFor(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
          throw new TimeoutException();
        String stdout = output.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        String stderr = errors.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        if (process.exitValue() != 0)
          throw new IOException(command.getFirst() + " failed: " + stderr.strip());
        return stdout;
      } catch (TimeoutException failure) {
        throw new IOException(
            command.getFirst() + " timed out after " + timeout.toSeconds() + " seconds", failure);
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw new IOException(command.getFirst() + " was interrupted", failure);
      } catch (ExecutionException failure) {
        throw new IOException(
            "Could not read output from " + command.getFirst(), failure.getCause());
      } finally {
        output.cancel(true);
        errors.cancel(true);
      }
    } finally {
      if (process.isAlive()) process.destroyForcibly();
      process.getInputStream().close();
      process.getErrorStream().close();
      process.getOutputStream().close();
    }
  }
}

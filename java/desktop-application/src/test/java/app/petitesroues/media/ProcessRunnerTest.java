package app.petitesroues.media;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Exercises real child processes, including full output pipes and cancellation. */
class ProcessRunnerTest {
  private List<String> command(String mode) {
    return List.of(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp",
        System.getProperty("java.class.path"),
        Child.class.getName(),
        mode);
  }

  @Test
  void drainsBothPipesAndReturnsOnlyStdout() throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      assertEquals(
          "x".repeat(100_000),
          new ProcessRunner(executor, Duration.ofSeconds(10)).run(command("output")));
    }
  }

  @Test
  void reportsStderrOnFailure() {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var failure =
          assertThrows(
              IOException.class,
              () -> new ProcessRunner(executor, Duration.ofSeconds(10)).run(command("fail")));
      assertTrue(failure.getMessage().contains("bad input"));
    }
  }

  @Test
  void timesOutWhileStdoutIsStillOpen() {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      assertTimeoutPreemptively(
          Duration.ofSeconds(5),
          () -> {
            var failure =
                assertThrows(
                    IOException.class,
                    () -> new ProcessRunner(executor, Duration.ofMillis(300)).run(command("hang")));
            assertTrue(failure.getMessage().contains("timed out"));
          });
    }
  }

  @Test
  void interruptionStopsTheCallAndPreservesTheFlag() throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var runner = new ProcessRunner(executor, Duration.ofSeconds(30));
      var result = new java.util.concurrent.CompletableFuture<Boolean>();
      Thread caller =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      Thread.currentThread().interrupt();
                      runner.run(command("hang"));
                      result.complete(false);
                    } catch (IOException failure) {
                      result.complete(
                          failure.getMessage().contains("interrupted")
                              && Thread.currentThread().isInterrupted());
                    }
                  });
      assertTrue(result.get(5, TimeUnit.SECONDS));
      caller.join();
    }
  }

  /** Separate JVM keeps the fixtures portable and leaves no descendant processes. */
  public static class Child {
    public static void main(String[] args) throws Exception {
      switch (args[0]) {
        case "output" -> {
          System.out.print("x".repeat(100_000));
          System.err.print("e".repeat(100_000));
        }
        case "fail" -> {
          System.err.print("bad input");
          System.exit(2);
        }
        case "hang" -> Thread.sleep(60_000);
        default -> throw new IllegalArgumentException();
      }
    }
  }
}

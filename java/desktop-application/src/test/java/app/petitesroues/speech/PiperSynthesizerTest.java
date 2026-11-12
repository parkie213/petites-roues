package app.petitesroues.speech;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

@Timeout(10)
class PiperSynthesizerTest {
  @TempDir Path temp;
  private Path piper;
  private Path voices;

  @BeforeEach
  void fakePiper() throws Exception {
    piper = temp.resolve("piper");
    voices = Files.createDirectory(temp.resolve("voices"));
    Files.createFile(voices.resolve("fr_FR-test.onnx"));
    Files.createFile(voices.resolve("en_GB-test.onnx"));
    Files.writeString(
        piper,
        """
        #!/usr/bin/env python3
        import io, json, os, struct, sys, threading, wave
        from pathlib import Path
        assert '--json-input' in sys.argv
        assert sys.argv[sys.argv.index('--output_file') + 1] == '-'
        with open(sys.argv[0] + '-starts', 'a') as audit:
            audit.write(str(os.getpid()) + '\\n')
        if Path(sys.argv[0] + '-block-input').exists():
            threading.Event().wait()
        for line in sys.stdin:
            request = json.loads(line)
            assert set(request) == {'text'}
            text = request['text']
            if text == 'crash':
                sys.exit(7)
            if text == 'hang':
                Path(sys.argv[0] + '-entered').touch()
                threading.Event().wait()
            wav = io.BytesIO()
            with wave.open(wav, 'wb') as audio:
                audio.setnchannels(1)
                audio.setsampwidth(1)
                audio.setframerate(22050)
                audio.writeframes(text.encode())
            sys.stdout.buffer.write(wav.getvalue())
            sys.stdout.buffer.flush()
        """);
    Files.setPosixFilePermissions(piper, PosixFilePermissions.fromString("rwx------"));
  }

  private PiperSynthesizer engine(Duration timeout) {
    return new PiperSynthesizer(piper, voices, JsonMapper.builder().build().writer(), timeout);
  }

  private String text(byte[] wav) {
    return new String(Arrays.copyOfRange(wav, 44, wav.length), StandardCharsets.UTF_8);
  }

  private List<Long> pids() throws IOException {
    return Files.readAllLines(Path.of(piper + "-starts")).stream().map(Long::parseLong).toList();
  }

  private void assertProcessesStopped() throws IOException {
    for (long pid : pids())
      assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
  }

  @Test
  void reusesTheProcessAndFramesEscapedMultilineRequestsWithoutFiles() throws Exception {
    try (var engine = engine(Duration.ofSeconds(3))) {
      String source = "Il a dit : « Ça va ? »\nOui, \\\"très bien\\\".";
      assertEquals(source, text(engine.synthesize("fr", source)));
      assertEquals("encore", text(engine.synthesize("fr", "encore")));
      assertEquals(1, pids().size());
      try (var files = Files.walk(temp)) {
        assertFalse(files.anyMatch(file -> file.toString().endsWith(".wav")));
      }
    }
    assertProcessesStopped();
  }

  @Test
  void discardsACrashedProcessAndCreatesOneOnTheNextRequest() throws Exception {
    try (var engine = engine(Duration.ofSeconds(3))) {
      assertEquals("bonjour", text(engine.synthesize("fr", "bonjour")));
      assertThrows(IOException.class, () -> engine.synthesize("fr", "crash"));
      assertProcessesStopped();
      assertEquals("encore", text(engine.synthesize("fr", "encore")));
      assertEquals(2, pids().size());
    }
    assertProcessesStopped();
  }

  @Test
  void timesOutBlockedOutputAndRecoversOnTheNextRequest() throws Exception {
    try (var engine = engine(Duration.ofSeconds(1))) {
      assertThrows(IOException.class, () -> engine.synthesize("fr", "hang"));
      assertProcessesStopped();
      assertEquals("encore", text(engine.synthesize("fr", "encore")));
      assertEquals(2, pids().size());
    }
    assertProcessesStopped();
  }

  @Test
  void aFailedVoiceCannotConsumeAnotherRequestsResponseWhileItsOwnerDiscardsIt() throws Exception {
    try (var voice =
        new PiperVoice(
            piper,
            voices.resolve("fr_FR-test.onnx"),
            JsonMapper.builder().build().writer(),
            Duration.ofMillis(250))) {
      assertThrows(IOException.class, () -> voice.synthesize("hang"));
      assertFalse(voice.isUsable());
      var failure = assertThrows(IOException.class, () -> voice.synthesize("bonjour"));
      assertEquals("Piper voice can no longer be used", failure.getMessage());
    }
    assertProcessesStopped();
  }

  @Test
  void theTimeoutAlsoCoversBlockedStdin() throws Exception {
    Files.createFile(Path.of(piper + "-block-input"));
    try (var engine = engine(Duration.ofSeconds(1))) {
      assertThrows(IOException.class, () -> engine.synthesize("fr", "x".repeat(1024 * 1024)));
      assertProcessesStopped();
    }
  }

  @Test
  void closeInterruptsAnExchangeAndCannotReopenTheProcess() throws Exception {
    try (var callers = Executors.newVirtualThreadPerTaskExecutor();
        var engine = engine(Duration.ofSeconds(30))) {
      var result = callers.submit(() -> engine.synthesize("fr", "hang"));
      await().atMost(Duration.ofSeconds(3)).until(() -> Files.exists(Path.of(piper + "-entered")));
      engine.close();
      var failure = assertThrows(ExecutionException.class, () -> result.get(3, TimeUnit.SECONDS));
      assertInstanceOf(IOException.class, failure.getCause());
      assertProcessesStopped();
      assertThrows(IllegalStateException.class, () -> engine.synthesize("fr", "late"));
      assertEquals(1, pids().size());
      engine.close();
    }
  }

  @Test
  void callerInterruptionReleasesTheProcessAndPreservesTheInterruptFlag() throws Exception {
    try (var engine = engine(Duration.ofSeconds(30))) {
      var preserved = new AtomicBoolean();
      var caller =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      engine.synthesize("fr", "hang");
                    } catch (InterruptedIOException interrupted) {
                      preserved.set(Thread.currentThread().isInterrupted());
                    } catch (IOException unexpected) {
                      throw new AssertionError(unexpected);
                    }
                  });
      await().atMost(Duration.ofSeconds(3)).until(() -> Files.exists(Path.of(piper + "-entered")));
      caller.interrupt();
      assertTrue(caller.join(Duration.ofSeconds(3)));
      assertTrue(preserved.get());
      assertProcessesStopped();
      assertEquals("encore", text(engine.synthesize("fr", "encore")));
    }
    assertProcessesStopped();
  }

  @Test
  void concurrentRequestsStayPairedAndDifferentLanguagesHaveDifferentProcesses() throws Exception {
    try (var callers = Executors.newVirtualThreadPerTaskExecutor();
        var engine = engine(Duration.ofSeconds(3))) {
      var texts = List.of("premier", "deuxième", "troisième", "quatrième");
      var results =
          texts.stream()
              .map(source -> callers.submit(() -> engine.synthesize("fr", source)))
              .toList();
      for (int index = 0; index < texts.size(); index++)
        assertEquals(texts.get(index), text(results.get(index).get(3, TimeUnit.SECONDS)));
      assertEquals(1, pids().size());
      assertEquals("hello", text(engine.synthesize("en", "hello")));
      assertEquals(2, pids().size());
    }
    assertProcessesStopped();
  }

  @Test
  void choosesTheFirstModelAndRemainsOptionalWithoutAnInstallation() throws Exception {
    Files.createFile(voices.resolve("fr_FR-a.onnx"));
    assertEquals(voices.resolve("fr_FR-a.onnx"), PiperSynthesizer.model(voices, "fr"));
    assertThrows(SpeechUnavailableException.class, () -> PiperSynthesizer.model(voices, "ja"));
    try (var engine =
        new PiperSynthesizer(
            temp.resolve("missing"), voices, JsonMapper.builder().build().writer())) {
      assertThrows(SpeechUnavailableException.class, () -> engine.synthesize("fr", "bonjour"));
    }
    assertFalse(Files.exists(Path.of(piper + "-starts")));
  }
}

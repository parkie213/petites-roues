package com.petitesroues.speech;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpeechServiceTest {
  @TempDir Path temp;

  @Test
  void speaksAllCapsLetteringInLowerCaseButKeepsMixedCase() {
    var french = Locale.forLanguageTag("fr");
    assertEquals(
        "tu es descendu par ce temps ?",
        SpeechService.speakable("TU ES DESCENDU PAR CE TEMPS ?", french));
    assertEquals("ÇA VA, Tanjiro ?", SpeechService.speakable("ÇA VA, Tanjiro ?", french));
    assertEquals("ça va", SpeechService.speakable("ÇA VA", french));
  }

  @Test
  void choosesTheFirstVoiceForTheLanguage() throws Exception {
    for (var name :
        new String[] {
          "fr_FR-upmc-medium.onnx",
          "fr_FR-siwis-medium.onnx",
          "fr_FR-siwis-medium.onnx.json",
          "en_GB-alba-medium.onnx",
          "fra-not-a-voice.onnx"
        }) Files.createFile(temp.resolve(name));
    assertEquals(temp.resolve("fr_FR-siwis-medium.onnx"), SpeechService.model(temp, "fr"));
    assertEquals(temp.resolve("en_GB-alba-medium.onnx"), SpeechService.model(temp, "en"));
    var missing =
        assertThrows(SpeechUnavailableException.class, () -> SpeechService.model(temp, "ja"));
    assertTrue(missing.getMessage().contains("'ja'"));
    assertThrows(
        SpeechUnavailableException.class, () -> SpeechService.model(temp.resolve("absent"), "fr"));
  }

  @Test
  void keepsOneProcessForRepeatedRequestsAndRestartsItAfterAFailure() throws Exception {
    // Mimics Piper's --json-input protocol: write the requested file, then print its path.
    // Text "crash" makes the process exit, like a Piper failure part-way through.
    var piper = temp.resolve("piper");
    Files.writeString(
        piper,
        """
        #!/usr/bin/env python3
        import json, sys
        open(sys.argv[0] + "-starts", "a").write("started\\n")
        for line in sys.stdin:
            request = json.loads(line)
            if request["text"] == "crash":
                sys.exit(1)
            open(request["output_file"], "w").write("WAV:" + request["text"])
            print(request["output_file"], flush=True)
        """);
    Files.setPosixFilePermissions(piper, PosixFilePermissions.fromString("rwx------"));
    try (var voice = new PiperVoice(piper, temp.resolve("model.onnx"))) {
      assertEquals("WAV:bonjour", new String(voice.synthesize("bonjour")));
      assertEquals("WAV:je veux", new String(voice.synthesize("je veux")));
      assertEquals(1, Files.readAllLines(Path.of(piper + "-starts")).size());
      assertThrows(Exception.class, () -> voice.synthesize("crash"));
      assertEquals("WAV:encore", new String(voice.synthesize("encore")));
      assertEquals(2, Files.readAllLines(Path.of(piper + "-starts")).size());
    }
  }
}

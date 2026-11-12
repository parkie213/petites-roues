package app.petitesroues.speech;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class PiperVoiceTest {
  private byte[] frame(int size) {
    var wav = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    wav.putInt(0, 0x46464952).putInt(4, size - 8).putInt(8, 0x45564157);
    return wav.array();
  }

  @Test
  void readsOneFrameWithoutConsumingTheNextResponse() throws Exception {
    var two = ByteBuffer.allocate(88).put(frame(44)).put(frame(44)).array();
    var input = new ByteArrayInputStream(two);
    assertArrayEquals(frame(44), PiperVoice.readWav(input));
    assertEquals(44, input.available());
    assertArrayEquals(frame(44), PiperVoice.readWav(input));
    assertEquals(0, input.available());
  }

  @Test
  void rejectsMalformedTruncatedAndOversizedFrames() {
    assertThrows(
        IOException.class, () -> PiperVoice.readWav(new ByteArrayInputStream(new byte[12])));
    assertThrows(
        EOFException.class, () -> PiperVoice.readWav(new ByteArrayInputStream(new byte[5])));
    assertThrows(
        EOFException.class, () -> PiperVoice.readWav(new ByteArrayInputStream(frame(100))));
    assertThrows(IOException.class, () -> PiperVoice.readWav(new ByteArrayInputStream(frame(12))));
    assertThrows(
        IOException.class,
        () -> PiperVoice.readWav(new ByteArrayInputStream(frame(33 * 1024 * 1024))));
    assertThrows(IOException.class, () -> PiperVoice.readWav(new ByteArrayInputStream(frame(-1))));
  }
}

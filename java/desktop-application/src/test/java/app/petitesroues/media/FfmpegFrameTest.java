package app.petitesroues.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/**
 * Grabs real frames from a generated video, so needs ffmpeg on the PATH as the server does; the
 * tests are skipped where it is not installed.
 */
class FfmpegFrameTest {
  private final ExecutorService processExecutor = Executors.newVirtualThreadPerTaskExecutor();
  private final ProcessRunner processes =
      new ProcessRunner(processExecutor, Duration.ofSeconds(30));
  private final Ffmpeg ffmpeg = new Ffmpeg(new ObjectMapper(), processes);

  @TempDir Path folder;
  private Path video;

  @AfterEach
  void closeProcessExecutor() {
    processExecutor.close();
  }

  /** A 4:3 video: three red seconds, then one blue second. */
  @BeforeEach
  void makeVideo() {
    boolean available = true;
    video = folder.resolve("clip.mp4");
    try {
      processes.run(
          List.of(
              "ffmpeg",
              "-v",
              "error",
              "-y",
              "-filter_complex",
              "color=red:s=320x240:r=10:d=3[a];color=blue:s=320x240:r=10:d=1[b];[a][b]concat=n=2",
              "-pix_fmt",
              "yuv420p",
              video.toString()));
    } catch (IOException missing) {
      available = false;
    }
    assumeTrue(available, "ffmpeg is not installed");
  }

  private static Color center(Path jpeg) throws IOException {
    BufferedImage image = ImageIO.read(jpeg.toFile());
    assertNotNull(image, "a whole JPEG");
    return new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2));
  }

  @Test
  void aFrameIsSixteenByNineAndCenterCroppedFromAFourByThreeVideo() throws IOException {
    Path target = folder.resolve("frame.jpg");

    ffmpeg.frame(video, 0.25, 480, target);

    BufferedImage frame = ImageIO.read(target.toFile());
    assertEquals(480, frame.getWidth());
    assertEquals(270, frame.getHeight());
  }

  @Test
  void theFrameIsTakenAQuarterOfTheWayThrough() throws IOException {
    Path early = folder.resolve("early.jpg");
    Path late = folder.resolve("late.jpg");

    ffmpeg.frame(video, 0.25, 480, early);
    ffmpeg.frame(video, 0.9, 480, late);

    Color first = center(early), last = center(late);
    assertTrue(first.getRed() > 150 && first.getBlue() < 100, "a quarter in is still red");
    assertTrue(last.getBlue() > 150 && last.getRed() < 100, "near the end is blue");
  }

  @Test
  void anUnreadableVideoFailsWithAnIOException() {
    Path broken = folder.resolve("broken.mp4");
    assertThrows(IOException.class, () -> ffmpeg.frame(broken, 0.25, 480, folder.resolve("x.jpg")));
  }
}

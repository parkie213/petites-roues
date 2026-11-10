package com.petitesroues.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/** Writes real thumbnails, so needs ffmpeg on the PATH, as the server does. */
class FfmpegThumbnailTest {
  @TempDir Path folder;

  @Test
  void concurrentThumbnailsOfOneImageEachWriteTheWholeFile() throws Exception {
    Path image = folder.resolve("poster.png");
    ImageIO.write(new BufferedImage(400, 600, BufferedImage.TYPE_INT_RGB), "png", image.toFile());
    Path target = folder.resolve("card.jpg");
    var ffmpeg = new Ffmpeg(new ObjectMapper());

    // Two first requests for the same card generate it at once, as the library page can.
    try (var pool = Executors.newFixedThreadPool(6)) {
      var writes = new ArrayList<Future<Object>>();
      for (int i = 0; i < 6; i++)
        writes.add(
            pool.submit(
                (Callable<Object>)
                    () -> {
                      ffmpeg.thumbnail(image, 200, target);
                      return null;
                    }));
      for (var write : writes) write.get();
    }

    BufferedImage card = ImageIO.read(target.toFile());
    assertNotNull(card, "the card is a whole JPEG");
    assertEquals(200, card.getWidth());
    try (var files = Files.list(folder)) {
      assertEquals(2, files.count(), "no temporary files are left beside it");
    }
  }
}

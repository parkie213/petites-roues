package com.petitesroues.web;

import com.petitesroues.speech.SpeechService;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Speaks text in a title's source language; audio is generated per request and never stored. */
@RestController
@RequestMapping("/api/titles/{id}/speech")
public class SpeechController {
  private static final MediaType WAV = MediaType.parseMediaType("audio/wav");
  private final SpeechService speech;

  public SpeechController(SpeechService speech) {
    this.speech = speech;
  }

  @GetMapping
  public ResponseEntity<byte[]> speak(@PathVariable String id, @RequestParam String text)
      throws IOException {
    return ResponseEntity.ok()
        .contentType(WAV)
        .cacheControl(CacheControl.noStore())
        .body(speech.speak(id, text));
  }
}

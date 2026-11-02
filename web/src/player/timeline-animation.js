/**
 * Draws from the media clock on browser animation frames, independently of React updates.
 * Keeps cue-boundary holds exact, skips unchanged paused frames, and cancels on cleanup.
 */
export function animateTimeline(videoRef, heldTimeRef, draw) {
  let frame;
  let lastTime;
  function tick() {
    const video = videoRef.current;
    const time =
      video?.paused && heldTimeRef.current >= 0
        ? heldTimeRef.current
        : (video?.currentTime ?? 0);
    if (time !== lastTime) {
      draw(time);
      lastTime = time;
    }
    frame = requestAnimationFrame(tick);
  }
  tick();
  return () => cancelAnimationFrame(frame);
}

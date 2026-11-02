import { createContext, useContext, useEffect, useRef, useState } from "react";
import { responseError } from "../shared/api.js";

const ReadAloudContext = createContext(null);

/**
 * Plays source-language text through the server's local voice. One utterance plays at a time:
 * asking for new text stops the current one, and asking for the text already playing stops it.
 * Audio is fetched for each request and released when it ends. `autoListen` is the reader's
 * preference for reading each bubble as its card opens.
 */
export function ReadAloudProvider({ url, autoListen = false, children }) {
  const current = useRef(null);
  const [state, setState] = useState({ text: null, loading: false, error: "" });

  function stop() {
    const playing = current.current;
    current.current = null;
    playing?.abort.abort();
    playing?.audio?.pause();
    if (playing?.src) URL.revokeObjectURL(playing.src);
    setState((value) => ({ ...value, text: null, loading: false }));
  }

  async function speak(text) {
    const repeat = current.current?.text === text;
    stop();
    if (repeat || !text?.trim()) return;
    const playing = { text, abort: new AbortController() };
    current.current = playing;
    setState({ text, loading: true, error: "" });
    try {
      const response = await fetch(`${url}?text=${encodeURIComponent(text)}`, {
        signal: playing.abort.signal,
      });
      if (!response.ok) throw await responseError(response);
      playing.src = URL.createObjectURL(await response.blob());
      if (current.current !== playing) return URL.revokeObjectURL(playing.src);
      playing.audio = new Audio(playing.src);
      playing.audio.onended = () => current.current === playing && stop();
      setState({ text, loading: false, error: "" });
      await playing.audio.play();
    } catch (failure) {
      if (failure.name === "AbortError" || current.current !== playing) return;
      stop();
      setState({ text: null, loading: false, error: failure.message });
    }
  }

  useEffect(() => stop, []);
  return (
    <ReadAloudContext.Provider value={{ ...state, speak, stop, autoListen }}>
      {children}
    </ReadAloudContext.Provider>
  );
}

/** {text, loading, error, autoListen, speak(text), stop()} for the nearest reader. */
export function useReadAloud() {
  return useContext(ReadAloudContext);
}

/** The R key reads the given text aloud, matching the video player's replay key. */
export function ReadAloudKey({ text }) {
  const { speak } = useReadAloud();
  useEffect(() => {
    const keydown = (event) => {
      if (
        event.key.toLowerCase() !== "r" ||
        !text ||
        event.defaultPrevented ||
        event.repeat ||
        event.altKey ||
        event.ctrlKey ||
        event.metaKey ||
        /INPUT|SELECT|TEXTAREA/.test(event.target.tagName) ||
        event.target.isContentEditable
      )
        return;
      event.preventDefault();
      speak(text);
    };
    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  });
  return null;
}

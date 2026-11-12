import { useCallback, useEffect, useRef, useState } from "react";
import { followJob, latestJob, startJob } from "./library-job.js";

/**
 * The library's background Add and Refresh: the running job while there is one (null otherwise),
 * and start(body), which begins one and follows it. A job already running when the page loads,
 * for example after a reload, is picked up. onFinished(job, body) runs once a job ends.
 */
export function useLibraryJob(onFinished) {
  const [job, setJob] = useState(null);
  const finished = useRef(onFinished);
  finished.current = onFinished;
  const stop = useRef(() => {});

  const follow = useCallback((started, body) => {
    stop.current();
    setJob(started);
    stop.current = followJob(started, (next) => {
      if (next.state === "running") setJob(next);
      else {
        setJob(null);
        finished.current(next, body);
      }
    });
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    latestJob(controller.signal).then(
      (running) => running?.state === "running" && follow(running, null),
      () => {},
    );
    return () => {
      controller.abort();
      stop.current();
    };
  }, [follow]);

  const start = useCallback(
    async (body) => follow(await startJob(body), body),
    [follow],
  );
  return { job, start };
}

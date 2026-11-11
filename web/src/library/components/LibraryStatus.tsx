import { Alert } from "@mantine/core";
import { Check, TriangleAlert } from "lucide-react";
import { JobProgress } from "../JobProgress.jsx";

/** What the library is doing and how its latest action went: a running job, an error, a message. */
export function LibraryStatus({
  job,
  error,
  message,
}: {
  job: unknown;
  error: string;
  message: string;
}) {
  return (
    <>
      {job != null && <JobProgress job={job} />}
      {error && (
        <Alert
          color="red"
          icon={<TriangleAlert size={18} />}
          title="Something went wrong"
          role="alert"
        >
          {error}
        </Alert>
      )}
      {message && (
        <Alert color="sage" icon={<Check size={18} />} role="status">
          {message}
        </Alert>
      )}
    </>
  );
}

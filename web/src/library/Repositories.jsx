import { useEffect, useState } from "react";
import { chooseOnComputer, request } from "../shared/api.js";
import "./repositories.css";

export function Repositories() {
  const [repos, setRepos] = useState(null),
    [path, setPath] = useState(""),
    [name, setName] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [removing, setRemoving] = useState(null);
  const load = (signal) =>
    request("/api/repositories", { signal }).then(setRepos);
  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal).catch((e) => {
      if (e.name !== "AbortError") setError(e.message);
    });
    return () => controller.abort();
  }, []);
  async function act(url, method, body, success) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await request(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: body ? JSON.stringify(body) : undefined,
      });
      await load();
      setMessage(success);
      setRemoving(null);
      return true;
    } catch (failure) {
      setError(failure.message);
      return false;
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="page repositories-page">
      <nav>
        <a href="/">← Library</a>
      </nav>
      <header className="page-header">
        <div>
          <div className="eyebrow">Your study content</div>
          <h1>Repositories</h1>
          <p>
            Register a local Git checkout. Refresh after pulling new content.
          </p>
        </div>
      </header>
      <form
        className="repository-add"
        onSubmit={async (e) => {
          e.preventDefault();
          if (
            await act(
              "/api/repositories",
              "POST",
              { localPath: path, name },
              "Repository added. Link videos from the library to start watching.",
            )
          ) {
            setPath("");
            setName("");
          }
        }}
      >
        <h2>Add repository</h2>
        <label htmlFor="repository-path">Directory on this computer</label>
        <div className="repository-path">
          <input
            id="repository-path"
            required
            placeholder="Path to your local study-content checkout"
            value={path}
            onChange={(e) => setPath(e.target.value)}
          />
          <button
            type="button"
            disabled={busy}
            onClick={async () => {
              try {
                const chosen = await chooseOnComputer("folder");
                if (chosen) setPath(chosen);
              } catch (failure) {
                setError(failure.message);
              }
            }}
          >
            Browse…
          </button>
        </div>
        <label htmlFor="repository-name">Display name (optional)</label>
        <input
          id="repository-name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Study content"
        />
        <button className="action" disabled={busy}>
          {busy ? "Working…" : "Add repository"}
        </button>
        <p className="detail">
          Documents stay in the checkout. Videos are linked separately. Git pull
          and push remain normal Git commands.
        </p>
      </form>
      {message && <p role="status">{message}</p>}
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <section aria-label="Registered repositories" className="repository-list">
        {repos?.map((repo) => (
          <article key={repo.id} className="repository-card">
            <div>
              <h2>{repo.name}</h2>
              <p className="repository-directory">{repo.localPath}</p>
              <p>
                {repo.titles} titles
                {repo.missing ? ` · ${repo.missing} documents missing` : ""}
              </p>
              <p className="detail">
                Last refreshed:{" "}
                {repo.lastRefreshedAt
                  ? new Date(repo.lastRefreshedAt).toLocaleString()
                  : "Never"}
              </p>
            </div>
            <div className="repository-actions">
              <a href={`/?repository=${repo.id}&view=all`}>View titles →</a>
              <button
                disabled={busy}
                onClick={() =>
                  act(
                    `/api/repositories/${repo.id}/refresh`,
                    "POST",
                    null,
                    "Repository refreshed.",
                  )
                }
              >
                Refresh
              </button>
              {removing === repo.id ? (
                <>
                  <p>
                    Unregister its titles? Files and videos will stay on disk.
                  </p>
                  <button
                    disabled={busy}
                    onClick={() =>
                      act(
                        `/api/repositories/${repo.id}`,
                        "DELETE",
                        null,
                        "Repository removed from the library. Files were left untouched.",
                      )
                    }
                  >
                    Remove from library
                  </button>
                  <button onClick={() => setRemoving(null)}>Cancel</button>
                </>
              ) : (
                <button disabled={busy} onClick={() => setRemoving(repo.id)}>
                  Remove…
                </button>
              )}
            </div>
          </article>
        ))}
        {repos?.length === 0 && (
          <p className="empty">
            No repositories yet. Add a checkout above to populate your library.
          </p>
        )}
        {!repos && !error && <p role="status">Loading repositories…</p>}
      </section>
    </main>
  );
}

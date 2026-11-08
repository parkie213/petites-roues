/** Shared navigation for the library, study notes and general quizzes. */
function navigate(event) {
  if (
    event.button !== 0 ||
    event.metaKey ||
    event.ctrlKey ||
    event.shiftKey ||
    event.altKey
  )
    return;
  event.preventDefault();
  history.pushState(null, "", event.currentTarget.href);
  window.dispatchEvent(new PopStateEvent("popstate"));
}

export function LibraryHeader({ active, actionsRef }) {
  return (
    <header className="library-header">
      <a
        href="/"
        onClick={navigate}
        className="library-brand"
        aria-label="petites roues library"
      >
        <img src="/favicon.png?v=7" alt="" width="48" height="48" />
        <span>petites roues</span>
      </a>
      <nav className="library-main-nav" aria-label="Main navigation">
        {[
          ["library", "/", "Library"],
          ["notes", "/notes", "Notes"],
          ["quizzes", "/quizzes", "Quiz me"],
        ].map(([key, href, label]) => (
          <a
            key={key}
            href={href}
            onClick={navigate}
            aria-current={active === key ? "page" : undefined}
          >
            {label}
          </a>
        ))}
      </nav>
      <div className="library-header-actions" ref={actionsRef} />
    </header>
  );
}

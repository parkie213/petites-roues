package app.petitesroues.library;

import org.springframework.stereotype.Component;

/**
 * The single monitor that serializes library mutations. Player and reader edits ({@link
 * LibraryUpdateService}), review-queue operations ({@code ReviewQueueService}) and repository
 * add/refresh/remove ({@link ContentRepositoryService}), including the same work run as library
 * jobs ({@link LibraryJobService}), each read and change the state the previous operation produced,
 * so they must not interleave.
 *
 * <p>The mutating services acquire this monitor themselves; no caller has to remember a locking
 * convention, and no caller can bypass the serialization. Acquire it before opening a database
 * transaction: it is deliberately held across repository discovery, and a transaction must never
 * wait for it. The monitor is reentrant, so a service holding it may call another service that
 * acquires it.
 */
@Component
public final class LibraryUpdateMonitor {}

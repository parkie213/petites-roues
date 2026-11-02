package com.petitesroues.model.library;

/**
 * A registered, structured collection of learning content. Its folders organize study documents,
 * translations, learner notes and optional local videos or comics. A repository may be a Git
 * checkout, but Git is not required by this model.
 *
 * <p>The registration describes a local content root and its last successful discovery result.
 * Missing documents remain registered so their local learning state survives temporary absence.
 * Files may change after discovery; counts describe registered entries rather than current file
 * availability.
 *
 * @param id stable local registration identity, independent of the content folder's name
 * @param name display name chosen when registering the content directory
 * @param localPath absolute path to the local content root
 * @param lastRefreshedAt last successful discovery time in UTC, or {@code null} before discovery
 * @param titles number of registered entries belonging to this repository, including missing
 *     documents
 * @param missing number of registered entries whose documents were absent at the last refresh
 */
public record ContentRepository(
    String id, String name, String localPath, String lastRefreshedAt, int titles, int missing) {}

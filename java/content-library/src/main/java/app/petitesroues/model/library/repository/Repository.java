package app.petitesroues.model.library.repository;

/**
 * A content collection's identity and display name, independent of its local storage and index. The
 * identity is opaque and stable within the owning library; it is not a cross-device identifier.
 * Platform registrations supply paths or storage grants separately.
 */
public record Repository(String id, String name) {}

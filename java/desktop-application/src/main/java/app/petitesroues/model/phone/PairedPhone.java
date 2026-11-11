package app.petitesroues.model.phone;

/**
 * A phone paired with this desktop. Its token is never kept, only a hash of it.
 *
 * @param id local identity, used to forget the phone
 * @param name the name the phone gave when pairing
 * @param pairedAt when it paired, ISO-8601
 * @param lastSeenAt its last authenticated request, ISO-8601, or null
 */
public record PairedPhone(String id, String name, String pairedAt, String lastSeenAt) {}

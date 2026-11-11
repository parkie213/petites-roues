package app.petitesroues.model.sync;

/**
 * What a phone sends to pair: the secret from the desktop's QR code and its own name.
 *
 * @param secret the one-time pairing secret ({@link PairingCode#pair()})
 * @param name the phone's name, shown in the desktop's list of phones
 */
public record PairRequest(String secret, String name) {}

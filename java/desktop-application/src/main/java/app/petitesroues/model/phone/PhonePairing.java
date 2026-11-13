package app.petitesroues.model.phone;

import app.petitesroues.sync.model.PairingCode;
import java.time.Instant;

/**
 * A pairing in progress, as the desktop shows it.
 *
 * @param code what the QR code holds
 * @param expiresAt when its secret stops working
 */
public record PhonePairing(PairingCode code, Instant expiresAt) {}

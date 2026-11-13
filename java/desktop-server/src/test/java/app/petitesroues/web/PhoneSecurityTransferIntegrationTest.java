package app.petitesroues.web;

import org.springframework.test.context.ActiveProfiles;

/** Replays the existing real phone transfer contract through the opt-in security chain. */
@ActiveProfiles("security")
class PhoneSecurityTransferIntegrationTest extends PhoneTransferIntegrationTest {}

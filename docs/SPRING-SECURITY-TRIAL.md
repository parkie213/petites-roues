# Spring Security phone trial

This optional comparison uses Spring Security for phone authentication and request
authorization. The default still uses `PhoneRequestFilter`; both use the same
pairing service and SQLite phone records. It is independent of the MCP, Modulith
and ArchUnit trial branches.

## Run it

```sh
./java/mvnw -B -f java/pom.xml verify
java -jar java/desktop-server/target/desktop-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=security
```

Use the UI's existing phone switch and QR pairing flow. No Android change,
password account, new credential or database migration is needed. Omit the profile
to compare with the existing filter.

The server declares `spring-security-config` and `spring-security-web` directly;
the existing Boot 4.0.8 dependency management selects Security 7.0.7. There is no
Boot security starter or default password/login auto-configuration. Only the
`security` profile enables `@EnableWebSecurity` and its filter chain.

## What Spring owns

- `AuthenticationFilter` converts the existing Authorization bearer header into
  an authentication request. Its manager delegates token verification and the
  last-seen update to `PhonePairingService` on every authenticated request.
- The authenticated principal is the `PairedPhone`, with `ROLE_PHONE` and no raw
  token credentials. Spring creates and clears its request security context.
- `SecurityFilterChain` requires that role for every `/phone` request except
  `POST /phone/pair`. Pairing remains authorized by its one-time secret; unrelated
  Authorization headers on that public operation are ignored as before.
- Missing, malformed or revoked credentials retain HTTP 401,
  `WWW-Authenticate: Bearer`, and `{error: "Pair this phone with the desktop again"}`.
  There are no redirects or saved requests.

`PhonePairingService` still owns pairing secrets, token hashing/storage, revocation
and last-seen writes. The self-signed TLS certificate, pinning and listener switch
also retain their existing owners. No OAuth server, JWT or remote identity provider
is involved.

## Preserved local and transport behavior

`LocalRequestFilter` runs once inside the active security chain, before credentials
are examined; its automatic servlet registration is disabled in this profile.
The old `PhoneRequestFilter` bean is excluded. With the profile off, the two
original servlet filters run as before.

Port isolation remains the local filter's responsibility: the phone listener
cannot serve UI/API routes and the UI port cannot serve `/phone` routes, even with
a valid phone token. Its existing Host, Origin and Fetch Metadata checks still
protect local writes. The trial disables Spring's CSRF-token mechanism because
it introduces no cookie-based authentication: phone calls explicitly carry bearer
credentials, while local writes retain the existing origin checks. Statelessness
alone is not the CSRF justification.

Sessions, form login, HTTP Basic, logout and request caching are disabled. Spring
response-header defaults are also disabled for this bounded authentication trial,
so media caching and the existing header contract stay with the controllers.
Spring's default strict HTTP firewall still applies when the profile is active;
unusual encoded paths can therefore be rejected earlier than by the original
filters. This experiment is not a complete security-policy migration.

## Verification and value

```sh
./java/mvnw -B -f java/pom.xml -pl desktop-server -am test \
  -Dtest=PhoneListenerIntegrationTest,PhoneSecurityIntegrationTest,PhoneSecurityTransferIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The profile tests inherit the existing pairing/revocation and real phone transfer
journeys, including certificate pinning, cut/resumed downloads and file access
restrictions. Additional requests check the authenticated principal, absence of
credentials/sessions/cookies, JSON challenges, subsequent unauthenticated requests,
two phone identities, and local write-origin rejection with persisted-state checks.
A test-only route observes the security context; it is not included in production.

The demonstrated benefit is a standard request identity/context lifecycle and
declarative authorization, with a place for future permissions. For the current
single phone role, the custom filter is shorter: this trial does not establish
that replacing it reduces code or fixes an existing authentication defect. Adopt
the framework if that shared authorization model earns its configuration and
dependency cost; keep pairing and listener domain rules in their current services.

References: [servlet architecture](https://docs.spring.io/spring-security/reference/7.0/servlet/architecture.html),
[authentication](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/architecture.html).

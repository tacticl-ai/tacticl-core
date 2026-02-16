# ADR-0001: Tacticl Authentication Strategy

**Status:** Accepted
**Date:** 2026-02-16
**Deciders:** @cuztomizer

## Context

Tacticl is a new product alongside Strategiz, with 2 additional products planned. We evaluated authentication architecture options for the multi-product ecosystem:

1. **Shared identity DB** — one `users` collection, product-specific profiles
2. **Separate DBs per product** — fully isolated auth
3. **White-label auth** — single auth service with brand-configurable UI
4. **Firebase Auth** — delegate to Google's managed service

Building shared auth infrastructure now would be premature — Tacticl has zero users and the product is still in active development. Over-investing in auth before validating the product risks wasted effort.

## Decision

**Keep `DevAuthController` for development and testing. Build proper auth closer to launch.**

## Current Implementation

- `DevAuthController` at `POST /api/auth/dev-token` generates PASETO tokens for testing
- Mobile app stores tokens via `useAuthStore` → `localStorage` (web) / `expo-secure-store` (native)
- All API calls authenticated via `Authorization: Bearer` header
- Agent chat verified working end-to-end against production

## Planned Auth Architecture (When Needed)

When Tacticl approaches launch:

- **1 central identity DB** — shared `users` collection (email, password hash, MFA config)
- **Product-specific profile DBs** — each product's Firestore has `profiles/{userId}`
- **1 auth service** — extracted `service-auth-core` (identity-only, no product onboarding)
- **Product-specific onboarding** — each product handles post-auth signup steps
- **Brand-configurable auth UI** — same frontend codebase, config-driven branding (logo, colors, domain)

## Security Considerations

- `DevAuthController` currently has `@Profile({"local", "qa", "prod"})` — **remove `"prod"` before real users exist**
- Dev token endpoint must NOT be available in production permanently
- PASETO v4.local tokens with symmetric encryption (consistent with Strategiz)

## Consequences

**Positive:**
- No wasted engineering effort on auth infra before product-market fit
- Development velocity maintained — dev tokens work for all testing scenarios
- Architecture direction documented for when the time comes

**Negative:**
- No real user auth flow until explicitly built
- Risk of forgetting to remove prod profile before launch (mitigated by this ADR)

**Neutral:**
- Auth architecture decision deferred, not avoided — will revisit pre-launch

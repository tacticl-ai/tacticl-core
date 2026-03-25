# Tacticl HTTP-Only Cookie Auth Design

**Date:** 2026-03-22
**Status:** Draft

## Problem

Tacticl web app (`tacticl.ai`) currently uses localStorage + Bearer header for auth tokens. This is insecure (XSS-accessible) and doesn't match the cidadel platform's HTTP-only cookie architecture. The auth flow also has a session persistence issue — users get kicked out to the sign-in page unexpectedly.

## Architecture

Cidadel is the sole identity provider (IDP). It issues PASETO v4.local tokens with `iss=cidadel.io`. Product backends (tacticl-core, strategiz-core) only validate tokens — they never issue them.

### Domain Layout

| Domain | Service | Project | Purpose |
|--------|---------|---------|---------|
| `auth.tacticl.ai` | cidadel-web (Firebase Hosting) | cidadel | Auth frontend (login/signup UI) |
| `auth-api.tacticl.ai` | cidadel-api (Cloud Run) | cidadel | Auth backend (token issuance, OAuth, session mgmt) |
| `api.tacticl.ai` | tacticl-core (Cloud Run) | tacticl | Product backend (sparks, agent, social) |
| `tacticl.ai` | tacticl-web (Firebase Hosting) | tacticl | Product frontend (chat, dashboard) |

All on `.tacticl.ai` — HTTP-only cookies set on `.tacticl.ai` domain are sent to all four automatically.

### Token Properties

- **Format:** PASETO v4.local (symmetric encryption)
- **Issuer:** `cidadel.io` (prod), `qa.cidadel.io` (QA)
- **Audience:** `tacticl`
- **Access token validity:** 2 hours
- **Refresh token validity:** 7 days
- **Cookie names:** `tacticl-access-token`, `tacticl-refresh-token`
- **Cookie attributes:** `HttpOnly`, `Secure`, `SameSite=Lax`, `Domain=.tacticl.ai`

## Auth Flow (Token Relay Pattern)

Cidadel already implements a token relay pattern for cross-subdomain SSO. The flow:

```
1. User visits tacticl.ai/chat
2. ProtectedRoute: no cookie → redirect to auth.tacticl.ai/signin?redirect=tacticl.ai/chat

3. User clicks "Sign in with Google" on auth.tacticl.ai
4. Google OAuth → redirect back to auth.tacticl.ai/auth/oauth/google/signin/callback

5. Auth frontend calls auth-api.tacticl.ai:
   POST /v1/auth/oauth/google/signin/callback { code, state }
   → cidadel-api exchanges code, creates session
   → Response: { accessToken, refreshToken, user, redirectAfterAuth }
   → Sets HTTP-only cookies on .tacticl.ai domain

6. Auth frontend calls auth-api.tacticl.ai:
   POST /v1/auth/token/generate?redirect=tacticl.ai/chat
   → cidadel-api validates cookie, generates one-time token
   → Returns redirect URL with one-time token appended

7. Auth frontend redirects to tacticl.ai/chat?auth_token={one-time-token}

8. tacticl-web receives one-time token, calls:
   POST auth-api.tacticl.ai/v1/auth/token/exchange { token: one-time-token }
   → cidadel-api validates one-time token
   → Sets HTTP-only cookies (tacticl-access-token, tacticl-refresh-token) on .tacticl.ai
   → Cleans one-time token from URL

9. All subsequent requests to api.tacticl.ai include cookies automatically
   → PasetoAuthenticationFilter reads tacticl-access-token cookie
   → Validates PASETO token (iss=cidadel.io, aud=tacticl)
```

## Changes Required

### 1. cidadel-web (auth frontend) — `cidadel-web` repo

**Product config update:**
- `src/config/products.ts`: Update tacticl's `authApiUrl` from `cidadel-api-iboj74jsea-ue.a.run.app` to `auth-api.tacticl.ai`

No other changes — the token relay flow is already implemented.

### 2. tacticl-web (product frontend) — `tacticl-web` repo

**Replace localStorage auth with cookie-based auth:**

- `src/stores/auth-store.ts`:
  - Remove `localStorage.getItem/setItem('tacticl-auth-token')`
  - Remove `localStorage.getItem/setItem('tacticl-user-id')`
  - `hydrate()`: When `auth_token` URL param is present, call `POST auth-api.tacticl.ai/v1/auth/token/exchange` to exchange for HTTP-only cookies, then clean URL
  - Auth state derived from whether API calls succeed (no client-side token storage)

- `src/api/client.ts`:
  - Remove `Authorization: Bearer` header injection
  - Keep `credentials: 'include'` (sends cookies cross-origin)
  - On 401: redirect to `auth.tacticl.ai/signin?redirect=...` (already implemented)

- `src/lib/websocket.ts`:
  - Remove `?token=` query parameter from WebSocket URL
  - Browser automatically sends cookies in WebSocket upgrade request
  - No code changes needed for cookie transmission — just remove the token param

- `src/components/auth/ProtectedRoute.tsx`:
  - Instead of checking `token` in store, make a lightweight session check call
  - Or: attempt first API call, if 401 → redirect to auth

- `src/hooks/useAuth.ts`:
  - `logout()`: Call `POST auth-api.tacticl.ai/v1/auth/sessions/revoke` to clear server-side session
  - Server clears HTTP-only cookies in response

- Add `.env` variable: `VITE_AUTH_API_URL=https://auth-api.tacticl.ai`

### 3. tacticl-core (product backend) — `tacticl-core` repo

**PasetoAuthenticationFilter (already configured):**
- Cookie name: `tacticl-access-token` (via `cidadel.authorization.access-token-cookie` property)
- Reads cookie first, falls back to Authorization header
- No changes needed

**PasetoTokenValidator — add issuer/audience validation:**
- Validate `iss=cidadel.io` (prod) or `iss=qa.cidadel.io` (QA)
- Validate `aud=tacticl`
- Properties: `auth.token.issuer=cidadel.io`, `auth.token.audience=tacticl`

**WebSocketAuthInterceptor:**
- Read cookie from WebSocket upgrade request headers instead of `?token=` query param
- Fall back to query param for device connections (devices use Bearer tokens, not cookies)

**Remove unnecessary cidadel auth component scanning:**
- tacticl-core should scan cidadel auth packages only for validation (PasetoTokenValidator, PasetoAuthenticationFilter)
- Auth controllers/services for login, OAuth, session management should NOT be scanned — those endpoints belong to cidadel-api only

### 4. cidadel-core (shared framework) — `cidadel-core` submodule

**PasetoTokenIssuer (in cidadel-api only):**
- `auth.token.issuer=cidadel.io` (prod)
- `auth.token.audience` set per product via ProductContext

**PasetoTokenValidator (used by all products):**
- Add configurable `auth.token.expected-issuer` property
- Validate issuer claim matches expected value
- Already validates token cryptographically; this adds claim validation

**AuthorizationProperties (already done):**
- `accessTokenCookie` property (configurable per product) — already implemented

### 5. Infrastructure

**Domain mapping (already created):**
- `auth-api.tacticl.ai` → cidadel-api in `cidadel` GCP project
- CNAME: `auth-api` → `ghs.googlehosted.com` (in GoDaddy)
- SSL auto-provisioned by Google

**Vault (already configured):**
- PASETO keys at `secret/tacticl/tokens` (prod.session-key, prod.identity-key)
- Same keys at `secret/cidadel/tokens` for cidadel-api
- Shared symmetric keys = cidadel issues, tacticl validates

## WebSocket Auth with Cookies

The browser automatically includes cookies in the WebSocket upgrade (HTTP) request. The `WebSocketAuthInterceptor` extracts the token from the `Cookie` header:

```java
// Extract from cookie in upgrade request
List<String> cookies = request.getHeaders().get("Cookie");
// Parse tacticl-access-token from cookie header
// Validate with PasetoTokenValidator
```

For **device connections** (daemons, not browsers), the existing `?token=` query param is kept as a fallback since devices use Bearer tokens, not cookies.

## What Tacticl-Core Should NOT Do

- Should NOT scan cidadel auth controllers (OAuth, session, signup endpoints)
- Should NOT issue tokens (no PasetoTokenIssuer needed)
- Should NOT handle login/logout API endpoints
- Should ONLY validate tokens via PasetoAuthenticationFilter and PasetoTokenValidator

## Migration

No migration needed — all test users. Users re-login and get new tokens with correct issuer. Access tokens expire in 2 hours.

## Testing

1. Clear all cookies for `tacticl.ai`
2. Visit `tacticl.ai/chat` → should redirect to `auth.tacticl.ai`
3. Sign in with Google → should redirect back to `tacticl.ai/chat`
4. Chat should work (API calls use cookies)
5. Refresh page → should stay logged in (cookies persist)
6. WebSocket should connect (cookies sent in upgrade)
7. Wait 2h+ → token should auto-refresh via refresh token cookie
8. Logout → cookies cleared, redirect to sign-in

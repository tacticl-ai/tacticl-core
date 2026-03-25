# Tacticl HTTP-Only Cookie Auth Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace localStorage/Bearer header auth in tacticl-web with HTTP-only cookies set by cidadel-api, using the existing token relay pattern.

**Architecture:** cidadel-api (at `auth-api.tacticl.ai`) is the sole token issuer. After OAuth login, cidadel-web generates a one-time token and redirects to tacticl-web. tacticl-web exchanges the one-time token for HTTP-only cookies via cidadel-api. All subsequent API calls rely on cookies sent automatically by the browser.

**Tech Stack:** TypeScript/React (tacticl-web, cidadel-web), Java/Spring Boot (tacticl-core, cidadel-core)

**Spec:** `docs/superpowers/specs/2026-03-22-tacticl-http-only-cookie-auth-design.md`

---

## File Structure

### cidadel-web (`/Users/cuztomizer/Documents/GitHub/cidadel-web`)
- Modify: `src/config/products.ts` — update tacticl `authApiUrl` and `apiBaseUrl`

### tacticl-web (`/Users/cuztomizer/Documents/GitHub/tacticl-web`)
- Rewrite: `src/stores/auth-store.ts` — remove localStorage, add token exchange
- Modify: `src/api/client.ts` — remove Bearer header, cookie-only
- Modify: `src/lib/websocket.ts` — remove `?token=` query param
- Modify: `src/components/auth/ProtectedRoute.tsx` — cookie-aware auth check
- Modify: `src/hooks/useAuth.ts` — server-side logout
- Modify: `.env.production` — add `VITE_AUTH_API_URL`
- Modify: `.env.development` — add `VITE_AUTH_API_URL`

### tacticl-core (`/Users/cuztomizer/Documents/GitHub/tacticl-core`)
- Modify: `service/service-agent/src/main/java/io/strategiz/social/service/agent/websocket/WebSocketAuthInterceptor.java` — read cookie from upgrade request
- Modify: `application-api/src/main/resources/application.properties` — add issuer/audience validation
- Modify: `application-api/src/main/resources/application-prod.properties` — add issuer/audience, CORS for auth-api

### cidadel-core (submodule in tacticl-core)
- Modify: `framework/framework-authorization/src/main/java/io/cidadel/framework/authorization/config/AuthorizationProperties.java` — add expected issuer/audience properties
- Modify: `framework/framework-authorization/src/main/java/io/cidadel/framework/authorization/validator/PasetoTokenValidator.java` — validate issuer/audience claims

---

## Chunk 1: cidadel-web — Update Auth API URL for Tacticl

### Task 1: Update tacticl product config to use auth-api.tacticl.ai

**Files:**
- Modify: `cidadel-web/src/config/products.ts:31-32`

- [ ] **Step 1: Update authApiUrl and apiBaseUrl for tacticl**

In `cidadel-web/src/config/products.ts`, change the tacticl config:

```typescript
// Before (lines 31-32):
authApiUrl: 'https://cidadel-api-iboj74jsea-ue.a.run.app',
apiBaseUrl: 'https://cidadel-api-iboj74jsea-ue.a.run.app',

// After:
authApiUrl: 'https://auth-api.tacticl.ai',
apiBaseUrl: 'https://auth-api.tacticl.ai',
```

- [ ] **Step 2: Build and deploy cidadel-web**

```bash
cd /Users/cuztomizer/Documents/GitHub/cidadel-web
npm run build
npx firebase deploy --only hosting
```

- [ ] **Step 3: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/cidadel-web
git add src/config/products.ts
git commit -m "feat: update tacticl auth API URL to auth-api.tacticl.ai"
```

---

## Chunk 2: tacticl-web — Replace localStorage Auth with Cookie Auth

### Task 2: Add AUTH_API_URL env variable

**Files:**
- Modify: `tacticl-web/.env.production`
- Modify: `tacticl-web/.env.development`

- [ ] **Step 1: Add VITE_AUTH_API_URL to env files**

`.env.production`:
```
VITE_API_BASE_URL=https://api.tacticl.ai
VITE_AUTH_API_URL=https://auth-api.tacticl.ai
```

`.env.development`:
```
VITE_API_BASE_URL=http://localhost:8080
VITE_AUTH_API_URL=http://localhost:8080
```

- [ ] **Step 2: Commit**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
git add .env.production .env.development
git commit -m "feat: add VITE_AUTH_API_URL env variable"
```

### Task 3: Rewrite auth-store.ts — remove localStorage, add token exchange

**Files:**
- Rewrite: `tacticl-web/src/stores/auth-store.ts`

- [ ] **Step 1: Replace auth-store.ts with cookie-based implementation**

```typescript
import { create } from 'zustand';

const AUTH_API_URL =
  import.meta.env.VITE_AUTH_API_URL || 'https://auth-api.tacticl.ai';

const AUTH_SIGNIN_URL =
  import.meta.env.VITE_AUTH_SIGNIN_URL || 'https://auth.tacticl.ai/signin';

interface AuthState {
  userId: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  hydrate: () => Promise<void>;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  userId: null,
  isLoading: true,
  isAuthenticated: false,

  hydrate: async () => {
    // Check for one-time token in URL (SSO redirect from auth.tacticl.ai)
    const params = new URLSearchParams(window.location.search);
    const authToken = params.get('auth_token');

    if (authToken) {
      try {
        // Exchange one-time token for HTTP-only cookies
        const response = await fetch(`${AUTH_API_URL}/v1/auth/token/exchange`, {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ token: authToken }),
        });

        if (response.ok) {
          const data = await response.json();
          // Clean URL — remove auth params
          params.delete('auth_token');
          params.delete('user_id');
          const clean = params.toString();
          const newUrl = window.location.pathname + (clean ? `?${clean}` : '');
          window.history.replaceState({}, '', newUrl);

          set({
            userId: data.userId || data.user?.id || null,
            isAuthenticated: true,
            isLoading: false,
          });
          return;
        }
      } catch (error) {
        console.error('Token exchange failed:', error);
      }

      // Clean URL even on failure
      params.delete('auth_token');
      params.delete('user_id');
      const clean = params.toString();
      const newUrl = window.location.pathname + (clean ? `?${clean}` : '');
      window.history.replaceState({}, '', newUrl);
    }

    // No URL token — check if we have a valid session cookie
    // by making a lightweight session check call
    try {
      const response = await fetch(`${AUTH_API_URL}/v1/auth/session/check`, {
        method: 'GET',
        credentials: 'include',
      });

      if (response.ok) {
        const data = await response.json();
        set({
          userId: data.userId || data.user?.id || null,
          isAuthenticated: true,
          isLoading: false,
        });
        return;
      }
    } catch {
      // Session check failed — user is not authenticated
    }

    set({ userId: null, isAuthenticated: false, isLoading: false });
  },

  clearAuth: () => {
    set({ userId: null, isAuthenticated: false });
  },
}));
```

- [ ] **Step 2: Verify no other files import `token` from auth-store**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
grep -rn "\.token" src/ --include="*.ts" --include="*.tsx" | grep -v node_modules | grep -v ".d.ts"
```

Review each reference — they should use `isAuthenticated` instead of `token`.

- [ ] **Step 3: Commit**

```bash
git add src/stores/auth-store.ts
git commit -m "feat: replace localStorage auth with HTTP-only cookie token exchange"
```

### Task 4: Update API client — remove Bearer header, cookie-only

**Files:**
- Modify: `tacticl-web/src/api/client.ts`

- [ ] **Step 1: Rewrite client.ts to use cookies only**

```typescript
import { useAuthStore } from '../stores/auth-store';

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'https://api.tacticl.ai';

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

class ApiClient {
  private async request<T>(
    path: string,
    options: RequestInit = {},
  ): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(options.headers as Record<string, string>),
    };

    // No Authorization header — HTTP-only cookies are sent automatically
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
      credentials: 'include', // Send cookies cross-origin
    });

    if (response.status === 401) {
      useAuthStore.getState().clearAuth();
      const redirectUrl = encodeURIComponent(window.location.href);
      window.location.href = `https://auth.tacticl.ai/signin?redirect=${redirectUrl}`;
      throw new ApiError(401, 'Unauthorized');
    }

    if (!response.ok) {
      const body = await response.text();
      throw new ApiError(response.status, body);
    }

    if (response.status === 204) return undefined as T;
    return response.json();
  }

  get<T>(path: string) {
    return this.request<T>(path);
  }

  post<T>(path: string, body?: unknown) {
    return this.request<T>(path, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  put<T>(path: string, body?: unknown) {
    return this.request<T>(path, {
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  delete<T>(path: string) {
    return this.request<T>(path, { method: 'DELETE' });
  }
}

export const api = new ApiClient();
```

- [ ] **Step 2: Commit**

```bash
git add src/api/client.ts
git commit -m "feat: remove Bearer header from API client, use cookies only"
```

### Task 5: Update WebSocket — remove token query param

**Files:**
- Modify: `tacticl-web/src/lib/websocket.ts`

- [ ] **Step 1: Remove token from WebSocket URL**

In `websocket.ts`, replace the `connect()` method's URL construction (lines 46-55):

```typescript
// Before:
const token = useAuthStore.getState().token;
const url = `${WS_BASE_URL}/ws/user${token ? `?token=${encodeURIComponent(token)}` : ''}`;

// After:
// Browser automatically sends cookies in the WebSocket upgrade request.
// No token in URL needed — more secure (no token in server logs).
const url = `${WS_BASE_URL}/ws/user`;
```

Also remove the `import { useAuthStore }` at line 1 if no longer used elsewhere in the file.

- [ ] **Step 2: Commit**

```bash
git add src/lib/websocket.ts
git commit -m "feat: remove token from WebSocket URL, rely on cookies in upgrade request"
```

### Task 6: Update ProtectedRoute — use isAuthenticated instead of token

**Files:**
- Modify: `tacticl-web/src/components/auth/ProtectedRoute.tsx`

- [ ] **Step 1: Replace token check with isAuthenticated**

```typescript
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import { useAuthStore } from '../../stores/auth-store';

export default function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const isLoading = useAuthStore((s) => s.isLoading);

  if (isLoading) {
    return (
      <Box
        sx={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: 'background.default',
        }}
      >
        <CircularProgress />
      </Box>
    );
  }

  if (!isAuthenticated) {
    const redirectUrl = encodeURIComponent(window.location.href);
    window.location.href = `https://auth.tacticl.ai/signin?redirect=${redirectUrl}`;
    return null;
  }

  return <>{children}</>;
}
```

- [ ] **Step 2: Commit**

```bash
git add src/components/auth/ProtectedRoute.tsx
git commit -m "feat: use isAuthenticated flag instead of token presence check"
```

### Task 7: Update useAuth hook — server-side logout

**Files:**
- Modify: `tacticl-web/src/hooks/useAuth.ts`

- [ ] **Step 1: Add server-side session revocation to logout**

```typescript
import { useAuthStore } from '../stores/auth-store';
import { useNavigate } from 'react-router-dom';

const AUTH_API_URL =
  import.meta.env.VITE_AUTH_API_URL || 'https://auth-api.tacticl.ai';

export function useAuth() {
  const { userId, isLoading, isAuthenticated, clearAuth } = useAuthStore();
  const navigate = useNavigate();

  const logout = async () => {
    try {
      // Revoke session server-side — cidadel-api clears HTTP-only cookies
      await fetch(`${AUTH_API_URL}/v1/auth/sessions/revoke`, {
        method: 'POST',
        credentials: 'include',
      });
    } catch {
      // Best-effort — clear client state regardless
    }
    clearAuth();
    navigate('/', { replace: true });
  };

  return {
    userId,
    isLoading,
    isAuthenticated,
    logout,
  };
}
```

- [ ] **Step 2: Commit**

```bash
git add src/hooks/useAuth.ts
git commit -m "feat: add server-side session revocation on logout"
```

### Task 8: Build and deploy tacticl-web

- [ ] **Step 1: Build**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-web
npm run build
```

- [ ] **Step 2: Deploy**

```bash
npx firebase deploy --only hosting
```

- [ ] **Step 3: Commit all remaining changes**

```bash
git add -A
git commit -m "feat: complete HTTP-only cookie auth migration"
```

---

## Chunk 3: tacticl-core — WebSocket Cookie Auth & Token Validation

### Task 9: Update WebSocketAuthInterceptor — read cookie from upgrade request

**Files:**
- Modify: `tacticl-core/service/service-agent/src/main/java/io/strategiz/social/service/agent/websocket/WebSocketAuthInterceptor.java`

- [ ] **Step 1: Add cookie extraction to WebSocket handshake**

Replace the full `beforeHandshake` method:

```java
@Override
public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
        WebSocketHandler wsHandler, Map<String, Object> attributes) {
    try {
        var params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        String deviceId = params.getFirst("deviceId");

        // Determine if this is a device or user connection based on the path
        String path = request.getURI().getPath();
        boolean isDeviceConnection = path.contains("/ws/device");

        if (isDeviceConnection && deviceId == null) {
            log.warn("[WS-AUTH] Missing deviceId for device connection");
            return false;
        }

        // Extract token: cookie first (browser), then query param (device fallback)
        String token = extractTokenFromCookie(request);
        if (token == null) {
            token = params.getFirst("token");
        }

        if (token == null) {
            log.warn("[WS-AUTH] No token found in cookie or query param");
            return false;
        }

        // Strip "Bearer " prefix if present
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        Optional<AuthenticatedUser> userOpt = tokenValidator.validateAndExtract(token);
        if (userOpt.isEmpty()) {
            log.warn("[WS-AUTH] Token validation failed");
            return false;
        }

        String userId = userOpt.get().getUserId();
        attributes.put("principal", new WebSocketPrincipal(userId, deviceId));

        if (isDeviceConnection) {
            log.info("[WS-AUTH] Authenticated device: user={}, device={}", userId, deviceId);
        } else {
            log.info("[WS-AUTH] Authenticated user: user={}", userId);
        }
        return true;
    } catch (Exception ex) {
        log.error("[WS-AUTH] Handshake failed", ex);
        return false;
    }
}

/** Extract PASETO token from the access-token cookie in the upgrade request. */
private String extractTokenFromCookie(ServerHttpRequest request) {
    List<String> cookieHeaders = request.getHeaders().get("Cookie");
    if (cookieHeaders == null) return null;

    String cookieName = "tacticl-access-token";
    for (String header : cookieHeaders) {
        for (String cookie : header.split(";")) {
            String trimmed = cookie.trim();
            if (trimmed.startsWith(cookieName + "=")) {
                return trimmed.substring(cookieName.length() + 1);
            }
        }
    }
    return null;
}
```

Add import at top of file:
```java
import java.util.List;
```

- [ ] **Step 2: Build and verify**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-core
./gradlew build -x test
```

- [ ] **Step 3: Commit**

```bash
git add service/service-agent/src/main/java/io/strategiz/social/service/agent/websocket/WebSocketAuthInterceptor.java
git commit -m "feat: add cookie extraction to WebSocket auth interceptor"
```

### Task 10: Add issuer/audience validation to PasetoTokenValidator

**Files:**
- Modify: `cidadel-core/framework/framework-authorization/src/main/java/io/cidadel/framework/authorization/config/AuthorizationProperties.java`
- Modify: `cidadel-core/framework/framework-authorization/src/main/java/io/cidadel/framework/authorization/validator/PasetoTokenValidator.java`

- [ ] **Step 1: Add expectedIssuer and expectedAudience to AuthorizationProperties**

In `AuthorizationProperties.java`, add after the `accessTokenCookie` field:

```java
/** Expected token issuer. If set, tokens with a different issuer are rejected. */
private String expectedIssuer;

/** Expected token audience. If set, tokens with a different audience are rejected. */
private String expectedAudience;

public String getExpectedIssuer() {
    return expectedIssuer;
}

public void setExpectedIssuer(String expectedIssuer) {
    this.expectedIssuer = expectedIssuer;
}

public String getExpectedAudience() {
    return expectedAudience;
}

public void setExpectedAudience(String expectedAudience) {
    this.expectedAudience = expectedAudience;
}
```

- [ ] **Step 2: Add claim validation to PasetoTokenValidator.validateAndExtract()**

In `PasetoTokenValidator.java`, update the `validateAndExtract` method (line 170) to validate issuer and audience:

```java
public Optional<AuthenticatedUser> validateAndExtract(String token) {
    try {
        Map<String, Object> claims = parseToken(token);

        // Validate issuer if configured
        if (expectedIssuer != null && !expectedIssuer.isEmpty()) {
            String iss = (String) claims.get("iss");
            if (!expectedIssuer.equals(iss)) {
                log.debug("Token issuer mismatch: expected={}, got={}", expectedIssuer, iss);
                return Optional.empty();
            }
        }

        // Validate audience if configured
        if (expectedAudience != null && !expectedAudience.isEmpty()) {
            String aud = (String) claims.get("aud");
            if (!expectedAudience.equals(aud)) {
                log.debug("Token audience mismatch: expected={}, got={}", expectedAudience, aud);
                return Optional.empty();
            }
        }

        return Optional.of(extractAuthenticatedUser(claims));
    } catch (PasetoException e) {
        log.debug("Token validation failed: {}", e.getMessage());
        return Optional.empty();
    }
}
```

Add fields to the class (near the key fields):

```java
private String expectedIssuer;
private String expectedAudience;
```

Initialize them in `init()` (or via constructor injection from `AuthorizationProperties`). If the validator already receives `AuthorizationProperties`, read from there:

```java
this.expectedIssuer = properties != null ? properties.getExpectedIssuer() : null;
this.expectedAudience = properties != null ? properties.getExpectedAudience() : null;
```

Check how the validator is currently instantiated — if it reads properties, wire it through. If it doesn't have access to properties, add a constructor parameter.

- [ ] **Step 3: Build and verify**

```bash
cd /Users/cuztomizer/Documents/GitHub/tacticl-core
./gradlew build -x test
```

- [ ] **Step 4: Commit**

```bash
git add cidadel-core/framework/framework-authorization/
git commit -m "feat: add issuer and audience validation to PasetoTokenValidator"
```

### Task 11: Configure tacticl-core properties for issuer/audience and CORS

**Files:**
- Modify: `tacticl-core/application-api/src/main/resources/application.properties`
- Modify: `tacticl-core/application-api/src/main/resources/application-prod.properties`

- [ ] **Step 1: Add issuer/audience to base properties**

In `application.properties`, add near the existing `cidadel.authorization.access-token-cookie` line:

```properties
# Token validation — only accept tokens from cidadel IDP
cidadel.authorization.expected-issuer=cidadel.io
cidadel.authorization.expected-audience=tacticl
```

- [ ] **Step 2: Add auth-api to CORS allowed origins in prod**

In `application-prod.properties`, update line 46:

```properties
# Before:
cidadel.cors.allowed-origin-patterns=https://tacticl\\.ai,https://auth\\.tacticl\\.ai

# After:
cidadel.cors.allowed-origin-patterns=https://tacticl\\.ai,https://auth\\.tacticl\\.ai,https://auth-api\\.tacticl\\.ai
```

Also update WebSocket CORS (line 49) to include tacticl.ai:

```properties
tacticl.websocket.allowed-origins=https://tacticl.ai,https://www.tacticl.ai,https://tacticl.io,https://www.tacticl.io
```

- [ ] **Step 3: Build**

```bash
./gradlew build -x test
```

- [ ] **Step 4: Commit**

```bash
git add application-api/src/main/resources/application.properties application-api/src/main/resources/application-prod.properties
git commit -m "feat: configure issuer/audience validation and CORS for cookie auth"
```

### Task 12: Deploy tacticl-core

- [ ] **Step 1: Deploy to prod**

```bash
gcloud builds submit --config deployment/cloudbuild/cloudbuild-prod.yaml --project tacticl .
```

- [ ] **Step 2: Fix IAM if needed**

```bash
gcloud beta run services add-iam-policy-binding --region=us-east1 --member=allUsers --role=roles/run.invoker tacticl-core --project tacticl
```

- [ ] **Step 3: Verify SSL on auth-api.tacticl.ai**

```bash
curl -s -o /dev/null -w "%{http_code}" https://auth-api.tacticl.ai/v1/auth/session/check
```

Expected: 401 or 403 (no cookie = unauthenticated, but service responds)

---

## Chunk 4: End-to-End Verification

### Task 13: Test the complete flow

- [ ] **Step 1: Clear all site data for tacticl.ai**

In Chrome DevTools → Application → Storage → Clear site data

- [ ] **Step 2: Visit tacticl.ai/chat**

Expected: Redirects to `auth.tacticl.ai/signin?redirect=...`

- [ ] **Step 3: Sign in with Google**

Expected: OAuth flow → redirects back to `tacticl.ai/chat?auth_token=...`

- [ ] **Step 4: Verify token exchange**

In Chrome DevTools → Network tab, look for:
- `POST auth-api.tacticl.ai/v1/auth/token/exchange` — should return 200
- Response should include `Set-Cookie: tacticl-access-token=...; HttpOnly; Secure; SameSite=Lax; Domain=.tacticl.ai`

- [ ] **Step 5: Verify chat works**

Expected: Chat loads, can send messages, no 401 errors

- [ ] **Step 6: Verify WebSocket connects**

In Chrome DevTools → Network → WS tab:
- WebSocket upgrade to `wss://api.tacticl.ai/ws/user` (no `?token=` in URL)
- Cookie header should include `tacticl-access-token`

- [ ] **Step 7: Refresh the page**

Expected: Still authenticated (cookies persist, session check succeeds)

- [ ] **Step 8: Verify no localStorage tokens**

In Chrome DevTools → Application → Local Storage → tacticl.ai:
- Should NOT have `tacticl-auth-token` or `tacticl-user-id`

- [ ] **Step 9: Test logout**

Click logout → should call `/v1/auth/sessions/revoke` → redirect to home → cookies cleared

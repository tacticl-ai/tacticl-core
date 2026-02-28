# Cidadel Identity Service — Separation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extract authentication into standalone `cidadel-core` (Java) and `cidadel-web` (React) services, serving both Tacticl and Strategiz with domain-aware branding. Identity-core writes directly to each product's Firestore — no separate identity datastore.

**Architecture:** A new identity service (Cidadel) owns signup, signin, sessions, passkeys, OAuth, and token issuance. It detects the product from the requesting domain and writes user/auth data directly to the correct product Firestore (tacticl or strategiz-io). Both product backends validate the same PASETO tokens (shared keys). Products keep their existing user entities — identity-core writes to them directly. This is Option 2 architecture, ideal for ≤4 products; if scaling beyond 4, migrate to Option 1 (centralized identity datastore).

**Tech Stack:** Java 21 / Spring Boot 3.5 / Gradle 8.12 (backend), React 19 / Vite / MUI 7 (frontend), PASETO v4.local tokens, Firestore (multi-project routing), Vault, Firebase Hosting, Cloud Run.

**Repos:** Under `cuztomizer` GitHub account: `cidadel-core`, `cidadel-web`

---

## Current State Summary

```
auth.tacticl.ai & auth.strategiz.io
  → Same strategiz frontend build (no domain detection)
  → Same strategiz-core backend (application-auth)
  → Same Firestore (strategiz-io)
  → PASETO tokens: aud=strategiz, iss=strategiz.ai (no product field)

Problems:
  1. Tacticl users created in Strategiz Firestore (wrong datastore)
  2. No domain-aware branding (Strategiz favicon on auth.tacticl.ai)
  3. Page title hardcoded ("Strategiz", not dynamic per route)
  4. No product field in tokens
  5. Single auth deployment for both products
```

## Target State

```
auth.tacticl.ai → cidadel-web (Tacticl branding) → cidadel-core → tacticl Firestore
auth.strategiz.io → cidadel-web (Strategiz branding) → cidadel-core → strategiz-io Firestore

PASETO tokens: { sub: userId, aud: "tacticl"|"strategiz", iss: "cidadel" }

NO separate identity datastore. cidadel-core writes user + auth entities
directly into each product's Firestore. Same userId format across products.

Option 2 architecture — direct multi-Firestore writes. Revisit if >4 products.
```

## cidadel-core Module Structure

Nested Gradle modules mirroring strategiz-core's Maven parent/child hierarchy:

```
cidadel-core/
├── settings.gradle.kts                          # Root — declares all nested modules
├── build.gradle.kts                             # Root — shared config for all subprojects
├── gradle.properties                            # JVM args
├── gradle/
│   └── libs.versions.toml                       # Version catalog
│
├── application/                                 # Spring Boot entry point (assembler)
│   ├── build.gradle.kts                         # Pulls in all service-* modules
│   └── src/main/java/io/cidadel/identity/
│       └── application/
│           ├── IdentityApplication.java
│           └── config/
│               ├── CorsConfig.java
│               ├── SecurityConfig.java
│               ├── ProductContext.java
│               ├── ProductDetectionFilter.java
│               ├── ProductFirestoreConfig.java
│               └── ProductAwareFirestore.java
│
├── service/                                     # ── Service layer (REST controllers) ──
│   ├── build.gradle.kts                         # Intermediate parent — shared service config
│   └── service-auth/
│       ├── build.gradle.kts                     # Depends on business:business-auth, data:data-*
│       └── src/main/java/io/cidadel/identity/
│           └── service/auth/
│               ├── controller/
│               │   ├── signup/                  # EmailSignupController
│               │   ├── passkey/                 # Registration, Authentication, Management
│               │   ├── oauth/                   # Google, Facebook signin/signup
│               │   ├── session/                 # SessionController, SignOutController
│               │   ├── recovery/                # AccountRecoveryController
│               │   ├── smsotp/                  # Registration, Authentication
│               │   ├── totp/                    # Registration, Authentication
│               │   └── sso/                     # SsoRedirectController
│               ├── dto/
│               │   ├── request/
│               │   └── response/
│               └── exception/
│
├── business/                                    # ── Business layer (domain logic) ──
│   ├── build.gradle.kts                         # Intermediate parent — shared business config
│   └── business-auth/
│       ├── build.gradle.kts                     # Depends on data:data-*, client:client-*
│       └── src/main/java/io/cidadel/identity/
│           └── business/auth/
│               └── service/
│                   ├── AccountCreationService.java
│                   ├── EmailSignupService.java
│                   ├── SignupTokenService.java
│                   ├── PasskeyService.java
│                   ├── SessionService.java
│                   ├── OAuthService.java
│                   ├── TotpService.java
│                   ├── SmsOtpService.java
│                   └── AccountRecoveryService.java
│
├── data/                                        # ── Data layer (entities + repositories) ──
│   ├── build.gradle.kts                         # Intermediate parent — shared data config
│   ├── data-user/
│   │   ├── build.gradle.kts                     # Depends on framework-exception, Firestore
│   │   └── src/main/java/io/cidadel/identity/
│   │       └── data/user/
│   │           ├── entity/
│   │           │   ├── UserEntity.java
│   │           │   ├── UserProfileEntity.java
│   │           │   ├── EmailReservationEntity.java
│   │           │   ├── EmailReservationStatus.java
│   │           │   └── SsoRelayToken.java
│   │           └── repository/
│   │               ├── UserRepository.java
│   │               ├── UserRepositoryImpl.java
│   │               ├── EmailReservationRepository.java
│   │               └── EmailReservationRepositoryImpl.java
│   ├── data-auth/
│   │   ├── build.gradle.kts
│   │   └── src/main/java/io/cidadel/identity/
│   │       └── data/auth/
│   │           ├── entity/
│   │           │   ├── AuthenticationMethodEntity.java
│   │           │   ├── AuthenticationMethodType.java
│   │           │   ├── OtpCodeEntity.java
│   │           │   ├── SmsOtpCodeEntity.java
│   │           │   ├── PushAuthRequestEntity.java
│   │           │   ├── PushSubscriptionEntity.java
│   │           │   ├── RecoveryRequestEntity.java
│   │           │   └── passkey/
│   │           │       └── PasskeyCredentialEntity.java
│   │           ├── model/
│   │           │   ├── PasskeyChallenge.java
│   │           │   ├── PasskeyCredential.java
│   │           │   └── PasetoToken.java
│   │           └── repository/
│   └── data-session/
│       ├── build.gradle.kts
│       └── src/main/java/io/cidadel/identity/
│           └── data/session/
│               ├── entity/
│               └── repository/
│
├── client/                                      # ── Client layer (external API clients) ──
│   ├── build.gradle.kts                         # Intermediate parent — shared client config
│   ├── client-google/                           # Google OAuth API
│   │   └── build.gradle.kts
│   ├── client-facebook/                         # Facebook OAuth API
│   │   └── build.gradle.kts
│   └── client-sms/                              # SMS OTP (Twilio/Firebase)
│       └── build.gradle.kts
│
└── deployment/
    └── cloudbuild/
        ├── cloudbuild-prod.yaml
        └── cloudbuild-qa.yaml
```

### Dependency Flow (same rules as strategiz-core)

```
application
  ↓ (imports everything)

service:service-*  →  business:business-*
                  ↘ data:data-*

business:business-*  →  client:client-*
                    ↘ data:data-*

client:client-*  →  (frameworks only, via GitHub Packages)

data:data-*  →  (frameworks only, via GitHub Packages)
```

### Package Naming Convention

```
io.cidadel.identity.{layer}.{module}

Examples:
  io.cidadel.identity.application.config
  io.cidadel.identity.service.auth.controller.signup
  io.cidadel.identity.service.auth.dto.request
  io.cidadel.identity.business.auth.service
  io.cidadel.identity.data.user.entity
  io.cidadel.identity.data.user.repository
  io.cidadel.identity.data.auth.entity
  io.cidadel.identity.data.session.entity
  io.cidadel.identity.client.google
  io.cidadel.identity.client.facebook
```

---

## Phase 1: cidadel-core (Java Backend)

### Task 1: Create cidadel-core repo with nested Gradle structure

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `application/build.gradle.kts`
- Create: `service/build.gradle.kts` (intermediate parent)
- Create: `service/service-auth/build.gradle.kts`
- Create: `business/build.gradle.kts` (intermediate parent)
- Create: `business/business-auth/build.gradle.kts`
- Create: `data/build.gradle.kts` (intermediate parent)
- Create: `data/data-user/build.gradle.kts`
- Create: `data/data-auth/build.gradle.kts`
- Create: `data/data-session/build.gradle.kts`
- Create: `client/build.gradle.kts` (intermediate parent)
- Create: `client/client-google/build.gradle.kts`
- Create: `client/client-facebook/build.gradle.kts`
- Create: `client/client-sms/build.gradle.kts`
- Create: `application/src/main/java/io/cidadel/identity/application/IdentityApplication.java`

**Step 1: Create GitHub repo**
```bash
gh repo create cuztomizer/cidadel-core --private --clone
cd cidadel-core
```

**Step 2: Create Gradle wrapper**
```bash
gradle wrapper --gradle-version 8.12
```

**Step 3: Create gradle.properties**
```properties
org.gradle.jvmargs=-Xmx4g
```

**Step 4: Create settings.gradle.kts**
```kotlin
rootProject.name = "cidadel-core"

// Nested module includes — mirrors Maven parent/child hierarchy
include(
    // Application (assembler)
    "application",

    // Service layer (REST controllers)
    "service:service-auth",

    // Business layer (domain logic)
    "business:business-auth",

    // Data layer (entities + repositories)
    "data:data-user",
    "data:data-auth",
    "data:data-session",

    // Client layer (external API clients)
    "client:client-google",
    "client:client-facebook",
    "client:client-sms"
)
```

**Step 5: Create root build.gradle.kts**
```kotlin
plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

group = "io.cidadel.identity"
version = "0.1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        // Strategiz shared framework libraries from GitHub Packages
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/strategiz-io/strategiz-core")
            credentials {
                username = project.findProperty("gpr.user") as String?
                    ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String?
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.7")
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
```

**Step 6: Create gradle/libs.versions.toml**
```toml
[versions]
# Core
spring-boot = "3.5.7"
java = "21"

# Strategiz shared libraries (from GitHub Packages)
strategiz = "1.0-SNAPSHOT"

# External
google-cloud-firestore = "3.28.0"
jackson = "2.18.0"
bucket4j = "8.10.1"
springdoc = "2.8.0"
httpclient = "4.5.14"

# Testing
junit = "5.12.2"
junit-platform = "1.12.2"
mockito = "4.11.0"

[libraries]
# Spring Boot starters
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }
spring-boot-starter-validation = { module = "org.springframework.boot:spring-boot-starter-validation" }
spring-boot-starter-security = { module = "org.springframework.boot:spring-boot-starter-security" }

# Strategiz shared frameworks (consumed from GitHub Packages)
strategiz-framework-authorization = { module = "io.strategiz:framework-authorization", version.ref = "strategiz" }
strategiz-framework-token-issuance = { module = "io.strategiz:framework-token-issuance", version.ref = "strategiz" }
strategiz-framework-exception = { module = "io.strategiz:framework-exception", version.ref = "strategiz" }
strategiz-framework-logging = { module = "io.strategiz:framework-logging", version.ref = "strategiz" }
strategiz-framework-secrets = { module = "io.strategiz:framework-secrets", version.ref = "strategiz" }
strategiz-framework-api-docs = { module = "io.strategiz:framework-api-docs", version.ref = "strategiz" }
strategiz-client-base = { module = "io.strategiz:client-framework-base", version.ref = "strategiz" }

# Google Cloud
google-cloud-firestore = { module = "com.google.cloud:google-cloud-firestore", version.ref = "google-cloud-firestore" }

# Jackson
jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson" }
jackson-annotations = { module = "com.fasterxml.jackson.core:jackson-annotations", version.ref = "jackson" }

# HTTP
httpclient = { module = "org.apache.httpcomponents:httpclient", version.ref = "httpclient" }

# OpenAPI
springdoc-openapi = { module = "org.springdoc:springdoc-openapi-starter-webmvc-ui", version.ref = "springdoc" }

# Rate limiting
bucket4j-core = { module = "com.bucket4j:bucket4j-core", version.ref = "bucket4j" }

# Testing
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher", version.ref = "junit-platform" }
mockito-core = { module = "org.mockito:mockito-core", version.ref = "mockito" }

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.7" }
```

**Step 7: Create intermediate parent build.gradle.kts files**

Each intermediate parent applies shared config to its children (like Maven parent POMs).

`service/build.gradle.kts`:
```kotlin
// Intermediate parent for service-* modules
// Common service dependencies applied to all children
subprojects {
    dependencies {
        // All service modules get framework authorization + exception
        implementation(rootProject.libs.strategiz.framework.authorization)
        implementation(rootProject.libs.strategiz.framework.exception)
        implementation(rootProject.libs.strategiz.framework.logging)
        implementation(rootProject.libs.strategiz.framework.api.docs)
        implementation(rootProject.libs.spring.boot.starter.web)
        implementation(rootProject.libs.spring.boot.starter.validation)
        implementation(rootProject.libs.springdoc.openapi)

        testImplementation(rootProject.libs.spring.boot.starter.test)
        testImplementation(rootProject.libs.junit.jupiter)
    }
}
```

`business/build.gradle.kts`:
```kotlin
// Intermediate parent for business-* modules
subprojects {
    dependencies {
        implementation(rootProject.libs.strategiz.framework.exception)
        implementation(rootProject.libs.strategiz.framework.logging)
        implementation(rootProject.libs.spring.boot.starter.web)
        implementation(rootProject.libs.jackson.databind)

        testImplementation(rootProject.libs.spring.boot.starter.test)
        testImplementation(rootProject.libs.junit.jupiter)
        testImplementation(rootProject.libs.mockito.core)
    }
}
```

`data/build.gradle.kts`:
```kotlin
// Intermediate parent for data-* modules
subprojects {
    dependencies {
        implementation(rootProject.libs.strategiz.framework.exception)
        implementation(rootProject.libs.strategiz.framework.logging)
        implementation(rootProject.libs.spring.boot.starter.web)
        implementation(rootProject.libs.google.cloud.firestore)
        implementation(rootProject.libs.jackson.databind)
        implementation(rootProject.libs.jackson.annotations)

        testImplementation(rootProject.libs.spring.boot.starter.test)
        testImplementation(rootProject.libs.junit.jupiter)
    }
}
```

`client/build.gradle.kts`:
```kotlin
// Intermediate parent for client-* modules
subprojects {
    dependencies {
        implementation(rootProject.libs.strategiz.framework.exception)
        implementation(rootProject.libs.strategiz.framework.secrets)
        implementation(rootProject.libs.strategiz.client.base)
        implementation(rootProject.libs.spring.boot.starter.web)
        implementation(rootProject.libs.httpclient)
        implementation(rootProject.libs.jackson.databind)

        testImplementation(rootProject.libs.spring.boot.starter.test)
        testImplementation(rootProject.libs.junit.jupiter)
        testImplementation(rootProject.libs.mockito.core)
    }
}
```

**Step 8: Create leaf module build.gradle.kts files**

`application/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // All internal modules
    implementation(project(":service:service-auth"))
    implementation(project(":business:business-auth"))
    implementation(project(":data:data-user"))
    implementation(project(":data:data-auth"))
    implementation(project(":data:data-session"))
    implementation(project(":client:client-google"))
    implementation(project(":client:client-facebook"))
    implementation(project(":client:client-sms"))

    // Strategiz shared frameworks
    implementation(libs.strategiz.framework.authorization)
    implementation(libs.strategiz.framework.token.issuance)
    implementation(libs.strategiz.framework.exception)
    implementation(libs.strategiz.framework.logging)
    implementation(libs.strategiz.framework.secrets)
    implementation(libs.strategiz.framework.api.docs)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)

    // Firestore (multi-project config lives here)
    implementation(libs.google.cloud.firestore)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
}

springBoot {
    mainClass = "io.cidadel.identity.application.IdentityApplication"
}
```

`service/service-auth/build.gradle.kts`:
```kotlin
// Inherits common service deps from service/build.gradle.kts
dependencies {
    implementation(project(":business:business-auth"))
    implementation(project(":data:data-user"))
    implementation(project(":data:data-auth"))
    implementation(project(":data:data-session"))
}
```

`business/business-auth/build.gradle.kts`:
```kotlin
// Inherits common business deps from business/build.gradle.kts
dependencies {
    implementation(project(":data:data-user"))
    implementation(project(":data:data-auth"))
    implementation(project(":data:data-session"))
    implementation(project(":client:client-google"))
    implementation(project(":client:client-facebook"))
    implementation(project(":client:client-sms"))

    implementation(rootProject.libs.strategiz.framework.secrets)
    implementation(rootProject.libs.strategiz.framework.token.issuance)
}
```

`data/data-user/build.gradle.kts`:
```kotlin
// Inherits common data deps from data/build.gradle.kts
// No additional dependencies — leaf data module
```

`data/data-auth/build.gradle.kts`:
```kotlin
// Inherits common data deps from data/build.gradle.kts
dependencies {
    implementation(project(":data:data-user"))
}
```

`data/data-session/build.gradle.kts`:
```kotlin
// Inherits common data deps from data/build.gradle.kts
dependencies {
    implementation(project(":data:data-user"))
}
```

`client/client-google/build.gradle.kts`:
```kotlin
// Inherits common client deps from client/build.gradle.kts
// Google OAuth specific — no additional deps needed beyond parent
```

`client/client-facebook/build.gradle.kts`:
```kotlin
// Inherits common client deps from client/build.gradle.kts
```

`client/client-sms/build.gradle.kts`:
```kotlin
// Inherits common client deps from client/build.gradle.kts
```

**Step 9: Create application entry point**
```java
package io.cidadel.identity.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.cidadel.identity")
@EnableScheduling
public class IdentityApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }
}
```

**Step 10: Verify build compiles**
```bash
./gradlew build -x test
```
Expected: BUILD SUCCESSFUL (all modules compile, no source yet in leaf modules)

**Step 11: Commit**
```bash
git add -A && git commit -m "chore: initialize cidadel-core with nested Gradle module structure"
```

---

### Task 2: Extract data-user module

Copy from `strategiz-core/data/data-user/` → `cidadel-core/data/data-user/`

**Target path:** `data/data-user/src/main/java/io/cidadel/identity/data/user/`

**Files to copy:**
- `entity/UserEntity.java` — Root user document (`users/{userId}`)
- `entity/UserProfileEntity.java` — Embedded profile (name, email, tier, role)
- `entity/EmailReservationEntity.java` — Email uniqueness enforcement
- `entity/EmailReservationStatus.java` — Enum: RESERVED, CONFIRMED, RELEASED
- `entity/SsoRelayToken.java` — SSO token relay
- `repository/UserRepository.java` — Interface
- `repository/UserRepositoryImpl.java` — Firestore implementation
- `repository/EmailReservationRepository.java`
- `repository/EmailReservationRepositoryImpl.java`

**Modification — Add product field to UserEntity:**
```java
@PropertyName("product")
private String product; // "tacticl" or "strategiz"
```

**Key point:** These repositories will use the product-aware Firestore routing (Task 5) to write to the correct product's Firestore. No separate identity datastore.

**Step 1: Copy files, update package names to `io.cidadel.identity.data.user`**

**Step 2: Add `product` field to UserEntity**

**Step 3: Write test for UserEntity with product field**
Test path: `data/data-user/src/test/java/io/cidadel/identity/data/user/`

**Step 4: Run test**
```bash
./gradlew :data:data-user:test
```

**Step 5: Commit**
```bash
git commit -m "feat(data-user): extract user entities with product field"
```

---

### Task 3: Extract data-auth module

Copy from `strategiz-core/data/data-auth/` → `cidadel-core/data/data-auth/`

**Target path:** `data/data-auth/src/main/java/io/cidadel/identity/data/auth/`

**Files to copy (all of them):**
- `entity/AuthenticationMethodEntity.java` — Stored in `users/{userId}/security`
- `entity/AuthenticationMethodType.java` — Enum: PASSKEY, TOTP, SMS_OTP, EMAIL_OTP, OAUTH_*, DEVICE_TRUST
- `entity/OtpCodeEntity.java`
- `entity/SmsOtpCodeEntity.java`
- `entity/passkey/PasskeyCredentialEntity.java`
- `entity/PushAuthRequestEntity.java` + status enum
- `entity/PushSubscriptionEntity.java`
- `entity/RecoveryRequestEntity.java` + status enum
- All repositories (interfaces + Firestore impls)
- All models (PasskeyChallenge, PasskeyCredential, PasetoToken, etc.)

**Key point:** Auth data (passkeys, TOTP, etc.) lives inside each product's Firestore under `users/{userId}/security`. cidadel-core writes these directly into the product's Firestore via multi-project routing.

**Step 1: Copy all files, update package names to `io.cidadel.identity.data.auth`**

**Step 2: Verify compilation**
```bash
./gradlew :data:data-auth:build -x test
```

**Step 3: Commit**
```bash
git commit -m "feat(data-auth): extract auth method entities and repositories"
```

---

### Task 4: Extract data-session module

Copy from `strategiz-core/data/data-session/` → `cidadel-core/data/data-session/`

**Target path:** `data/data-session/src/main/java/io/cidadel/identity/data/session/`

**Step 1: Copy all session entities and repositories, update package names to `io.cidadel.identity.data.session`**

**Step 2: Verify compilation**
```bash
./gradlew :data:data-session:build -x test
```

**Step 3: Commit**
```bash
git commit -m "feat(data-session): extract session entities and repositories"
```

---

### Task 5: Extract client modules

Copy OAuth and SMS clients from strategiz-core.

**Step 1: Extract client-google**

Copy from `strategiz-core/client/client-google/` → `cidadel-core/client/client-google/`

Target path: `client/client-google/src/main/java/io/cidadel/identity/client/google/`

Files: OAuth client, config, DTOs. Update package names.

**Step 2: Extract client-facebook**

Copy from `strategiz-core/client/client-facebook/` → `cidadel-core/client/client-facebook/`

Target path: `client/client-facebook/src/main/java/io/cidadel/identity/client/facebook/`

**Step 3: Extract client-sms**

Copy from `strategiz-core/client/client-firebase-sms/` (or `client-sms/`) → `cidadel-core/client/client-sms/`

Target path: `client/client-sms/src/main/java/io/cidadel/identity/client/sms/`

**Step 4: Verify all clients compile**
```bash
./gradlew :client:client-google:build -x test
./gradlew :client:client-facebook:build -x test
./gradlew :client:client-sms:build -x test
```

**Step 5: Commit**
```bash
git commit -m "feat(client): extract Google OAuth, Facebook OAuth, and SMS clients"
```

---

### Task 6: Extract business-auth module

Copy auth business logic from strategiz-core.

**Target path:** `business/business-auth/src/main/java/io/cidadel/identity/business/auth/`

**Services to extract from strategiz-core (service/service-auth + business/business-token-auth):**
- `AccountCreationService.java` — Atomic user + email reservation + auth method creation
- `EmailSignupService.java` — OTP send/verify
- `SignupTokenService.java` — Signup token cookie management
- `PasskeyService.java` — WebAuthn registration/authentication
- `SessionService.java` — Session creation, validation, refresh
- `OAuthService.java` — Google/Facebook OAuth flows
- `TotpService.java` — TOTP registration/verification
- `SmsOtpService.java` — SMS OTP flows
- `AccountRecoveryService.java` — Account recovery

**Step 1: Copy all business services, update package names to `io.cidadel.identity.business.auth.service`**

**Step 2: Verify compilation against data and client modules**
```bash
./gradlew :business:business-auth:build -x test
```

**Step 3: Commit**
```bash
git commit -m "feat(business-auth): extract auth business services"
```

---

### Task 7: Extract service-auth module

Copy REST controllers from strategiz-core.

**Target path:** `service/service-auth/src/main/java/io/cidadel/identity/service/auth/`

**Controllers to extract:**
- `controller/signup/EmailSignupController.java` — POST `/v1/auth/signup/email/initiate`, `/verify`, `/status`
- `controller/passkey/PasskeyRegistrationController.java`
- `controller/passkey/PasskeyAuthenticationController.java`
- `controller/passkey/PasskeyManagementController.java`
- `controller/oauth/GoogleOauthSignUpController.java`
- `controller/oauth/GoogleOauthSignInController.java`
- `controller/oauth/FacebookOauthSignUpController.java`
- `controller/oauth/FacebookOauthSignInController.java`
- `controller/session/SessionController.java`
- `controller/session/SignOutController.java`
- `controller/recovery/AccountRecoveryController.java`
- `controller/smsotp/SmsOtpRegistrationController.java`
- `controller/smsotp/SmsOtpAuthenticationController.java`
- `controller/totp/TotpRegistrationController.java`
- `controller/totp/TotpAuthenticationController.java`
- `controller/sso/SsoRedirectController.java` — **NEW** (see Task 10)
- All DTOs (request/ + response/)

**Step 1: Copy all controllers and DTOs, update package names to `io.cidadel.identity.service.auth`**

**Step 2: Verify compilation against business-auth module**
```bash
./gradlew :service:service-auth:build -x test
```

**Step 3: Commit**
```bash
git commit -m "feat(service-auth): extract all auth controllers and DTOs"
```

---

### Task 8: Add multi-product Firestore routing

Core capability of cidadel — detect which product the request is for and route reads/writes to the correct product's Firestore. Lives in the `application` module since it's cross-cutting infrastructure.

**Files (all in application module):**
- Create: `application/src/main/java/io/cidadel/identity/application/config/ProductContext.java`
- Create: `application/src/main/java/io/cidadel/identity/application/config/ProductDetectionFilter.java`
- Create: `application/src/main/java/io/cidadel/identity/application/config/ProductFirestoreConfig.java`
- Create: `application/src/main/java/io/cidadel/identity/application/config/ProductAwareFirestore.java`

**Step 1: Create ProductContext enum**
```java
package io.cidadel.identity.application.config;

public enum ProductContext {
    TACTICL("tacticl", "tacticl"),
    STRATEGIZ("strategiz", "strategiz-io");

    private final String id;
    private final String firestoreProjectId;

    ProductContext(String id, String firestoreProjectId) {
        this.id = id;
        this.firestoreProjectId = firestoreProjectId;
    }

    public String getId() { return id; }
    public String getFirestoreProjectId() { return firestoreProjectId; }

    public static ProductContext fromOrigin(String origin) {
        if (origin != null && origin.contains("tacticl")) {
            return TACTICL;
        }
        return STRATEGIZ;
    }
}
```

**Step 2: Create ProductDetectionFilter (servlet filter)**
```java
@Component
public class ProductDetectionFilter extends OncePerRequestFilter {
    private static final ThreadLocal<ProductContext> CURRENT_PRODUCT = new ThreadLocal<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null) origin = request.getHeader("Referer");
        ProductContext product = ProductContext.fromOrigin(origin);
        CURRENT_PRODUCT.set(product);
        try {
            filterChain.doFilter(request, response);
        } finally {
            CURRENT_PRODUCT.remove();
        }
    }

    public static ProductContext getCurrentProduct() {
        ProductContext ctx = CURRENT_PRODUCT.get();
        return ctx != null ? ctx : ProductContext.STRATEGIZ;
    }
}
```

**Step 3: Create ProductFirestoreConfig — dual Firestore beans**
```java
@Configuration
public class ProductFirestoreConfig {
    @Bean("tacticlFirestore")
    public Firestore tacticlFirestore() {
        return FirestoreOptions.newBuilder()
            .setProjectId("tacticl")
            .build().getService();
    }

    @Bean("strategizFirestore")
    public Firestore strategizFirestore() {
        return FirestoreOptions.newBuilder()
            .setProjectId("strategiz-io")
            .build().getService();
    }
}
```

**Step 4: Create ProductAwareFirestore — routing proxy**
```java
@Component
@Primary
public class ProductAwareFirestore {
    private final Firestore tacticlFirestore;
    private final Firestore strategizFirestore;

    public ProductAwareFirestore(
        @Qualifier("tacticlFirestore") Firestore tacticl,
        @Qualifier("strategizFirestore") Firestore strategiz
    ) {
        this.tacticlFirestore = tacticl;
        this.strategizFirestore = strategiz;
    }

    public Firestore getFirestore() {
        return switch (ProductDetectionFilter.getCurrentProduct()) {
            case TACTICL -> tacticlFirestore;
            case STRATEGIZ -> strategizFirestore;
        };
    }
}
```

All repositories inject `ProductAwareFirestore` instead of raw `Firestore`. Each request automatically routes to the correct product's Firestore.

**Step 5: Update all repository impls in data-user, data-auth, data-session to use ProductAwareFirestore**

Note: `ProductAwareFirestore` lives in `application` module but needs to be injectable into `data-*` modules. Two approaches:
- Option A: Move `ProductAwareFirestore` + `ProductDetectionFilter` into a shared `data/data-framework-base` module
- Option B: Define an interface in data layer, implement in application (cleaner dependency direction)

Recommended: Option A — create `data/data-framework-base/` with the Firestore routing abstraction, similar to strategiz-core's `data-framework-base`.

**Step 6: Write integration test verifying product detection from Origin header**
- Origin: `https://auth.tacticl.ai` → writes to tacticl Firestore
- Origin: `https://auth.strategiz.io` → writes to strategiz-io Firestore

**Step 7: Commit**
```bash
git commit -m "feat: add multi-product Firestore routing based on request origin"
```

---

### Task 9: Update PASETO token issuance to include product

**Files:**
- Modify (or fork): `PasetoTokenIssuer.java` from framework-token-issuance

**Step 1: Add product claim to token payload**
```java
// In createAuthenticationToken():
claims.put("product", ProductDetectionFilter.getCurrentProduct().getId());
claims.put("aud", ProductDetectionFilter.getCurrentProduct().getId());
```

**Step 2: Update iss from "strategiz.ai" to "cidadel"**

**Step 3: Write test verifying product claim in issued token**

**Step 4: Commit**
```bash
git commit -m "feat(token): include product claim in PASETO tokens"
```

---

### Task 10: Application config, CORS, SSO endpoint, and deployment

**Files:**
- Create: `application/src/main/resources/application.properties`
- Create: `application/src/main/resources/application-prod.properties`
- Create: `application/src/main/resources/application-qa.properties`
- Create: `application/src/main/java/io/cidadel/identity/application/config/CorsConfig.java`
- Create: `application/src/main/java/io/cidadel/identity/application/config/SecurityConfig.java`
- Create: `service/service-auth/.../controller/sso/SsoRedirectController.java`
- Create: `deployment/cloudbuild/cloudbuild-prod.yaml`
- Create: `deployment/cloudbuild/cloudbuild-qa.yaml`

**Step 1: application.properties**
```properties
server.port=8080
spring.application.name=cidadel-core

# Vault
spring.cloud.vault.uri=${VAULT_ADDR:http://localhost:8200}
spring.cloud.vault.token=${VAULT_TOKEN:root-token}
strategiz.vault.enabled=true
strategiz.vault.secrets-path=secret/cidadel

# OAuth
oauth.providers.google.client-id=${AUTH_GOOGLE_CLIENT_ID:}
oauth.providers.google.client-secret=${AUTH_GOOGLE_CLIENT_SECRET:}
oauth.providers.facebook.client-id=${AUTH_FACEBOOK_CLIENT_ID:}
oauth.providers.facebook.client-secret=${AUTH_FACEBOOK_CLIENT_SECRET:}

# Passkey
passkey.rpId=${PASSKEY_RP_ID:localhost}

# Cookies
app.cookie.domain=${COOKIE_DOMAIN:localhost}
app.cookie.secure=${COOKIE_SECURE:false}
```

**Step 2: application-prod.properties**
```properties
# CORS — both product auth domains
strategiz.cors.allowed-origins=https://auth.tacticl.ai,https://auth.strategiz.io,https://tacticl.ai,https://strategiz.io

# Passkey
passkey.rpId=tacticl.ai

# Cookies
app.cookie.domain=.tacticl.ai
app.cookie.secure=true

# Frontend URLs (for OAuth redirects)
oauth.frontend-url.tacticl=https://auth.tacticl.ai
oauth.frontend-url.strategiz=https://auth.strategiz.io
```

**Step 3: Create SSO redirect endpoint**
```java
package io.cidadel.identity.service.auth.controller.sso;

@RestController
@RequestMapping("/v1/auth/sso")
public class SsoRedirectController {

    @GetMapping("/redirect")
    public void redirect(
        @RequestParam String product,
        @RequestParam String returnUrl,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        // Validate returnUrl is an allowed domain for the product
        // Extract session from cookie
        // Generate short-lived SSO relay token
        // Redirect: returnUrl?auth_token=X&user_id=Y
    }
}
```

**Step 4: CORS config allowing both product domains**

**Step 5: Cloud Build YAML**
- GCP project: `cidadel`
- Artifact Registry: `cidadel-core`
- Cloud Run service: `cidadel-core`
- Region: us-east1

**Step 6: Full build verification**
```bash
./gradlew build
```

**Step 7: Commit**
```bash
git commit -m "feat: add application config, CORS, SSO endpoint, and Cloud Run deployment"
```

---

## Phase 2: cidadel-web (React Frontend)

### Task 11: Create cidadel-web repo with Vite + React + MUI

**Files:**
- Create: `cidadel-web/package.json`
- Create: `cidadel-web/vite.config.ts`
- Create: `cidadel-web/tsconfig.json`
- Create: `cidadel-web/index.html`
- Create: `cidadel-web/firebase.json`
- Create: `cidadel-web/.firebaserc`
- Create: `cidadel-web/src/main.tsx`
- Create: `cidadel-web/src/App.tsx`

**Step 1: Create GitHub repo and initialize Vite project**
```bash
gh repo create cuztomizer/cidadel-web --private --clone
cd cidadel-web
npm create vite@latest . -- --template react-ts
npm install @mui/material @emotion/react @emotion/styled @mui/icons-material
npm install react-router-dom zustand axios date-fns
```

**Step 2: Configure Firebase Hosting**
```json
{
  "hosting": {
    "public": "dist",
    "ignore": ["firebase.json", "**/.*", "**/node_modules/**"],
    "rewrites": [{ "source": "**", "destination": "/index.html" }]
  }
}
```

**Step 3: Set up router with auth routes**
```
/signin         → SignInPage
/signup         → SignUpPage
/sign-in        → redirect to /signin
/sign-up        → redirect to /signup
/oauth/callback → OAuthCallbackPage
```

**Step 4: Commit**
```bash
git commit -m "chore: initialize cidadel-web with Vite, React, MUI"
```

---

### Task 12: Domain-aware branding system

**Files:**
- Create: `src/config/product.ts`
- Create: `src/hooks/useProduct.ts`
- Create: `src/hooks/usePageTitle.ts`
- Create: `public/tacticl-favicon.svg`
- Create: `public/strategiz-favicon.svg`

**Step 1: Create product config**
```typescript
// src/config/product.ts
export type Product = 'tacticl' | 'strategiz';

export interface ProductConfig {
  id: Product;
  name: string;
  apiBaseUrl: string;
  primaryColor: string;
  accentColor: string;
  faviconPath: string;
  font: string;
}

const PRODUCTS: Record<Product, ProductConfig> = {
  tacticl: {
    id: 'tacticl',
    name: 'Tacticl',
    apiBaseUrl: import.meta.env.VITE_API_URL || 'https://cidadel-core-xxx.run.app',
    primaryColor: '#6C63FF',
    accentColor: '#03DAC6',
    faviconPath: '/tacticl-favicon.svg',
    font: 'Inter, sans-serif',
  },
  strategiz: {
    id: 'strategiz',
    name: 'Strategiz',
    apiBaseUrl: import.meta.env.VITE_API_URL || 'https://cidadel-core-xxx.run.app',
    primaryColor: '#39FF14',
    accentColor: '#1976d2',
    faviconPath: '/strategiz-favicon.svg',
    font: 'Orbitron, sans-serif',
  },
};

export function detectProduct(): Product {
  const hostname = window.location.hostname;
  if (hostname.includes('tacticl')) return 'tacticl';
  return 'strategiz';
}

export function getProductConfig(): ProductConfig {
  return PRODUCTS[detectProduct()];
}
```

Note: Both products hit the **same** cidadel-core backend. The `apiBaseUrl` is identical — product routing is determined by the `Origin` header, not by different backends.

**Step 2: Create useProduct hook**
```typescript
export function useProduct() {
  const config = useMemo(() => getProductConfig(), []);
  return config;
}
```

**Step 3: Create usePageTitle hook**
```typescript
export function usePageTitle(page: string) {
  const product = useProduct();
  useEffect(() => {
    document.title = `${page} - ${product.name}`;
    const link = document.querySelector("link[rel~='icon']") as HTMLLinkElement;
    if (link) link.href = product.faviconPath;
  }, [page, product]);
}
```

**Step 4: Copy Tacticl favicon from tacticl-web/public/favicon.svg**

**Step 5: Copy/create Strategiz favicon**

**Step 6: Commit**
```bash
git commit -m "feat: add domain-aware product branding system"
```

---

### Task 13: Extract auth clients and pages from strategiz

**Files:**
- Create: `src/api/client.ts` — Base API client
- Create: `src/api/session.ts` — Session client (from strategiz sessionClient.ts)
- Create: `src/api/passkey.ts` — Passkey client (from strategiz passkeyClient.ts)
- Create: `src/api/oauth.ts` — OAuth client
- Create: `src/api/types.ts` — All auth types
- Create: `src/pages/SignInPage.tsx` — From strategiz
- Create: `src/pages/SignUpPage.tsx` — From strategiz
- Create: `src/pages/OAuthCallbackPage.tsx`
- Create: `src/contexts/AuthContext.tsx` — From strategiz SessionAuthContext
- Create: `src/theme/index.ts` — Product-aware MUI theme

**Step 1: Create base API client**
```typescript
import { getProductConfig } from '../config/product';

class ApiClient {
  private get baseUrl() {
    return getProductConfig().apiBaseUrl;
  }

  async post<T>(path: string, body?: unknown): Promise<T> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: body ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) throw new ApiError(res.status, await res.text());
    return res.json();
  }
  // ... get, put, delete
}

export const api = new ApiClient();
```

**Step 2: Port session, passkey, oauth clients from strategiz**

**Step 3: Create product-aware MUI theme**
```typescript
import { getProductConfig } from '../config/product';

export function createProductTheme() {
  const product = getProductConfig();
  return createTheme({
    palette: {
      mode: 'dark',
      primary: { main: product.primaryColor },
      background: { default: '#121212', paper: '#1E1E1E' },
    },
    typography: { fontFamily: product.font },
  });
}
```

**Step 4: Port SignInPage — add usePageTitle('Sign In')**

**Step 5: Port SignUpPage — add usePageTitle('Sign Up')**

**Step 6: Port AuthContext, add post-auth redirect logic**
```typescript
const params = new URLSearchParams(window.location.search);
const returnUrl = params.get('redirect') || getProductConfig().defaultRedirectUrl;
window.location.href = `${returnUrl}?auth_token=${token}&user_id=${userId}`;
```

**Step 7: Commit**
```bash
git commit -m "feat: extract auth clients, pages, and theme with product branding"
```

---

### Task 14: Firebase Hosting deployment

**Files:**
- Create: `cidadel-web/.firebaserc`
- Create: `cidadel-web/.env.production`
- Create: `cidadel-web/.env.development`

**Step 1: Configure Firebase project `cidadel`**

**Step 2: Set up custom domains**
```bash
firebase hosting:sites:create cidadel-auth
firebase target:apply hosting cidadel-auth cidadel-auth
# Firebase console: add auth.tacticl.ai + auth.strategiz.io → cidadel-auth
```

**Step 3: Environment files**
```bash
# .env.production
VITE_API_URL=https://cidadel-core-xxx-ue.a.run.app

# .env.development
VITE_API_URL=http://localhost:8080
```

**Step 4: Deploy**
```bash
npm run build && firebase deploy --only hosting:cidadel-auth
```

**Step 5: Commit**
```bash
git commit -m "feat: configure Firebase Hosting with custom domains"
```

---

## Phase 3: Product Backend Updates

### Task 15: Update tacticl-core to consume cidadel tokens

**Files:**
- Modify: `tacticl-core/application/src/main/resources/application-prod.properties`
- Verify: `UserProvisioningService.java` already handles JIT user creation

**Step 1: Verify CORS allows cidadel-web domains** (auth.tacticl.ai already in allowlist)

**Step 2: Verify UserProvisioningService creates TacticlUser on first API call**
Already implemented. cidadel-core writes user data to tacticl Firestore, UserProvisioningService creates product-specific TacticlUser entity (preferences, onboarding) on first API call.

**Step 3: Update token validation to accept `iss: "cidadel"`**
Accept both issuers during transition, then only "cidadel" after cutover.

**Step 4: Commit in tacticl-core**
```bash
git commit -m "chore: verify cidadel identity service compatibility"
```

---

### Task 16: Update strategiz frontend to use cidadel-web

**Files:**
- Modify: `strategiz/src/pages/main/LandingPage.tsx`
- Modify: `strategiz/src/App.tsx` — Remove local signin/signup routes
- Remove: `strategiz/src/pages/auth/` (SignInPage, SignUpPage)
- Remove: `strategiz/src/components/auth/` (SignInForm, SignUpForm)
- Remove: `strategiz/src/clients/auth/` (all clients)
- Remove: `strategiz/src/types/auth/` (all types)
- Remove: `strategiz/src/contexts/SessionAuthContext.tsx`

**Step 1: Update LandingPage to redirect to auth.strategiz.io**
```typescript
const AUTH_BASE = 'https://auth.strategiz.io';
const SIGNUP_URL = `${AUTH_BASE}/signup?redirect=${encodeURIComponent(window.location.origin + '/dashboard')}`;
const SIGNIN_URL = `${AUTH_BASE}/signin?redirect=${encodeURIComponent(window.location.origin + '/dashboard')}`;
```

**Step 2: Add token hydration from URL params**
```typescript
const params = new URLSearchParams(window.location.search);
const urlToken = params.get('auth_token');
if (urlToken) {
  localStorage.setItem('strategiz-auth-token', urlToken);
}
```

**Step 3: Remove all auth components, clients, types, context**

**Step 4: Commit in strategiz**
```bash
git commit -m "feat: delegate auth to cidadel-web, remove local auth pages"
```

---

### Task 17: Update strategiz-core backend

**Step 1: Keep application-auth running during migration (backwards compatible)**

**Step 2: Update token validation to accept `iss: "cidadel"` tokens**

**Step 3: Plan deprecation timeline for application-auth module**

**Step 4: Commit**
```bash
git commit -m "chore: prepare strategiz-core for cidadel identity migration"
```

---

## Phase 4: Migration & Cutover

### Task 18: Migrate existing strategiz-io users to tacticl Firestore

Users who signed up via auth.tacticl.ai are currently in strategiz-io Firestore. Since cidadel-core writes directly to each product's Firestore, we need to move these users to the right place.

**Step 1: Write migration script**
```
1. Query strategiz-io Firestore for users who signed up via tacticl
2. For each user:
   a. Create UserEntity in tacticl Firestore (same userId)
   b. Copy auth methods (passkeys, TOTP, etc.) from users/{id}/security
   c. Copy email reservation
   d. Mark user.product = "tacticl" in tacticl Firestore
   e. Optionally soft-delete the user in strategiz-io Firestore
```

**Step 2: Run migration in staging first**

**Step 3: Run in production**

**Step 4: Verify all migrated users can still sign in via auth.tacticl.ai**

---

### Task 19: DNS and domain cutover

**Step 1: Point auth.tacticl.ai to cidadel-web Firebase Hosting**

**Step 2: Point auth.strategiz.io to cidadel-web Firebase Hosting**

**Step 3: Verify both domains show correct branding**
- auth.tacticl.ai → Tacticl logo, purple theme, correct favicon
- auth.strategiz.io → Strategiz logo, neon green theme, correct favicon

**Step 4: Verify signup/signin works on both domains**
- auth.tacticl.ai/signup → cidadel-core writes user to tacticl Firestore
- auth.strategiz.io/signup → cidadel-core writes user to strategiz-io Firestore

**Step 5: Verify SSO redirect works**
- Sign up on auth.tacticl.ai → redirected to tacticl.ai with valid token
- Sign up on auth.strategiz.io → redirected to strategiz.io with valid token

---

## Risk Mitigation

1. **Backwards compatibility**: Keep strategiz-core application-auth running during transition. Old tokens remain valid (same PASETO keys).

2. **Gradual rollout**: Deploy cidadel-core and cidadel-web first, test with staging domains, then cut over DNS.

3. **Rollback plan**: If cidadel-web has issues, repoint DNS back to strategiz frontend. Old auth still works.

4. **Shared PASETO keys**: Both old (strategiz-core) and new (cidadel-core) tokens work on both product backends because they share the same Vault keys.

5. **WebAuthn RP ID challenge**: Passkeys are bound to a domain (rpId). If rpId was `strategiz.io`, passkeys won't work on `auth.tacticl.ai`. Need to verify current rpId config and potentially re-register passkeys. Current prod config shows `passkey.rpId=tacticl.ai` in tacticl-core, so Tacticl passkeys should work. Strategiz passkeys bound to `strategiz.io` should work on `auth.strategiz.io`.

6. **Option 2 scaling**: This architecture (direct multi-Firestore writes) is designed for ≤4 products. If expanding beyond 4 products, migrate to Option 1 (centralized identity datastore with foreign keys in product stores).

---

## Infrastructure Summary

| Resource | Name | Notes |
|----------|------|-------|
| GCP Project | `cidadel` | New project for Cloud Run + Artifact Registry |
| Cloud Run | `cidadel-core` | us-east1, public access |
| Artifact Registry | `cidadel-core` | Docker images |
| Firebase Project | `cidadel` | Hosting for cidadel-web |
| Firebase Hosting Site | `cidadel-auth` | Custom domains: auth.tacticl.ai, auth.strategiz.io |
| Vault Path | `secret/cidadel` | OAuth secrets, PASETO keys |
| GitHub Repos | `cuztomizer/cidadel-core`, `cuztomizer/cidadel-web` | Private repos |

## Repos Summary

| Repo | Type | Status |
|------|------|--------|
| `cuztomizer/cidadel-core` | Java/Spring Boot/Gradle backend | NEW — Create |
| `cuztomizer/cidadel-web` | React/Vite frontend | NEW — Create |
| `tacticl-core` | Java backend | MODIFY — Verify token compatibility |
| `tacticl-web` | React frontend | MINOR — Auth URLs already correct |
| `tacticl-mobile` | React Native | MINOR — Update AUTH_URL constant |
| `strategiz-core` | Java backend | MODIFY — Deprecate application-auth |
| `strategiz` | React frontend | MODIFY — Remove auth pages, add token hydration |

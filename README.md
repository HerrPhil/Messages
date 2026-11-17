# Messages — Modern Android Reference Architecture

> A production-grade, offline-first Android reference application built with **Kotlin**,
> **Jetpack Compose**, and **Clean Architecture**—engineered to demonstrate resilient networking,
> multi-module dependency injection, and defence-in-depth mobile security.

---

## 🎯 Project Purpose & Engineering Retrospective

This repository serves as a living architectural sandbox and retrospective on building, hardening,
and shipping modern native Android applications. Rather than demonstrating isolated APIs,
the project tackles real-world production challenges:
 * concurrent authentication failures,
 * flaky network degradation,
 * multi-module dependency graphs, and
 * strict local data protection.

This repository also served as an evaluation tool for free AI engines.
The two free AI tools that are utilized to accelerate writing code, reviewing code, and discussing
trade-offs are:
(1) [Google Gemini AI](https://gemini.google.com/), and
(2) [Google Search](https://www.google.com/search) with AI mode enabled.

---

## 🏗📆 Project Timeline

In May 2026, I created a bare project from one of the Android Studio app template, and an idea
to assemble enterprise-strength code that is resilient and stable.

Here we are, November 2026, and the core lessons I wanted to practice are complete.

The velocity I aimed for was 2 to 4 hours of coding per day. It was a part-time effort.

The end result is 31848 lines of code and a working app.

---

## 🏗 Architecture & Tech Stack

The codebase follows **Clean Architecture** and **Unidirectional Data Flow (UDF)** principles,
organized into clear, decoupled feature and core modules to enforce separation of concerns and
optimize incremental Gradle build times.

* **UI Layer:** 100% Kotlin with a **Jetpack Compose-first** approach, featuring thoughtful 
    interoperability strategies for evolving legacy XML layouts without full rewrites.
* **State & Concurrency:** Lifecycle-aware `ViewModel`s exposing immutable UI state 
    via **Kotlin Coroutines & `Flow`**, paired with type-safe Jetpack Navigation.
* **Domain & Data Layers:** Strict boundary separation using Use Cases, Repositories, and
    distinct (network) data/database/domain models.
* **Dependency Injection:** **Hilt** configured across a non-trivial **multi-module**
    Gradle setup (`:app`, `:core:*`, `:feature:*`).

---

## 🛡️ Networking, Resilience & Offline-First Sync

Mobile networks are inherently unreliable. The data layer is engineered so that transient failures
or expired sessions never blank critical UI or corrupt local state:

* **Hardened REST/JSON Pipeline:** Built on **Retrofit** and **OkHttp**
    with structured error mapping and exponential backoff retries.
* **Concurrent Session & Token Handling:** Custom OkHttp `Authenticator`
    and interceptors designed to handle `401 Unauthorized` responses,
    deduplicate parallel token refresh calls, and safely recover or
    sign out on unrecoverable session expiry.
* **Offline & Degraded Modes:** **Room** (`DAO` + reactive `Flow` queries) acts as
    the single source of truth, seamlessly caching remote responses and
    queuing mutations so the app remains responsive offline or under degraded connectivity.

---

## 🔐 Mobile Security & Privacy by Design

Informed by formal training in cybersecurity operations and incident response
(**Google Cybersecurity Certificate**),
this project treats the mobile client as a zero-trust environment:

* **Secrets & Token Management:** Zero hardcoded API keys; session tokens and sensitive payloads
    are isolated and protected using platform-backed encryption rather than plaintext preferences.
* **Data Minimization & PII Protection:** Strict scoping of runtime permissions,
    sanitized local database caching, and custom network/application logging policies
    that redact Personally Identifiable Information (PII) and auth headers in release builds.
* **Defensive Coding:** Input validation at module boundaries and
    hardened network security configurations (TLS enforcement).

---

## ✅ Production Quality & Engineering Workflow

* **Automated Testing Pyramid:** Comprehensive **unit tests**
    (ViewModels, Use Cases, Repositories, and OkHttp/MockWebServer network edge cases)
    alongside **Compose UI tests** verifying user journeys and degraded states.
* **Maintainable Delivery:** Developed using atomic, small, reviewable Pull Requests
    with strict static analysis and Gradle build hygiene.

---

## 🔄 Refactoring Epics

The two large refactoring epics in this project are dependency injection and offline-first cache.

### Dependency Injection

Since this project is a practice and learning project, I deliberately chose to be responsible
for dependency injection at the beginning of the project. That required rolling my own App Container
to handle instances of common classes to inject, and an App ViewModel Provider to orchestrate
passing DI instances from the app container to ViewModels.

Starting September 10, I started porting all the dependency injection code to Hilt.
Due to the strict adherence to separation of concerns by using multiple modules,
and maintaining discrete data (network), domain, and presentation (UI) layers,
I was able to incrementally morph each layer to Hilt configured code.

The final check-in was October 8.

My thought process was I wanted to build up enough of an enterprise app so that doing
dependency injection would require some planning and learning.

I found Hilt to be a real pleasure to fold into the codebase. The transition from the manual DI
to the annotation-driven Hilt DI was clearly explained. In some places, it was more concise.
For example, it cleaned up the injection of API services for repositories by making it easy to
annotate an bare, unauthenticated API service for login, and a rich, authenticated API service
for the business features post-login.

### Offline-first

Another architecture decision I made was to use the cache repository pattern, and defer the 
offline-first epic that introduces Room as the offline cache. The cache repository approach
allowed for some extra coroutine learning of its declarative nature and lifecycle.

I found the Room migration to be so easy. Each repository can be migrated vertically.
The code can be changed incrementally, in small PRs. That way the project is always building clean,
and the app remains runnable. As well, deferring the epic to the end allowed a non-trivial body of
work to build up so effort was required to plot out the offline-first code.

---

## ⚡️ Artificial Intelligence Tools

This project benefitted by the coding patterns and samples that have been used to teach the
LLM's of Google Gemini AI and Google Search (AI mode). There definitely are synergies when using
Android, Kotlin, and Jetpack Compose to create a mobile app.

* **Technology - Android:** Google's role is Creator & Primary Steward. Its key responsibilities
    & milestones are it develops the OS (AOSP), Android SDK, Android Studio and Google Play services.
*  **Technology - Kotlin:** Google's role is Co-Founder of Kotlin Foundation & Primary Ecosystem
    Partner. Its key responsibilities & milestones are it co-manages trademark/governance with
    JetBrains; designated Kotlin as Android's preferred langauge (2019) and
    endorsed Kotlin Multiplatform (2024).
* **Technology - Jetpack Compose:** Google's role is Creator & Primary Maintainer. Its key
    responsibilities & milestones are it designed, builds, and maintains the
    declarative UI framework; officially made Android UI development "Compose First" in May 2026.

### 👨🏻‍💻The Overly Optimistic Junior Developer - AI

I have learned over the period of time that I have been coding this project from scratch
that an AI tool can be a powerful programming partner. However, without recalibrating the
search context, and providing explicit outcomes that are expected from each request,
an AI engines will champion code solutions that are delusional or at best are a guide of what
to try.

It is imperative that AI request/response thread be taken with a grain of salt.
I had several sessions where I was lenient with a given AI thread. I was using it as a live
technical document, something that is more dynamic than a printed technical book from one of the
technical book publishing houses. Whenever an AI thread started thrashing, I found that I had to
reframe my question or request to reduce the scope of the request to give the AI LLM engine
more strict guardrails to follow.

---

## 📂 Module Structure

```text
+-- app/                      # Application entry point, DI root, top-level navigation
+-- core/
│   +-- data (network)/       # Retrofit, OkHttp interceptors, token refresh authenticator
│   +-- database/             # Room database, DAOs, local entities, migrations
│   +-- security/             # Crypto wrappers, secure token storage, PII-safe logging
│   +-- ui/                   # Shared Compose theme, reusable components, XML bridges
│   +-- common/               # Coroutine dispatchers, Result wrappers, base utilities
+-- feature/
    +-- auth/                 # Login, session lifecycle
    +-- home screen/          # dashboard for authenticated users with UI, ViewModels, Use Cases and Repositories
    +-- message screen/       # master/detail screen for messages with UI, ViewModels, Use Cases and Repositories
    +-- bulletin screen/      # master/detail screen for bulletins with UI, ViewModels, Use Cases and Repositories
    +-- authenticated shell/  # shared drawer menu of all screens.

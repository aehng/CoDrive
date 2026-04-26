<!-- CoDrive Comprehensive Project Master Document
     This file is the canonical project mission & engineering plan. Keep this file up to
     date as the single source of truth for architecture, safety, and phase-by-phase work. -->

# CoDrive — Comprehensive Project Master Document

Package: `com.codrive.ai` | Target: Samsung S25 FE (8GB RAM) | SDK: Min 33 (Android 13), Target 35

Last updated: 2026-04-23

## 1. Project Identity & Overview

Core Paradigm: An Android Accessibility Service acting as a voice-controlled "Agentic Proxy." It scrapes the UI, processes reasoning via the cloud, and executes physical screen interactions hands-free.

Architecture: Native Android (Kotlin). Cloud-Brain / Local-Memory / Local-Voice Hybrid.

Methodology: "Tracer Bullet" — priority on one complete vertical slice (STT -> Scrape -> Groq -> Click) before full subsystem integration.

## 2. The "Brain" (LLM & API)

Provider: Groq API

Model: qwen/qwen3-32b (Standard JSON output, no reasoning stream overhead).

API Key Management: Stored strictly in `local.properties` as `GROQ_API_KEY` and injected into the app via Gradle `buildConfigField`. Never commit secrets to Git.

User-provided API keys & model selection (Settings UX)

- The app must provide a secure Settings screen where users can add their own API keys and choose a provider and model. This enables users to use personal Groq keys or other providers (Gemini, OpenAI) without modifying the build.
- Storage: Store user-entered API keys using `EncryptedSharedPreferences` or another Keystore-backed mechanism. Do NOT store user API keys in plaintext or commit them.
- Dev vs Runtime keys:
  - `local.properties` and Gradle `buildConfigField` may be used for development/test keys only (debug builds). User-entered keys must be stored encrypted at runtime.
  - Optionally provide a debug-only import path for `GROQ_API_KEY` from `local.properties` (gated behind a debug flag) to ease developer testing.
- Provider/model selection:
  - Ship the tracer-bullet with a default runtime provider/model: Groq `qwen/qwen3-32b` (hardcoded default) to ensure consistent early behavior.
  - The Settings screen should allow choosing a provider (Groq, Gemini, OpenAI, Custom HTTP) and entering the model identifier string for that provider.
  - Implement a provider-adapter pattern in `LlmClient` so the runtime request wiring uses the selected provider/model and key without changing app logic.
  - On key entry, run a lightweight validation call and show success/failure in the UI (avoid expensive token-consuming requests during validation).
  - Prompt contract: model output must be raw JSON only. Do not emit conversational text, explanations, or markdown blocks outside the JSON object.

Security & UX guidance:

- Mask keys in the UI after entry (show only last 4 characters). Allow users to rotate/remove keys at any time.
- Persist selected provider/model in cleartext settings but keep the API key encrypted.
- Log provider/model selection (not the key) for debugging. Never log full keys or prompt content in production.


Confirmed Free Tier Limits (April 2026)

- RPM: 60 requests per minute
- TPM: 6,000 tokens per minute (tightest bottleneck)
- RPD: 1,000 requests per day

Operational Constraints

- TPM Guardrail: With 6K TPM, the UI Scraper MUST be highly aggressive in pruning to keep the payload small.
- Budgeting: At 60 RPM, rate-limit risk shifts to token weight. Keep UI maps small.

Strict JSON Schema (strict: true)

{
  "action_type": "CLICK | TYPE | SCROLL | HOME | BACK | RECENTS | OPEN_NOTIFICATIONS | OPEN_QUICK_SETTINGS | OPEN_POWER_DIALOG | LOCK_SCREEN | TAKE_SCREENSHOT | SWIPE_DOWN | SWIPE_UP | SWIPE_LEFT | SWIPE_RIGHT | SEARCH_MEMORY | RESPOND | FINISH",
  "target_index": 0,
  "text_to_type": "string",
  "tool_query": "string",
  "voice_feedback": "string",
  "confidence_score": 0.0
}

## 3. The "Eyes" (Semantic Pruner)

Engine: Android `AccessibilityService`.

Algorithm: Recursive DFS traversal of the active `AccessibilityNodeInfo` tree.

Retention Criteria: Keep nodes ONLY if they have `text`, `contentDescription`, or are interactive (`isClickable`, `isEditable`, `isCheckable`).

RAM Management: Mandatory `node.recycle()` on every processed child during traversal to prevent memory leaks on the S25 FE.

State Management: A `HashMap<Int, AccessibilityNodeInfo>` maps the AI-provided index back to a live UI node. This map is rebuilt on each snapshot and old indices are invalidated.

Failsafe: If zero interactable nodes are found, trigger the VLM fallback path (Tier 3) or speak: "This screen is unreadable." depending on mode.

Role mapping (explicit):
- `isEditable == true` -> `role: input`
- `isCheckable == true` -> `role: checkbox`
- `isClickable == true` -> `role: button`
- else -> `role: text`

Each kept node MUST include:
- `index: Int` (temporary crawl id)
- `bounds: [left, top, right, bottom]` (absolute screen coordinates)

Runtime requirement: Rebuild registry per snapshot and call `node.recycle()` on processed child nodes to avoid RAM bloat.

## 4. The "Memory" (Local Database & RAG)

Substrate: Android Room Persistence Library (SQLite).

Local Tables:

- `IdentityEntity`: Indefinite retention. Stores name, phone, resume, bio. User-managed via settings.
- `SessionContextEntity`: Ephemeral (1-hour TTL). Stores short-term turn history and is purged on background or after 60 minutes.

SEARCH_MEMORY Loop: Groq may return `action_type: SEARCH_MEMORY` and a `tool_query`. App queries Room, appends results to model context, and re-queries Groq until terminal action.

## 5. The "Voice" (Local Audio Stack)

STT: Sherpa-ONNX (Local/Offline) — streaming.

TTS: Piper (Local/Offline) — low-latency neural.

Sync: TTS narrates `voice_feedback` asynchronously while the physical action executes.

## 6. The "Hands" (Execution)

Mapping: Retrieve live `AccessibilityNodeInfo` via `target_index` from the registry.

Tap: Calculate midpoint (x, y) of node `bounds` and dispatch `GestureDescription` at that point.

Type: Use `ACTION_SET_TEXT` for editable nodes where appropriate.

Anti-Stale: Always call `node.refresh()` before acting. If `refresh()` returns false, abort and re-scrape.

Risky-Action Taxonomy (Explicit vs Implicit Confirmation)

Tier 1 — Navigation (Low Risk): `SCROLL`, `CLICK` on navigation elements (tabs, "Next").
  - Implicit confirmation: execute and narrate.

Tier 2 — Data Mutation (Medium Risk): `TYPE`, `CLICK` on checkboxes/toggles.
  - Implicit confirmation with reversibility. If `confidence_score < 0.8`, ask for clarification and stop.

Tier 3 — External-send / Destructive (High Risk): `CLICK` on submits, purchases, deletes.
  - Explicit confirmation required. TTS asks for "Confirm?" and waits for user "Yes" before executing.

## 7. Interaction Waterfall

Tier 1 (Local): Local regex router for simple commands (Go Home, Back, Scroll).
Tier 2 (Cloud): Full scrape + Groq decision.
Tier 3 (VLM Fallback): If scraper finds zero interactable nodes, use local VLM to ground coordinates.

Active session continuity (implemented):
- Keep a short-lived in-memory conversation buffer between mic taps.
- Session timeout: 30 seconds of inactivity, then clear context.
- Persist history for clarification or conversational `RESPOND` turns.
- Clear history after successful physical execution or terminal completion.

## 8. Tier 3: VLM Fallback Path

Model: Qwen2-VL-0.5B-Instruct (INT4 quantized locally).

Trigger: Accessibility tree has zero interactable nodes.

Coordinate Grounding: VLM returns a point; system searches for nodes within a snap radius (dp -> px conversion). If found, snap to node center; otherwise tap raw coordinates.

## 9. Safety & Edge Cases

Dead-Zone Sentry (ConnectivityManager): Immediately stop TTS and say: "Connection lost. Pausing CoDrive." Unbind scraper and pause execution. On reconnect, say: "Connection restored." and await next user command.

No action queueing: Do not queue actions while offline.

## 10. Future: Navigation Knowledge Base (PKB)

Store repeatable success paths in Room so frequently-used multi-step flows can be executed locally without repeated Groq calls.

## Tracer Bullet & Phase Discipline

Follow phase-by-phase execution. The tracer bullet vertical slice is the immediate priority:

Phase 0 — Baseline: Manifest, permissions, accessibility config.
Phase 1 — Core models/interfaces: `PrunedNodeEntry`, `PrunedUiMap`, `AgentDecision`, `ExecutionResult`, `SttEngine`, `TtsEngine`, `LlmClient`, `ActionExecutor`. Also add a Settings UI (secure API key entry, provider & model selection) and encrypted runtime storage for user keys.
Phase 2 — Semantic pruner, registry, semantic merging, and unit tests.
Phase 3 — Groq client, strict JSON-only handling, and runtime wiring so the selected provider/model and encrypted key from Settings are used at request time.
Phase 4 — Action execution with anti-stale checks, focus-before-type behavior, and gesture/global-action dispatch.
Phase 5 — Orchestrator (chat overlay -> prune -> infer -> execute -> feedback) plus launcher-ready seams for a persistent overlay bubble.

Immediate next implementation TODOs:
- [x] Document and prepare the overlay bubble entrypoint so the launcher can live outside `ChatActivity`. (See `docs/overlay-bubble-entrypoint.md`)


Definition of Done (Tracer Bullet MVP):
- A hardcoded trigger flows into the Pruner -> Groq -> strict JSON parse -> `node.refresh()` -> simulated `dispatchGesture` (on-device) with clear test steps and JUnit coverage.

## Developer Notes & Conventions

- Secrets: Keep `GROQ_API_KEY` in `local.properties` only.
- Tests: Every new Kotlin class should have a JUnit test in `app/src/test`.
- Recycle discipline: Call `node.recycle()` for every processed child.
- UX: Do not auto-open accessibility settings on boot. Subsequent boots should show "Service OFF" and a button to open settings manually. The app should only navigate to the accessibility settings after an explicit user tap in the app.

## Open Decisions (Record)

1. Service access pattern: Option A static instance on `CoDriveAccessibilityService`, Option B bound service bridge, Option C callback registry. (See design doc below.)

2. Feedback channel: Option A append model `voice_feedback` + executor result, Option B executor-only status text, Option C dual-line "decision + action outcome." (See tradeoffs below.)

### Service Access Patterns — short explainer

- Option A — Static instance on `CoDriveAccessibilityService`:
  - Pros: Simple global access, minimal IPC. Easy for quick tracer-bullet wiring.
  - Cons: Global mutable state, harder to test, potential lifecycle coupling.

- Option B — Bound Service Bridge:
  - Pros: Strong lifecycle contract, easier to mock and unit-test, follows Android service binding patterns.
  - Cons: More boilerplate; slightly more complex for fast prototyping.

- Option C — Callback Registry (observer pattern):
  - Pros: Decoupled, scalable for multiple clients (UI, overlay, tests).
  - Cons: More plumbing; coordination complexity.

Recommendation for tracer-bullet: Start with Option A to move fast, then migrate to Option B/C when expanding.

### Feedback Channel Options — short explainer

- Option A — Append model `voice_feedback` + executor result:
  - Pros: Rich narrative; good for voice UX.
  - Cons: Longer messages may be noisy in the chat transcript.

- Option B — Executor-only status text:
  - Pros: Concise; easy to present in UI.
  - Cons: Loses model rationale.

- Option C — Dual-line "decision + action outcome":
  - Pros: Best of both worlds — shows model decision (first line) and concise executor result (second line).
  - Cons: Slightly more UI complexity.

Recommendation: Implement Option C in the UI (dual-line) and use Option A for voice output when TTS is available.

---

If anything above needs to be split into smaller actionable tasks, tell me which area to start implementing next and I'll create concrete TODOs and scaffold the first tracer-bullet files.

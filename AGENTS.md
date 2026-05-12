# CoDrive — Comprehensive Project Master Document

Package: `com.codrive.ai` | Target: Samsung S25 FE (8GB RAM) | SDK: Min 33 (Android 13), Target 35

Last updated: 2026-05-03

## 1. Project Identity & Overview
- **Core idea:** Android Accessibility Service as a voice-controlled "Agentic Proxy" (STT -> Scrape -> LLM -> Execute).
- **Architecture:** Native Android (Kotlin). Cloud-Brain / Local-Memory / Local-Voice hybrid.
- **Methodology:** Tracer-Bullet vertical-slice first (complete flow end-to-end), then expand.

Status checklist (high-level)
-----------------------------
- [x] Phase 0 — Baseline: manifest, permissions, accessibility config
- [x] Phase 1 — Core models & interfaces (PrunedNodeEntry, PrunedUiMap, AgentDecision, ExecutionResult, SttEngine, TtsEngine, LlmClient, ActionExecutor, Settings UI, Encrypted key storage)
- [x] Phase 2 — Semantic pruner, registry, semantic merging, unit tests
- [x] Phase 3 — API client + strict JSON handling + runtime wiring for selected provider/model/key
- [x] Phase 4 — Action execution: anti-stale checks, focus-before-type, gestures
- [x] Phase 5 — Orchestrator + overlay bubble + launcher seams
- [x] Immediate: overlay bubble entrypoint documented (See `docs/overlay-bubble-entrypoint.md`)

## 2. The "Brain" (LLM & API)
- **Primary Provider (Heavy Lifting):** Google Gemini
- **Primary Model:** `gemini-1.5-flash` (Chosen for 250,000 TPM limit to handle complex UI maps and agentic batching).
- **Secondary Provider (Fast-Track):** Groq (`qwen/qwen3-32b`). Reserved for Tier 1 simple tasks where sub-500ms latency is required and payloads are tiny.

API key policy and Settings UX (tasks)
- [x] Provide a secure Settings screen for user-provided API keys and provider/model selection.
- [x] Persist provider/model selection in cleartext settings.
- [x] Store API keys encrypted at runtime (EncryptedSharedPreferences or Keystore-backed storage).
- [ ] Add a debug-only import path from `local.properties` gated behind a debug flag for development.
- [x] Implement lightweight validation call on key entry (low cost) and show success/failure in UI.
- [x] Implement provider-adapter pattern in `LlmClient` so runtime wiring uses selected provider/model/key.
- [x] Ensure prompt contract: model returns raw JSON object only (no explanatory text or markdown).

Free-tier limits (Gemma 4 31B - May 2026) — operational constraints
- **RPM:** 15 requests/min (Primary bottleneck — avoid rapid-fire scraping).
- **TPM:** 250,000 tokens/min (Massive headroom for UI maps).
- **RPD:** 1,500 requests/day.


Strict action JSON schema (enforced)

    {
      "action_type": "CLICK | TYPE | SCROLL | HOME | BACK | RECENTS | OPEN_NOTIFICATIONS | OPEN_QUICK_SETTINGS | OPEN_POWER_DIALOG | LOCK_SCREEN | TAKE_SCREENSHOT | SWIPE_DOWN | SWIPE_UP | SWIPE_LEFT | SWIPE_RIGHT | SEARCH_MEMORY | RESPOND | FINISH",
      "target_index": 0,
      "text_to_type": "string",
      "tool_query": "string",
      "voice_feedback": "string",
      "confidence_score": 0.0
    }

## 3. The "Eyes" (Semantic Pruner)
Design and invariants
- Engine: Android `AccessibilityService` using recursive DFS over `AccessibilityNodeInfo` tree.
- Retain nodes only if they have `text`, `contentDescription`, or are interactive (`isClickable`, `isEditable`, `isCheckable`).

UI Map Optimization (The Tuple Format)
- **Format:** Compact 4-item tuple: `[index, role, [centerX, centerY], "text"]`
- **Role minification:** `"b"` (button), `"i"` (input), `"c"` (checkbox), `"t"` (text)
- **Rationale:** Reduces payload size by ~60% to maximize API speed and limit token burn.

Registry & memory
- Build a fresh `HashMap<Int, AccessibilityNodeInfo>` per snapshot mapping temporary index -> live node.
- **MUST** call `node.recycle()` on every processed child to avoid memory leaks on target device.

Pruner tasks
- [x] Implement DFS pruner that recycles children as it goes.
- [x] Update `UiTreePruner` to perform Semantic Merging (merge child text into clickable parent containers).
- [x] Produce `PrunedUiMap` in the compact tuple format.

## 4. The "Memory" (Local Database & RAG)
- Substrate: Android Room (SQLite).
- Tables:
  - `IdentityEntity` (indefinite retention): name, phone, resume, bio (user-managed).
  - `SessionContextEntity` (ephemeral, 1-hour TTL): short-term turn history.

Memory tasks
- [x] Implement Room schema and DAO for IdentityEntity and SessionContextEntity.
- [x] Add TTL purge for `SessionContextEntity` (purge after background or 60 minutes).

## 5. The "Voice" (Unified 100% Offline Audio Stack)
To achieve zero-cost scaling and preserve privacy, the entire audio pipeline runs locally via Sherpa-ONNX v1.13.0.

- **Phase 1 (Tracer Bullet):** - **STT:** Android `SpeechRecognizer` (Native).
  - **TTS:** Android `TextToSpeech` (Native).
- **Phase 5 (Final Target - 2026 Standard):** - **STT:** SenseVoice Small (INT8). Exported to QNN/Hexagon for NPU acceleration on Samsung S25 FE. Chosen for superior car-cabin noise resilience.
  - **TTS:** Kokoro-82M (v1.0). Provides studio-grade neural voice synthesis entirely offline.



## 6. The "Hands" (Execution)
Execution rules
- Map `target_index` -> live `AccessibilityNodeInfo` via registry.
- Before acting: call `node.refresh()`; if `false` then abort and re-scrape.
- Tap: compute midpoint of `bounds` and dispatch `GestureDescription` at that point.
- Type: use `ACTION_FOCUS` -> `ACTION_SET_TEXT` for editable nodes.

Risk taxonomy and gating
- Tier 1 — Navigation (low risk): SCROLL, CLICK on navigation elements — implicit confirmation.
- Tier 2 — Data mutation (medium risk): TYPE, CLICK toggles — if `confidence_score < 0.8` ask clarification.
- Tier 3 — External/Destructive (high risk): submits, purchases, deletes — require explicit confirmation (TTS ask "Confirm?").

Execution tasks
- [x] Implement `ActionExecutor` with midpoint tap and ACTION_SET_TEXT support.
- [x] Implement anti-stale: `node.refresh()` checks and re-scrape fallback.
- [ ] Implement Tiered confirmation gating.

## 7. Interaction Waterfall
- Tier 1 (Local): local regex router for trivial commands (Home, Back, Scroll).
- Tier 2 (Cloud): full scrape -> pruned ui map -> LLM inference -> parse strict JSON -> execute.
- Tier 3 (VLM fallback): when pruner finds zero interactable nodes, use local VLM to ground coordinates.

Session continuity & Orchestration
- [x] Keep an `ActiveSessionHistory` conversation buffer in RAM between mic taps.
- [x] Session timeout: 30s of inactivity clears the buffer.
- [x] Persist short history for clarification/RESPOND turns.
- [x] Clear history after terminal action or successful physical execution.
- [ ] Add `Retry-After` logic to `InferenceLoopRunner` to gracefully handle HTTP 429 Rate Limits.

## 8. Tier 3: VLM Fallback
When accessibility scraping yields no interactable nodes, a local Vision Language Model will ground coordinates.
- **Model Recommendation:** `InternVL2-1B` (Excellent coordinate grounding, ~800MB RAM footprint).
- **Hardware Acceleration:** Run via `llama.cpp` using the **QNN/Hexagon backend** to leverage the device NPU for efficiency.
- **Grammar Constraints:** Enforce strict GBNF or JSON Schema output from the inference engine to guarantee valid coordinates (e.g., `{"point": [123, 456], "label": "submit"}`).
- Add a snap-radius routine: search for pruned nodes within radius (dp->px conversion) and snap to node center; otherwise perform raw tap.

VLM tasks
- [ ] Integrate local `llama.cpp` runtime with QNN backend and GBNF verifier.
- [ ] Implement snap-radius search and raw-tap fallback.
- [ ] InternVL integration plan (phased):
  - Phase A — API picker + model readiness
    - Add InternVL option in API/model picker UI (local VLM) with clear offline-only badge.
    - Require `ModelReadiness.hasVlmModels()` before enabling the toggle.
    - Add a "Warm up InternVL" action that calls `InternVlRuntime.ensureLoaded()` and reports success.
  - Phase B — Tier 3 fallback (unreadable screens)
    - When `PrunedUiMap.isUnreadable`, call InternVL to emit a point JSON via GBNF.
    - Snap-to-node within radius; else raw tap at VLM point.
    - Log the VLM label + chosen point in the overlay log.
  - Phase C — First-line VLM mode for light tasks
    - Add a "VLM first" mode in settings for short commands (length + keyword heuristics).
    - Default to: keyword trigger ("tap", "press", "open") OR explicit prefix ("vision:").
    - Safe guard: if VLM confidence low or no point, fall back to standard LLM flow.
  - Phase D — Offline command mode
    - Local-only rule: allow VLM + local regex navigation without cloud calls.
    - Explicit UI indicator: "Offline Mode" and a one-tap exit to restore cloud.
  - Phase E — Telemetry + guardrails
    - Record success/failure counters per mode and auto-disable VLM-first after repeated failures.
    - Gate destructive actions behind confirmation even in VLM-first mode.

## 9. Safety & Edge Cases
- Dead-Zone Sentry: on connectivity loss, stop TTS, say "Connection lost. Pausing CoDrive.", unbind scraper and pause. On reconnect: "Connection restored." and await user.
- Do not queue actions while offline.

Safety tasks
- [ ] Implement ConnectivityManager hook to pause/unpause system and TTS.

## 10. Future: Navigation Knowledge Base (PKB)
- Store repeatable success paths (multi-step flows) in Room so frequent flows can run locally without cloud calls.

## 11. Open Decisions (Recorded)
1) Feedback channel:
  - **Verdict:** Use Option A (voice feedback) for TTS narration, and Option C (dual-line decision + action outcome) for the visual UI log.
2) Agentic Batching:
  - **Concept:** Allow the cloud model to return a LIST of actions (e.g., `[TYPE, CLICK]`) to perform multi-step sequences like "Type and Send" in a single turn.
  - **Strategy:** With Gemini 1.5 Flash's massive token limits, this is highly viable. Gate behind an experiment flag with strict validation to handle stale UI shifts between batched actions.

## Tracer Bullet & Phase Discipline
Phases (tracer-bullet priority):
- Phase 0 — Baseline (manifest, permissions, accessibility config)
- Phase 1 — Core models & interfaces + Settings UI + Encrypted key storage
- Phase 2 — Pruner & registry + unit tests
- Phase 3 — API client + strict JSON handling + runtime wiring
- Phase 4 — Action execution, anti-stale, gestures
- Phase 5 — Orchestrator (chat overlay -> prune -> infer -> execute -> feedback)

Developer Notes & Conventions
- Secrets: keep API keys in `local.properties` only for dev/debug. User keys must be encrypted at runtime.
- Tests: every new Kotlin class should have a JUnit test under `app/src/test`.
- Recycle discipline: call `node.recycle()` for every processed child.
- UX: do not auto-open accessibility settings on boot; show "Service OFF" and require explicit user tap to open accessibility settings.
<!-- CoDrive Comprehensive Project Master Document
     This file is the canonical project mission & engineering plan. Keep this file up to
     date as the single source of truth for architecture, safety, and phase-by-phase work. -->

# CoDrive — Comprehensive Project Master Document

Package: `com.codrive.ai` | Target: Samsung S25 FE (8GB RAM) | SDK: Min 33 (Android 13), Target 35

Last updated: 2026-05-03

Overview
--------
- Core idea: Android Accessibility Service as a voice-controlled "Agentic Proxy" (STT -> Scrape -> LLM -> Execute).
- Architecture: Native Android (Kotlin). Cloud-Brain / Local-Memory / Local-Voice hybrid.
- Methodology: Tracer-Bullet vertical-slice first (complete flow end-to-end), then expand.

Status checklist (high-level)
-----------------------------
- [x] Phase 0 — Baseline: manifest, permissions, accessibility config
- [x] Phase 1 — Core models & interfaces (PrunedNodeEntry, PrunedUiMap, AgentDecision, ExecutionResult, SttEngine, TtsEngine, LlmClient, ActionExecutor, Settings UI, Encrypted key storage)
- [x] Phase 2 — Semantic pruner, registry, semantic merging, unit tests
- [x] Phase 3 — Groq client + strict JSON handling + runtime wiring for selected provider/model/key
- [x] Phase 4 — Action execution: anti-stale checks, focus-before-type, gestures
- [x] Phase 5 — Orchestrator + overlay bubble + launcher seams
- [x] Immediate: overlay bubble entrypoint documented (See `docs/overlay-bubble-entrypoint.md`)

2. Brain: LLM & API
--------------------
- Provider (default): Groq
- Default model: `qwen/qwen3-32b` (expect strict JSON-only output)

API key policy and Settings UX (tasks)

- [x] Provide a secure Settings screen for user-provided API keys and provider/model selection
- [x] Persist provider/model selection in cleartext settings
- [x] Store API keys encrypted at runtime (EncryptedSharedPreferences or Keystore-backed storage)
- [ ] Add a debug-only import path from `local.properties` gated behind a debug flag for development
- [x] Implement lightweight validation call on key entry (low cost) and show success/failure in UI
- [x] Implement provider-adapter pattern in `LlmClient` so runtime wiring uses selected provider/model/key
- [x] Ensure prompt contract: model returns raw JSON object only (no explanatory text or markdown)

Developer notes (security & logging)

- Mask keys in the UI after entry (show only last 4 chars)
- Allow users to rotate/remove keys at any time
- Never log full keys or prompts in production; logging may include provider/model selection only

Free-tier limits (Apr 2026) — operational constraints

- RPM: 60 requests/min
- TPM: 6,000 tokens/min (primary guardrail)
- RPD: 1,000 requests/day

- Token/Rate guidance: keep UI payloads small (aggressive pruning). Implement Retry-After handling for 429s.

Strict action JSON schema (enforced)

{
  "action_type": "CLICK | TYPE | SCROLL | HOME | BACK | RECENTS | OPEN_NOTIFICATIONS | OPEN_QUICK_SETTINGS | OPEN_POWER_DIALOG | LOCK_SCREEN | TAKE_SCREENSHOT | SWIPE_DOWN | SWIPE_UP | SWIPE_LEFT | SWIPE_RIGHT | SEARCH_MEMORY | RESPOND | FINISH",
  "target_index": 0,
  "text_to_type": "string",
  "tool_query": "string",
  "voice_feedback": "string",
  "confidence_score": 0.0
}

3. Eyes: Semantic Pruner (Accessibility scraper)
----------------------------------------------

Design and invariants
- Engine: Android `AccessibilityService` using recursive DFS over `AccessibilityNodeInfo` tree
- Retain nodes only if they have `text`, `contentDescription`, or are interactive (`isClickable`, `isEditable`, `isCheckable`)
- Role mapping (explicit):
  - `isEditable == true` -> `role: input`
  - `isCheckable == true` -> `role: checkbox`
  - `isClickable == true` -> `role: button`
  - else -> `role: text`

Registry & memory
- Build a fresh `HashMap<Int, AccessibilityNodeInfo>` per snapshot mapping temporary index -> live node
- MUST call `node.recycle()` on every processed child to avoid memory leaks on target device
- Each pruned node record must include:
  - `index: Int` (temporary crawl id)
  - `bounds: [left, top, right, bottom]` (absolute screen coordinates)

Failsafe
- If zero interactable nodes found: trigger VLM fallback path (Tier 3) or TTS: "This screen is unreadable." depending on mode

Pruner tasks

- [x] Implement DFS pruner that recycles children as it goes
- [x] Produce `PrunedUiMap` in the compact tuple format for token savings (see UI Map section)

4. Memory: Local Database & RAG (Room)
-------------------------------------

- Substrate: Android Room (SQLite)
- Tables:
  - `IdentityEntity` (indefinite retention): name, phone, resume, bio (user-managed)
  - `SessionContextEntity` (ephemeral, 1-hour TTL): short-term turn history

SEARCH_MEMORY loop
- If model returns `action_type: SEARCH_MEMORY` with `tool_query`, query Room, append results to model context, re-query until a terminal action is returned

Memory tasks

- [x] Implement Room schema and DAO for IdentityEntity and SessionContextEntity
- [x] Add TTL purge for `SessionContextEntity` (purge after background or 60 minutes)

5. Voice: Local Audio Stack
---------------------------

- STT: Sherpa-ONNX (local, streaming)
- TTS: Piper (local, low-latency)
- Sync: TTS narrates `voice_feedback` asynchronously while actions execute

Voice tasks

- [ ] Integrate Sherpa-ONNX for STT
- [ ] Integrate Piper for TTS

6. Hands: Execution (Action Executor)
------------------------------------

Execution rules
- Map `target_index` -> live `AccessibilityNodeInfo` via registry
- Before acting: call `node.refresh()`; if `false` then abort and re-scrape
- Tap: compute midpoint of `bounds` and dispatch `GestureDescription` at that point
- Type: use `ACTION_SET_TEXT` for editable nodes

Risk taxonomy and gating
- Tier 1 — Navigation (low risk): SCROLL, CLICK on navigation elements — implicit confirmation
- Tier 2 — Data mutation (medium risk): TYPE, CLICK toggles — if `confidence_score < 0.8` ask clarification
- Tier 3 — External/Destructive (high risk): submits, purchases, deletes — require explicit confirmation (TTS ask "Confirm?")

Execution tasks

- [x] Implement `ActionExecutor` with midpoint tap and ACTION_SET_TEXT support
- [x] Implement anti-stale: `node.refresh()` checks and re-scrape fallback
- [ ] Implement Tiered confirmation gating

7. Interaction Waterfall
-----------------------

- Tier 1 (Local): local regex router for trivial commands (Home, Back, Scroll)
- Tier 2 (Cloud): full scrape -> pruned ui map -> LLM inference -> parse strict JSON -> execute
- Tier 3 (VLM fallback): when pruner finds zero interactable nodes, use local VLM to ground coordinates

Session continuity (implemented)

- [x] Keep a short-lived in-memory conversation buffer between mic taps
- [x] Session timeout: 30s of inactivity clears the buffer
- [x] Persist short history for clarification/RESPOND turns
- [x] Clear history after terminal action or successful physical execution

8. Tier 3: VLM Fallback (merged & revised)
-----------------------------------------

When accessibility scraping yields no interactable nodes, a small local VLM will ground coordinates.

- Current/previous models: Qwen2-VL-0.5B-Instruct (INT4) — upgraded recommendation: `InternVL2-1B` for better grounding accuracy
- Run the local VLM via `llama.cpp` with QNN/Hexagon backend where available to leverage device NPU
- Enforce strict JSON/GBNF output from VLM: e.g. `{ "x": 123, "y": 456 }` in absolute px reference frame
- Add a snap-radius routine: search for pruned nodes within radius (dp->px conversion) and snap to node center; otherwise perform raw tap

VLM tasks

- [ ] Integrate local VLM runtime and strict-output verifier
- [ ] Implement snap-radius search and raw-tap fallback

9. Safety & Edge Cases
----------------------

- Dead-Zone Sentry: on connectivity loss, stop TTS, say "Connection lost. Pausing CoDrive.", unbind scraper and pause. On reconnect: "Connection restored." and await user.
- Do not queue actions while offline

Safety tasks

- [ ] Implement ConnectivityManager hook to pause/unpause system and TTS

10. Future: Navigation Knowledge Base (PKB)
----------------------------------------

- Store repeatable success paths (multi-step flows) in Room so frequent flows can run locally without cloud calls

PKB tasks

- [ ] Design PKB schema and execution runner

Tracer Bullet & Phase Discipline
--------------------------------

Phases (tracer-bullet priority):

- Phase 0 — Baseline (manifest, permissions, accessibility config)
- Phase 1 — Core models & interfaces + Settings UI + Encrypted key storage
- Phase 2 — Pruner & registry + unit tests
- Phase 3 — Groq client + strict JSON handling + runtime wiring
- Phase 4 — Action execution, anti-stale, gestures
- Phase 5 — Orchestrator (chat overlay -> prune -> infer -> execute -> feedback)

Immediate next steps

- [x] Document and prepare the overlay bubble entrypoint so the launcher can live outside `ChatActivity` (`docs/overlay-bubble-entrypoint.md`)
- [x] Implement the minimal tracer-bullet vertical slice: hardcoded trigger -> pruner -> Groq -> strict JSON -> node.refresh() -> simulated dispatchGesture

Developer Notes & Conventions
-----------------------------

- Secrets: keep `GROQ_API_KEY` in `local.properties` only for dev/debug. User keys must be encrypted at runtime.
- Tests: every new Kotlin class should have a JUnit test under `app/src/test`
- Recycle discipline: call `node.recycle()` for every processed child
- UX: do not auto-open accessibility settings on boot; show "Service OFF" and require explicit user tap to open accessibility settings

Open Decisions (recorded)
-------------------------

1) Feedback channel — options:
   [X] Option A: model `voice_feedback` + executor result (voice narrative)
   - Option B: executor-only status text (concise)
   [X] Option C: dual-line UI (decision + action outcome) — recommended for UI; Option A for TTS
Verdict: voice feedback only for tts but on the ui we should display what the ai did and the speech it is reading

Additional Plan Updates
-----------------------

UI Map Optimization (the tuple format)

- Format: compact 4-item tuple: [index, role, [centerX, centerY], "text"]
- Role minification: `"b"` (button), `"i"` (input), `"c"` (checkbox), `"t"` (text)
- Rationale: token savings (~60%) to respect TPM guardrails

 - [x] Implement `PrunedUiMap` serializer producing tuple form and make prompts document tuple semantics

Logic & Orchestration updates

- [ ] Add `Retry-After` logic to `InferenceLoopRunner` to respect 429 responses and avoid tight retries
- [x] Update `UiTreePruner` to merge child text into clickable parent containers where appropriate (e.g., list rows)
- [x] Maintain `ActiveSessionHistory` in RAM for 30s (clarification support)

Agentic Batching (open)

- Consider allowing the cloud model to return a LIST of actions for multi-step execution. Tradeoffs:
  - Pros: fewer round trips
  - Cons: increased risk and rollback complexity
- Recommendation: gate behind experiment flag with stricter thresholds and confirmation rules

-- end

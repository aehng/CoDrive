# CoDrive Agent Mission and Build Plan

This file is the standing mission for all future implementation work in this repository.

## North Star
Build **CoDrive** (`com.codrive.ai`) as an Android 13+ accessibility proxy that:
1. Accepts user prompts through an always-available overlay chat UI (first implementation target).
2. Evolves to a live voice overlay bubble session (main product), with always-listening while active, barge-in interruption, and swipe-to-dismiss end control.
3. Scrapes and semantically prunes the current UI tree.
4. Sends command + UI map to Groq (`deepseek-r1-distill-qwen-32b`) with strict JSON schema output.
5. Verifies target node freshness.
6. Executes safe click/type/scroll behavior.
7. Speaks feedback with local TTS.

## Fixed Technical Constraints
- Min SDK: 33
- Package: `com.codrive.ai`
- Architecture: Cloud-Brain / Local-Memory Hybrid
- STT: Sherpa-ONNX (local, streaming)
- TTS: Piper (local, low-latency)
- Automation: `AccessibilityService` + `GestureDescription`
- Memory substrate: Room Persistence Library (SQLite) for local identity memory
- Safety guardrails:
  - Ignore non-visible nodes (`isVisibleToUser == false`).
  - If `confidence_score < 0.8`, ask for clarification and take no action.
- Traversal memory hygiene:
  - Call `node.recycle()` on processed child nodes during traversal to prevent RAM bloat.

## Interaction Modes
1. Overlay Chat Mode (priority now)
   - User sends text prompts in a system overlay that can be used on top of other apps.
   - Agent executes accessibility actions against the current foreground app.
2. Live Voice Bubble Mode (later)
   - Bubble appears over other apps.
   - While active: always listening and speaking back.
   - Must support barge-in (user interruption stops current TTS playback).
   - End session by dragging bubble to dismiss target.

## Semantic Pruner Contract
Recursively traverse `AccessibilityNodeInfo` using DFS and produce a flat JSON array.

Keep nodes when any is true:
- node has `text` or `contentDescription`
- node is interactive (`isClickable`, `isEditable`, or `isCheckable`)

Role mapping (explicit):
- `isEditable == true` -> `role: input`
- `isCheckable == true` -> `role: checkbox`
- `isClickable == true` -> `role: button`
- else -> `role: text`

Each kept node must include:
- `index: Int` (temporary crawl id)
- `bounds: [left, top, right, bottom]` (absolute screen coordinates)

Runtime requirements:
- Maintain `HashMap<Int, AccessibilityNodeInfo>` to map `target_index` back to the live node.
- Rebuild the map per snapshot and invalidate old indices after each new crawl.

WebView failsafe:
- If the tree is empty or contains no interactable nodes, fail closed and trigger TTS: "This screen is unreadable."

## Groq Response Contract (strict json_schema)
Model: `deepseek-r1-distill-qwen-32b`

Required fields:
- `action_type`: `CLICK | TYPE | SCROLL | SEARCH_MEMORY | FINISH`
- `target_index`: Integer
- `text_to_type`: String
- `tool_query`: String
- `voice_feedback`: String
- `confidence_score`: Number (0.0..1.0)

DeepSeek reasoning handling:
- Groq responses may include `reasoning_content` (`<think>` traces).
- Log reasoning for debug visibility when enabled, but extract and parse only the strict JSON content block for action execution.
- If schema validation fails, take no action and ask for clarification.

## Groq Runtime Policy (Free-Tier Aware)
Given limits (30 RPM, 18,000 TPM, 14,400 RPD, 500,000 TPD), default policy is:
- Request timeout: 12 seconds
- Retries: 1 retry on timeout/5xx with exponential backoff (600 ms base, jitter)
- Rate-limit behavior: on HTTP 429, back off and speak/log "I am rate-limited, retrying shortly."
- Token budget target per request:
  - Prompt + UI map + output <= 1,500 tokens soft target
  - Hard cap <= 2,000 tokens per request
- Reasoning control:
  - System prompt includes "limit reasoning to 2 sentences max"
  - Keep UI map concise through strict semantic pruning
- Fail-closed behavior:
  - On repeated failure, execute no gesture and return a safe clarification response.

## Tracer Bullet Workflow
1. User submits command (text first; voice pipeline wired in later phase).
2. Pruner builds `PrunedUiMap` + node registry map.
3. Groq inference returns strict schema response.
4. Multi-turn RAG loop:
   - If `action_type == SEARCH_MEMORY`, query Room using `tool_query`.
   - Append DB result to model context and re-query Groq.
   - Continue until terminal action (`CLICK | TYPE | SCROLL | FINISH`) or safe abort.
5. On target node: call `refresh()`, then re-check validity and visibility.
6. Execute action (gesture midpoint click, `ACTION_SET_TEXT`, or scroll).
7. Speak `voice_feedback` via Piper.

## Safety Modes (User Selectable)
Expose policy options in settings:
1. Allow all actions
2. App allowlist only
3. Confirmation for risky actions

Risky-action taxonomy (confirmation mode):
- Tier 1 - Navigation (low risk): `SCROLL`, and `CLICK` on navigational UI (tabs, next, expand).
  - UX: implicit confirmation. Execute immediately and narrate outcome.
- Tier 2 - Data mutation (medium risk): `TYPE`, and `CLICK` on toggles (`checkbox`, `radio`).
  - UX: implicit confirmation with reversibility. Execute and narrate; only halt for clarification when `confidence_score < 0.8`.
- Tier 3 - External-send / destructive (high risk): `CLICK` on actions such as submit, buy, delete.
  - UX: mandatory explicit confirmation. Intercept and halt before tap. Ask: "Ready to submit this application. Confirm?" Execute only after explicit user "Yes".

Note: Mode plumbing should be designed early, but full policy UX can land after tracer bullet.

## Memory Retention Policy
Room is split into two purpose-specific tables:
- `IdentityEntity` (indefinite retention)
  - Stores durable profile data (name, phone, resume details, identifiers).
  - User-managed through settings.
- `SessionContextEntity` (session-only / rolling 1-hour)
  - Stores short-lived task context (for example, recent page choice or prior step state).
  - Purge on app background and when records exceed 60 minutes.

Goal: prevent stale cross-session contamination while preserving stable user identity memory.

## Implementation Phases
### Phase 0 - Baseline setup
- Confirm package `com.codrive.ai` and SDK 33.
- Add/update manifest permissions: `BIND_ACCESSIBILITY_SERVICE`, `POST_NOTIFICATIONS`, `RECORD_AUDIO`, and overlay-related permissions/components as needed.
- Add `accessibility_service_config.xml`.

### Phase 1 - Core models and interfaces
- Add models: `PrunedNodeEntry`, `PrunedUiMap`, `AgentDecision`, `ExecutionResult`.
- Add interfaces: `SttEngine`, `TtsEngine`, `LlmClient`, `ActionExecutor`.
- Add Room contracts for memory split: `IdentityEntity`, `SessionContextEntity`, DAOs, and `IdentityDatabase`.

### Phase 2 - Semantic pruner and registry
- Implement DFS pruner + explicit role inference.
- Enforce visibility filter and interactive keep criteria.
- Enforce `node.recycle()` discipline for processed children.
- Build and maintain `HashMap<Int, AccessibilityNodeInfo>` per snapshot.
- Unit test visibility filter, keep criteria, and index stability per crawl.

### Phase 3 - Groq integration and memory loop
- Implement Groq client + DTOs with strict schema validation.
- Add `reasoning_content` handling so only content JSON drives actions.
- Implement `SEARCH_MEMORY` multi-turn loop with Room query tool.
- Add timeout/retry/rate-limit policy from runtime section.

### Phase 4 - Action execution
- Implement click (bounds midpoint + `dispatchGesture`).
- Implement type action (`ACTION_SET_TEXT`) and basic scroll action.
- Enforce `refresh()` anti-stale check before acting.

### Phase 5 - Tracer bullet orchestrator (chat-first)
- Wire overlay text chat -> prune -> infer -> verify -> execute -> feedback.
- Implement confidence guardrail branch.
- Add dead-zone sentry with `ConnectivityManager`:
  - On disconnect: immediately interrupt TTS, speak "Connection lost. Pausing CoDrive.", hard-pause execution, and temporarily unbind scraper work to reduce battery drain.
  - On reconnect: speak "Connection restored." and wait for next user command.

### Phase 6 - Voice overlay and interruption
- Integrate streaming Sherpa-ONNX + Piper for full-duplex session behavior.
- Add bubble overlay lifecycle (show, active, dismiss).
- Implement barge-in: stop TTS immediately when user interrupts speech.

### Phase 7 - Safety and observability hardening
- Add safe logging (no sensitive full-tree dumps in production logs).
- Complete UX for safety mode controls (allow-all / allowlist / risky-confirm).
- Add telemetry counters for stale-node aborts, low-confidence clarifications, and rate-limit events.

## Definition of Done (MVP)
- A command can query local Room memory and execute a visible UI action selected by model index.
- Stale nodes, unreadable WebView states, low confidence, and network loss are blocked safely.
- Groq reasoning traces are optionally logged, while only strict JSON output controls behavior.
- End-to-end tracer bullet works on a real device using the overlay chat path.

## Open Decisions To Resolve Before Full Build
1. Voice bubble UX details: exact gesture/animation for drag-to-dismiss and inactive state behavior?




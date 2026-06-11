# CoDrive

CoDrive is an Android 13+ accessibility proxy for overlay chat and future voice-bubble control.

## Current phase
- Phase 0: baseline setup
- Phase 1: core models, interfaces, and Room memory contracts
- Phase 2: semantic pruner and node registry
- Phase 3: Groq strict JSON parsing and SEARCH_MEMORY loop scaffold
- Phase 4: action execution scaffold (click/type/scroll + refresh anti-stale)
- Phase 5: chat-first tracer bullet orchestration scaffold

## Build and test
```powershell
./gradlew test
```

```powershell
./gradlew connectedDebugAndroidTest
```

```powershell
./gradlew test assembleDebugAndroidTest
```

## Notes
- Package name: `com.codrive.ai`
- Min SDK: 33
- Room is scaffolded for durable identity memory and rolling session context
- Configure LLM keys locally only (do not commit):
  - Groq: `local.properties` -> `GROQ_API_KEY=your_key_here` or environment variable `GROQ_API_KEY`
  - OpenRouter: `local.properties` -> `OPENROUTER_API_KEY=your_key_here` or environment variable `OPENROUTER_API_KEY`
  - OpenRouter Owl Alpha uses the model slug `openrouter/owl-alpha`
- Main launcher behavior: never auto-opens accessibility settings; user taps manual button when service is OFF
- Phase 3 adds strict Groq JSON parsing, retry/rate-limit handling, and a bounded memory lookup loop
- Phase 2 adds DFS pruning, explicit role mapping, live node indexing, and unreadable-screen fail-closed behavior
- Overlay bubble scaffold and launcher seam: `docs/overlay-bubble-entrypoint.md`
- Gesture coverage now includes global actions + directional swipes (including notification shade style actions)
- Bubble overlay now supports inline transcript + send box (commands can run without opening `ChatActivity`)
- Accessibility scrape root prefers non-CoDrive windows so overlay/chat UI is excluded from automation context
- Overlay now includes draft always-listening STT with pause-based auto-submit and best-effort interruption of in-flight requests

## Model bootstrap
- Launch the in-app Model Bootstrap screen from the home page to download and verify offline models.
- Models are stored under `noBackupFilesDir/models` with subfolders for `vlm`, `stt`, and `tts`.
- Sherpa voice toggle is only enabled after all required models are verified.
- Downloads use WorkManager with Wi-Fi/charging constraints from the bootstrap UI.


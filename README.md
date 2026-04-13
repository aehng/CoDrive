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
- Configure Groq key locally only (do not commit):
  - `local.properties` -> `GROQ_API_KEY=your_key_here`
  - or environment variable `GROQ_API_KEY`
- Main launcher behavior: never auto-opens accessibility settings; user taps manual button when service is OFF
- Phase 3 adds strict Groq JSON parsing, retry/rate-limit handling, and a bounded memory lookup loop
- Phase 2 adds DFS pruning, explicit role mapping, live node indexing, and unreadable-screen fail-closed behavior


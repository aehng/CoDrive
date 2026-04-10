# CoDrive

CoDrive is an Android 13+ accessibility proxy for overlay chat and future voice-bubble control.

## Current phase
- Phase 0: baseline setup
- Phase 1: core models, interfaces, and Room memory contracts
- Phase 2: semantic pruner and node registry

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
- The tracer-bullet execution loop will come in the next phase
- Phase 2 adds DFS pruning, explicit role mapping, live node indexing, and unreadable-screen fail-closed behavior


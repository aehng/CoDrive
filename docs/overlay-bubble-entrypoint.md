# Overlay Bubble Entrypoint (Tracer Bullet Scaffold)

This document defines the launcher-ready seam for running chat outside `ChatActivity`.

## Goal

Move launcher responsibility out of `ChatActivity` so future entrypoints (persistent bubble, voice trigger, quick tile) can launch the same chat flow.

## Entrypoint Contract

`app/src/main/java/com/codrive/ai/launcher/ChatLauncherEntryPoint.java`

- `newChatIntent(context)`: canonical intent for opening `ChatActivity`.
- `newStartOverlayIntent(context)`: starts `OverlayBubbleService`.
- `newStopOverlayIntent(context)`: stops `OverlayBubbleService`.

All launcher surfaces should use this contract instead of constructing ad-hoc intents.

## Current Scaffold

`app/src/main/java/com/codrive/ai/overlay/OverlayBubbleService.java`

- Service-backed overlay bubble using `TYPE_APPLICATION_OVERLAY`.
- Bubble can be dragged.
- Tap bubble toggles a mini panel.
- Inline panel includes transcript + command box + send button.
- Commands execute directly from the bubble (no `ChatActivity` handoff required).
- Panel actions:
  - `Open Chat`: launches `ChatActivity` via `ChatLauncherEntryPoint`.
  - `Close Bubble`: stops the overlay service.
- Permission gate: requires "Display over other apps" (`Settings.canDrawOverlays`).

## Accessibility Root Safety

- `CoDriveAccessibilityService` tracks the latest non-CoDrive root and exposes `getLatestAutomationRootNode()`.
- Command execution uses that accessor so scraping targets the underlying app screen and not CoDrive overlay/chat UI.
- Overlay panel is collapsed to non-focusable mode before command execution to reduce focus interference.

## Launcher Wiring

`app/src/main/java/com/codrive/ai/MainActivity.java`

- `Start Chat` now uses `ChatLauncherEntryPoint.newChatIntent(...)`.
- New `Start Bubble Overlay` button starts overlay through `ChatLauncherEntryPoint`.
- If overlay permission is missing, launcher opens `ACTION_MANAGE_OVERLAY_PERMISSION`.

## Next Integration Steps

1. Move command submission and transcript state into a shared coordinator interface so both `ChatActivity` and bubble panel can send commands directly.
2. Add microphone button in bubble panel and route to future STT engine.
3. Add voice feedback toggle in bubble panel for future TTS integration.
4. Promote overlay service to foreground mode when made persistent.



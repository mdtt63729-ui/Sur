# Player open/close animation — state of the port

What this is: the mini-player ⇄ full-player transition from BitChord, as far as it has been
carried in this app, plus everything a developer with a phone in hand needs to finish it.

## What is in the tree

- `ui/playertransition/PlayerDock.kt` — BitChord's dock: the mini player's cover (reported by
  `MiniPlayer.kt`), the sheet's travel and offset, and the single `transitionProgress` both ends
  read. `LocalPlayerDock` is provided in `App.kt`.
- `ui/playertransition/PlayerTransitionController.kt` — the spring (195f, critically damped) with
  `open()`, `close(onFinished)`, `manualProgress`.
- `ui/playertransition/PlayerArtworkPortal.kt` — the travelling cover, composed above the player in
  `NowPlayingScreen.kt`.
- `ui/playertransition/MiniPlayerGestures.kt` — pull-to-open from the bar.
- `NowPlayingScreen.kt` — a `ModalBottomSheet` (its own window, as BitChord's is). It sets
  `sheetTravelPx` / `sheetOffsetProvider` from its own size, samples `sheetState.requireOffset()`
  into `dock.transitionProgress`, and routes dismissal through `transitionController.close { … }`.

## Four bugs already found and fixed — do not reintroduce them

1. **Zero travel.** The slide distance was computed from a height that was still 0 when the open
   started, so the spring animated 0 → 0. Seed the travel before starting.
2. **Zero start position.** `open()` read `positionPx()`, which answers 0 while the player stands
   open, so it slid 0 → 0 again. An open must start from the closed position.
3. **Alpha gated on the transition.** The player's content alpha rode `transitionProgress`
   (`0.15 + 0.85t`); when progress never advanced the whole player sat at 15% opacity and looked
   frozen. Content alpha is now fixed at 1.
4. **Hide blocked.** `confirmValueChange` refused every `SheetValue.Hidden` unless one close path
   had set a flag, so a back press — which asks the sheet to hide directly — was swallowed and the
   player could not be closed. It always allows the hide now.

## What is still open

- `PlayerTransitionController.open()` is **not called**. The portal currently animates only from
  the sheet's own movement, which is short. Calling `open()` from a `LaunchedEffect` arms
  `transitionActive`, but it must then reach `false` again or the player stays mid-transition —
  that is what made it look stuck once already.
- The artwork portal's destination comes from the player's own cover rectangle. Apple Music reports
  it (`NowPlayingContentAppleMusic.kt`, position only — the thumbnail quality lock forbids touching
  the URL, size, cache key or decode). The other three styles do not report yet.
- BitChord's original files (GPL-3.0) are in the `player-dock-animation` pack: `PlayerDock.kt`,
  `PlayerSheetMotion.kt`, `MiniPlayerPull.kt`, and three integration snippets.

## The honest position

None of this has been run on a device from this environment. Every fix above was found by reading
code and stepping through screen recordings frame by frame. The motion has not been seen working.

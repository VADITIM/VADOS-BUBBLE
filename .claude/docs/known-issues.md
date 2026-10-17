# Known issues

Open bugs are tracked in the vault ledger (`Improvements Working.md`). Recorded here are the ones a change knowingly left open.

- **The Dashboard's first frame still pays several forced layouts.** `pinCornerWidths` and `handOverClock` each measure the corner bubbles before and after a class change, by design (measure and put back), and `rankQuickBubbles` measures after them. Folding these into one read batch means restructuring the corner handover in `clock.js`. That should only be done against a trace from the phone (architecture.md, profiling over DevTools).
- **The Notifications expand builds its whole list before the growth starts.** `openNotifications` parses `readNotifications()` and builds every row before `setSize`, because the window's height is measured off the built list. Moving it after the transition needs a height that is known in advance; this has not been measured.
- **The Status proxy is relaid on the Dashboard's first frame.** `fitStatusProxy` takes the window to full screen as the panel opens, which is one `relayoutWindow` on the host's main thread. Deferring it would let early taps on the panel fall through to the app underneath, so it stays.

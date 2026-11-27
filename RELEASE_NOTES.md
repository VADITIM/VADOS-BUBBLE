# 0.8.0

The bubble is rebuilt natively in Jetpack Compose, replacing the WebView canvas, and `compose` is now the main branch.

- **Dashboard:** the connections section sits lower; holding one slider grows the other into it without a bounce and lifts the held bar to the screen centre; the connection buttons rotate-pop in and out where they stand; the battery and stats pills leave upwards past the screen top.
- **Notifications menu:** a group shows at most two stacked layers, which slide out of the group's pill on entry and tuck back into it on exit; groups open and close by dragging down and up, following the finger; swiping a row away no longer bounces the list; rows keep one corner radius.
- **Clock:** the seconds sit closer together, further from hh:mm, in the same colour.
- **Main:** stretches over the punch hole while it bounces home from the Dashboard.
- **Media player:** the timeline wipes in left to right while the closed equalizer pops out, and the equalizer pops back in on close.
- **Earlier on `compose`:** transfer mod, swipe-to-trash notification rows, the Now pill in the Dashboard, the flashlight in Main, the player fold, satellites and the bar scrim.

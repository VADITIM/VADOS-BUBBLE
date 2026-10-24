# 0.6.5

- Dashboard toggles: after holding a connection button to open its settings, the bottom row of toggles ignored taps until a connection button was pressed again. Each hold now only silences the button that was held.
- Battery module: a tap opens the battery page instead of switching the energy saver.
- Energy saver: turning it on sets the screen to 60Hz (Standard motion smoothness), and turning it off restores the previous mode.
- Extra dim and the energy saver are written directly to Settings from the tap, instead of waiting for a shell command behind the panel's read.
- Hotspot: every version of One UI's hotspot call is tried, and a refusal is logged rather than swallowed.
- Notifications menu: a tapped notification is opened with this app's start rights whatever windows are standing, and a notification that opens nothing is logged.
- The Comdirect toggle's icon moved 1px left.

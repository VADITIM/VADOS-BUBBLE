import { PROXY_TAP_SLOP } from './bridge.js';
import { stirLiquid } from './liquid.js';
import { rubberBandPast, toy, untoy } from './motion.js';
import { closeNowPanel, nowOpen } from './now.js';
import { toClosed } from './row.js';
import { GROWN_PAD, HOLD_MILLIS, bridge, root, shared } from './state.js';

/**
 * The bubble at the right end of the bar, standing on the system's own icons.
 *
 * It carries no mod: what the phone is *attached* to is true all the time, so this bubble is read
 * rather than watched. That is the line between it and the Now bubble at the other end — Now
 * carries what is happening, a torch burning or a file arriving, and it comes and goes with it;
 * Status carries what is connected and stands there for as long as the bar does. The one thing it
 * announces is the phone being plugged in, which is news about the reading it is already holding —
 * see the charge announcement below.
 *
 * It is still a bubble, and it answers everything a bubble answers: it is pressed, it is held,
 * it is pulled down, and it opens. It was a box that only reported for a long time and it read
 * as one — a shape on the bar with no press and no state while every other shape had both.
 * A few pixels shorter than the bubbles that carry a mod is the whole of the difference now.
 */
export const statusPill = document.getElementById('status');
const statusClosed = document.getElementById('status-closed');
const statusFaces = {
  closed: statusClosed,
  charge: document.getElementById('status-charge'),
  panel: document.getElementById('status-panel'),
};

/** Mirrors --status-right in pill.css: how far its right edge stands off the screen edge. Where the bubble stands, and not something to give up for reaching the edge — see CLOCK_LEFT, which was zeroed once and slid its digits along with the box. */
const STATUS_RIGHT = 14;

/** Its flight out of the punch hole, matching the Now bubble's at the other end. */
const STATUS_TRAVEL = 420;
const STATUS_LAND = 0.42;

/* The panel it opens into, and it is the screen now.
   It was the media player's box — a panel is a panel, and two open bubbles at two widths read as
   two different kinds of thing — and what that bought was nine controls and a battery crammed into
   a card while the rest of the screen sat empty behind them. The charge is a *level*, and a level
   wants height more than anything else in this project; the controls want a thumb's worth of room
   each. Both of those are the whole screen, so it takes the whole screen.
   What does not change is that it is still one bubble: it grows out of the closed one, it is drawn
   on the same canvas, it merges with whatever it passes, and it is still taken downwards — the top
   edge is the line the row rests on and an open bubble that climbs is one that has left the bar.
   Its size is measured rather than written down, because the screen is not a constant. */
/** How far the panel stands off the screen's edges, and it is one number on all four sides now: the panel is the whole screen, so the bottom row stands the same ten pixels off the bottom edge that the sides stand off theirs. The foot was thirty, to keep the transfer bubble clear of the gesture handle — the handle is drawn over the panel rather than under it and the twenty pixels bought nothing but a strip of frosted wallpaper along the bottom of every open. */
const STATUS_MARGIN = 10;
const STATUS_MARGIN_FOOT = STATUS_MARGIN;
const STATUS_PANEL = { width: 0, height: 0, ms: 220 };

/**
 * The screen, in the page's own units — and **never `window.innerWidth`**, which is not it.
 *
 * The canvas window is exactly the display (1080×2340 at a device ratio of 3, so 360×780 to the
 * page), and the WebView reports `innerWidth` 404 and `innerHeight` 877 inside it: the layout
 * viewport it hands out is wider and taller than the surface it is drawn on, by 44 and 97. Sized
 * off that, the panel was 384 wide on a 360-wide screen and overhung both edges — the battery glyph
 * cut off at the left, the percentage cut off at the right, and the controls pushed off the bottom.
 * The document's own client box is the surface, so it is the one to measure. This is the same trap
 * as "the page does not know how wide the screen is" in [bubbles.md] — it does, but only if it asks
 * the right question, and the wrong one is the one that reads as correct on every smaller shape.
 */
const screenWidth = () => document.documentElement.clientWidth;
const screenHeight = () => document.documentElement.clientHeight;

/**
 * The panel's box, from the screen's. The top is the closed bubble's own — the CSS places the open
 * one at exactly that line plus the pad every grown bubble drops by — so it is read off the bubble
 * instead of re-derived from the three custom properties the host writes it from.
 */
/**
 * How far clear of the row the panel stands, on top of the pad every grown bubble drops by. The pad
 * is measured from the line the closed row rests on and the row's bubbles are 34 tall, so the pad
 * alone put the panel's top row eight pixels *above* their bottoms — well inside the goo's reach,
 * and the connectors grew necks up into the clock and the main bubble. Mirrors `--quick-clearance`
 * in pill.css, which is what places the charge; this is what places the frame beside it, and the two
 * have to land on one line.
 */
const QUICK_CLEARANCE = 22;

function fitStatusPanel() {
  const top = statusPill.getBoundingClientRect().top + GROWN_PAD + QUICK_CLEARANCE;
  STATUS_PANEL.width = Math.round(screenWidth() - STATUS_MARGIN * 2);
  STATUS_PANEL.height = Math.round(screenHeight() - top - STATUS_MARGIN_FOOT);
  root.style.setProperty('--status-panel-width', STATUS_PANEL.width + 'px');
  root.style.setProperty('--status-panel-height', STATUS_PANEL.height + 'px');
  // The same box, handed to the frame the modules are laid out in. It is written here rather than
  // in the stylesheet because the top of it is read off the bubble, and the bubble does not exist
  // at load — this is the one function that runs when it does.
  root.style.setProperty('--quick-left', Math.round(statusPanelLeft()) + 'px');
  root.style.setProperty('--quick-top', Math.round(top) + 'px');
  root.style.setProperty('--quick-width', STATUS_PANEL.width + 'px');
  root.style.setProperty('--quick-height', STATUS_PANEL.height + 'px');
}
fitStatusPanel();

/** How far down the finger has to pull before the pull is an ask rather than a wander. */
const STATUS_PULL = 34;

/**
 * The same ask, when the gesture was taken away instead of finished.
 *
 * This bubble stands in the status bar, which is where SystemUI's own shade gesture starts, and
 * SystemUI pilfers the pointer partway down: the page sees a cancel with the finger still
 * moving, not a lift. That is why the pull worked sometimes and not others — it was not the
 * threshold being missed, it was the gesture being confiscated before the threshold arrived.
 * A cancel mid-pull is *stronger* evidence of an ask than a lift is, so it commits at a lower
 * bar, which is the rule motion.md already states for the main bubble's pull.
 */
const STATUS_PULL_STOLEN = 12;

/**
 * How long a slot takes to arrive or leave, and how long the bubble's answering bounce lasts.
 * Mirrors the transitions on .status-slot in pill.css.
 */
const SLOT_MOVE = 260;
const SLOT_BOUNCE = 220;

/**
 * Where each thing stands along the bubble, ascending left to right. Slots rather than an
 * order: a number can be given to a new reading without touching what is already there, and
 * what appears between two existing icons is decided by the number rather than by where the
 * code happened to push it.
 */
const SLOTS = { usb: 0, transfer: 1, modus: 7, link: 8, battery: 9 };

/**
 * The one glyph set. Every icon here is drawn rather than named, because the payload is
 * data — the watcher says what is attached and never what it looks like.
 *
 * Half of them are stroked rather than filled now, and a stroked one has to carry `fill="none"` on the shape itself: the stylesheet writes `fill: currentColor` on the `<svg>`, which beats a presentation attribute on that same element but loses to one on the child — so an icon that only says it on the parent comes out as a solid blob.
 */
const GLYPHS = {
  wifi: '<svg viewBox="0 0 24 24"><path d="M1.33309 8.07433C0.92156 8.44266 0.886539 9.07485 1.25487 9.48638C1.62319 9.89791 2.25539 9.93293 2.66691 9.5646L1.33309 8.07433ZM21.3331 9.5646C21.7446 9.93293 22.3768 9.89791 22.7451 9.48638C23.1135 9.07485 23.0784 8.44266 22.6669 8.07433L21.3331 9.5646ZM12 19C11.4477 19 11 19.4477 11 20C11 20.5523 11.4477 21 12 21V19ZM12.01 21C12.5623 21 13.01 20.5523 13.01 20C13.01 19.4477 12.5623 19 12.01 19V21ZM14.6905 17.04C15.099 17.4116 15.7315 17.3817 16.1031 16.9732C16.4748 16.5646 16.4448 15.9322 16.0363 15.5605L14.6905 17.04ZM18.0539 13.3403C18.4624 13.7119 19.0949 13.682 19.4665 13.2734C19.8381 12.8649 19.8082 12.2324 19.3997 11.8608L18.0539 13.3403ZM7.96372 15.5605C7.55517 15.9322 7.52524 16.5646 7.89687 16.9732C8.2685 17.3817 8.90095 17.4116 9.3095 17.04L7.96372 15.5605ZM4.60034 11.8608C4.19179 12.2324 4.16185 12.8649 4.53348 13.2734C4.90511 13.682 5.53756 13.7119 5.94611 13.3403L4.60034 11.8608ZM2.66691 9.5646C5.14444 7.34716 8.41371 6 12 6V4C7.90275 4 4.16312 5.54138 1.33309 8.07433L2.66691 9.5646ZM12 6C15.5863 6 18.8556 7.34716 21.3331 9.5646L22.6669 8.07433C19.8369 5.54138 16.0972 4 12 4V6ZM12 21H12.01V19H12V21ZM12 16C13.0367 16 13.9793 16.3931 14.6905 17.04L16.0363 15.5605C14.9713 14.5918 13.5536 14 12 14V16ZM12 11C14.3319 11 16.4546 11.8855 18.0539 13.3403L19.3997 11.8608C17.4466 10.0842 14.8487 9 12 9V11ZM9.3095 17.04C10.0207 16.3931 10.9633 16 12 16V14C10.4464 14 9.02872 14.5918 7.96372 15.5605L9.3095 17.04ZM5.94611 13.3403C7.54544 11.8855 9.66815 11 12 11V9C9.15127 9 6.55344 10.0842 4.60034 11.8608L5.94611 13.3403Z" fill="currentColor"/></svg>',
  ethernet: '<svg viewBox="0 0 24 24"><path d="M7 3h10a2 2 0 0 1 2 2v5h-3v3h-2v-3h-4v3H8v-3H5V5a2 2 0 0 1 2-2zm-2 12h14v4a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2z"/></svg>',
  bluetooth: '<svg viewBox="0 0 24 24"><path fill="none" d="M7 17L17 7L12 2V22L17 17L7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  usb: '<svg viewBox="0 0 24 24"><path d="M11 2h2l1.5 2.5h-5zM11 5h2v10.2l3-2.4V10h-1.5V7.5h4V10H17v3.4l-4.8 3.8V19a2 2 0 1 1-2 0v-3.6l-3.4-2.6V10.9a2 2 0 1 1 2 0v.9l1.4 1.1z"/></svg>',
  // A file in flight. The same three drawings the clock bubble wore for it, moved here with the
  // mod: an arrow pointing the way the file is moving, and the tick it wears for a moment when it
  // lands. One shape for the two directions, because which of them it is *is* the direction.
  download: '<svg viewBox="0 0 24 24"><path d="M11 3h2v9.2l3.3-3.3 1.4 1.4L12 16l-5.7-5.7 1.4-1.4L11 12.2zM5 18h14v2H5z"/></svg>',
  upload: '<svg viewBox="0 0 24 24"><path d="M12 3l5.7 5.7-1.4 1.4L13 6.8V16h-2V6.8L7.7 10.1 6.3 8.7zM5 18h14v2H5z"/></svg>',
  transferDone: '<svg viewBox="0 0 24 24"><path d="M9.8 16.2 5.6 12l-1.4 1.4 5.6 5.6L20.4 7.9 19 6.5z"/></svg>',
  hotspot: '<svg viewBox="0 0 24 24"><path d="M12 9.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5zM7.8 5.8 6.4 4.4a10 10 0 0 0 0 15.2l1.4-1.4a8 8 0 0 1 0-12.4zm9.8-1.4-1.4 1.4a8 8 0 0 1 0 12.4l1.4 1.4a10 10 0 0 0 0-15.2z"/></svg>',
  // The pin, and not a globe or a compass: what this switch decides is whether the phone may work
  // out where it is standing, and the pin is the mark that answers exactly that question.
  gps: '<svg viewBox="0 0 24 24"><path d="M12 2a7 7 0 0 0-7 7c0 5.2 7 13 7 13s7-7.8 7-13a7 7 0 0 0-7-7zm0 4.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5z"/></svg>',
  // Aeroplane mode. Drawn as the plane rather than as every radio crossed out at once, because the
  // one thing every phone on earth agrees this switch is called is the picture of the aircraft.
  plane: '<svg viewBox="0 0 24 24"><path d="M21 16v-2l-8-5V3.5a1.5 1.5 0 0 0-3 0V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5z"/></svg>',
  // Do not disturb, drawn as the bar through the circle rather than as a crossed-out bell: it
  // is a mode the phone is in, not a notification that was refused.
  zen: '<svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 2a8 8 0 0 1 8 8 8 8 0 0 1-8 8 8 8 0 0 1-8-8 8 8 0 0 1 8-8zM7.5 11h9v2h-9z"/></svg>',
  // A mobile link whose generation the phone will not name. The bars are the icon it stands on,
  // and a link that is up with nothing to say about itself is still a link the bubble owes.
  mobile: '<svg viewBox="0 0 24 24"><path d="M4 16h3v4H4zm5-3h3v7H9zm5-4h3v11h-3zm5-5h3v16h-3z"/></svg>',
  // One glyph per mode One UI can be kept quiet by. Samsung does not put its own icon on the
  // rule — `iconResName` is null on every Lifestyle mode this phone has — so there is nothing to
  // load and draw, and the mode is recognised by its name instead. A name nobody here knows gets
  // the plain ring, which is what every mode used to get.
  zenSleep: '<svg viewBox="0 0 24 24"><path d="M20.7 14.3A8.5 8.5 0 0 1 9.7 3.3 9 9 0 1 0 20.7 14.3z"/></svg>',
  zenHeart: '<svg viewBox="0 0 24 24"><path d="M12 21S3.5 15.4 3.5 9.4A4.9 4.9 0 0 1 12 6.2a4.9 4.9 0 0 1 8.5 3.2C20.5 15.4 12 21 12 21z"/></svg>',
  zenWork: '<svg viewBox="0 0 24 24"><path d="M9 4h6a2 2 0 0 1 2 2v1h3a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h3V6a2 2 0 0 1 2-2zm0 3h6V6H9z"/></svg>',
  zenDrive: '<svg viewBox="0 0 24 24"><path d="M6.5 5h11l2 6H4.5zM4 12h16a1 1 0 0 1 1 1v5h-3v-2H6v2H3v-5a1 1 0 0 1 1-1zm2.5 2a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zm11 0a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z"/></svg>',
  zenExercise: '<svg viewBox="0 0 24 24"><path d="M4 9h2v6H4zm14 0h2v6h-2zM7 7h2v10H7zm8 0h2v10h-2zM9.5 11h5v2h-5z"/></svg>',
  zenGame: '<svg viewBox="0 0 24 24"><path d="M7 7h10a5 5 0 0 1 0 10 4 4 0 0 1-2.8-1.2L13 14.6h-2l-1.2 1.2A4 4 0 0 1 7 17a5 5 0 0 1 0-10zm-1.5 3v1.5H4v2h1.5V15h2v-1.5H9v-2H7.5V10zm10 0a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0-2.4z"/></svg>',
  // Do not disturb, drawn as the sign it is everywhere else: a ring with a bar across it. There is no
  // off face for it and there should not be — a mode has one shape and the colour says which end it
  // is at, and a ring with the bar taken out is a ring, which is a different symbol rather than this
  // one switched off. See GLYPHS_OFF.
  modus: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/><path d="M7.5 11h9v2h-9z"/></svg>',
  dim: '<svg viewBox="-7.5 0 32 32"><path fill="currentColor" d="M9.75 8.25v0.219c0 0.844-0.375 1.25-1.156 1.25s-1.125-0.406-1.125-1.25v-0.219c0-0.813 0.344-1.219 1.125-1.219s1.156 0.406 1.156 1.219zM12.063 9.25l0.156-0.188c0.469-0.688 1.031-0.781 1.625-0.344 0.625 0.438 0.719 1.031 0.25 1.719l-0.188 0.156c-0.469 0.688-1.031 0.781-1.625 0.313-0.625-0.438-0.688-0.969-0.219-1.656zM5 9.063l0.125 0.188c0.469 0.688 0.406 1.219-0.188 1.656-0.625 0.469-1.219 0.375-1.688-0.313l-0.125-0.156c-0.469-0.688-0.406-1.281 0.188-1.719 0.625-0.438 1.219-0.281 1.688 0.344zM8.594 11.125c2.656 0 4.844 2.188 4.844 4.875 0 2.656-2.188 4.813-4.844 4.813-2.688 0-4.844-2.156-4.844-4.813 0-2.688 2.156-4.875 4.844-4.875zM1.594 12.5l0.219 0.063c0.813 0.25 1.063 0.719 0.844 1.469-0.25 0.75-0.75 0.969-1.531 0.719l-0.219-0.063c-0.781-0.25-1.063-0.719-0.844-1.469 0.25-0.75 0.75-0.969 1.531-0.719zM15.375 12.563l0.219-0.063c0.813-0.25 1.313-0.031 1.531 0.719s-0.031 1.219-0.844 1.469l-0.188 0.063c-0.813 0.25-1.313 0.031-1.531-0.719-0.25-0.75 0.031-1.219 0.813-1.469zM8.594 18.688c1.469 0 2.688-1.219 2.688-2.688 0-1.5-1.219-2.719-2.688-2.719-1.5 0-2.719 1.219-2.719 2.719 0 1.469 1.219 2.688 2.719 2.688zM0.906 17.281l0.219-0.063c0.781-0.25 1.281-0.063 1.531 0.688 0.219 0.75-0.031 1.219-0.844 1.469l-0.219 0.063c-0.781 0.25-1.281 0.063-1.531-0.688-0.219-0.75 0.063-1.219 0.844-1.469zM16.094 17.219l0.188 0.063c0.813 0.25 1.063 0.719 0.844 1.469s-0.719 0.938-1.531 0.688l-0.219-0.063c-0.781-0.25-1.063-0.719-0.813-1.469 0.219-0.75 0.719-0.938 1.531-0.688zM3.125 21.563l0.125-0.188c0.469-0.688 1.063-0.75 1.688-0.313 0.594 0.438 0.656 0.969 0.188 1.656l-0.125 0.188c-0.469 0.688-1.063 0.75-1.688 0.313-0.594-0.438-0.656-0.969-0.188-1.656zM13.906 21.375l0.188 0.188c0.469 0.688 0.375 1.219-0.25 1.656-0.594 0.438-1.156 0.375-1.625-0.313l-0.156-0.188c-0.469-0.688-0.406-1.219 0.219-1.656 0.594-0.438 1.156-0.375 1.625 0.313zM9.75 23.469v0.25c0 0.844-0.375 1.25-1.156 1.25s-1.125-0.406-1.125-1.25v-0.25c0-0.844 0.344-1.25 1.125-1.25s1.156 0.406 1.156 1.25z"></path></svg>',
  rotate: '<svg viewBox="0 0 24 24"><path fill="none" d="M20.4898 14.9907C19.8414 16.831 18.6124 18.4108 16.9879 19.492C15.3635 20.5732 13.4316 21.0972 11.4835 20.9851C9.5353 20.873 7.67634 20.1308 6.18668 18.8704C4.69703 17.61 3.65738 15.8996 3.22438 13.997C2.79138 12.0944 2.98849 10.1026 3.78602 8.32177C4.58354 6.54091 5.93827 5.06746 7.64608 4.12343C9.35389 3.17941 11.3223 2.81593 13.2546 3.08779C16.5171 3.54676 18.6725 5.91142 21 8M21 8V2M21 8H15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  saver: '<svg viewBox="0 0 24 24"><path d="M9 2h6v2h1.5A1.5 1.5 0 0 1 18 5.5v15A1.5 1.5 0 0 1 16.5 22h-9A1.5 1.5 0 0 1 6 20.5v-15A1.5 1.5 0 0 1 7.5 4H9zm3.6 5-4.1 7h2.6l-.7 5 4.1-7h-2.6z"/></svg>',
  // The disc, and the light in the middle of it: the ring is the switch and the centre is whether it is actually running, which is the one switch in this panel whose truth comes from outside the shell. Red is the same red the clock bubble goes while it records — one colour for one state, wherever it is being said.
  recording: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/><circle class="record-dot" cx="12" cy="12" r="4.5" fill="currentColor"/></svg>',
  mic: '<svg viewBox="0 0 24 24"><path fill="none" d="M12 17V21M12 21H9M12 21H15" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><rect fill="none" x="10" y="3" width="4" height="10" rx="2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path fill="none" d="M17.7378 12.7542C17.3674 13.9659 16.6228 15.0293 15.6109 15.7918C14.599 16.5544 13.3716 16.977 12.1047 16.9991C10.8378 17.0212 9.59647 16.6417 8.55854 15.9149C7.52061 15.1881 6.73941 14.1515 6.32689 12.9534" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
};

/**
 * The off face, for the controls that have one. A link that is down is not the same icon dimmed —
 * it is a different reading, and a slash through it says so at a glance where a colour has to be
 * compared against something to mean anything. Only the three that genuinely have an off *shape*
 * are in here; a control absent from this map keeps its one glyph whichever end it is at, which
 * is correct for a mode rather than a lack of one.
 */
const GLYPHS_OFF = {
  wifi: '<svg viewBox="0 0 24 24" fill="none"><path d="M1.33309 8.07433C0.92156 8.44266 0.886539 9.07485 1.25487 9.48638C1.62319 9.89791 2.25539 9.93293 2.66691 9.5646L1.33309 8.07433ZM21.3331 9.5646C21.7446 9.93293 22.3768 9.89791 22.7451 9.48638C23.1135 9.07485 23.0784 8.44266 22.6669 8.07433L21.3331 9.5646ZM12 19C11.4477 19 11 19.4477 11 20C11 20.5523 11.4477 21 12 21V19ZM12.01 21C12.5623 21 13.01 20.5523 13.01 20C13.01 19.4477 12.5623 19 12.01 19V21ZM14.6905 17.04C15.099 17.4116 15.7315 17.3817 16.1031 16.9732C16.4748 16.5646 16.4448 15.9322 16.0363 15.5605L14.6905 17.04ZM18.0539 13.3403C18.4624 13.7119 19.0949 13.682 19.4665 13.2734C19.8381 12.8649 19.8082 12.2324 19.3997 11.8608L18.0539 13.3403ZM7.96372 15.5605C7.55517 15.9322 7.52524 16.5646 7.89687 16.9732C8.2685 17.3817 8.90095 17.4116 9.3095 17.04L7.96372 15.5605ZM4.60034 11.8608C4.19179 12.2324 4.16185 12.8649 4.53348 13.2734C4.90511 13.682 5.53756 13.7119 5.94611 13.3403L4.60034 11.8608ZM10.5705 4.06305C10.0204 4.1118 9.61391 4.59729 9.66266 5.14741C9.71141 5.69754 10.1969 6.10399 10.747 6.05525L10.5705 4.06305ZM17.3393 10.3798C16.8567 10.1114 16.2478 10.285 15.9794 10.7677C15.711 11.2504 15.8847 11.8593 16.3673 12.1277L17.3393 10.3798ZM3.70711 2.29289C3.31658 1.90237 2.68342 1.90237 2.29289 2.29289C1.90237 2.68342 1.90237 3.31658 2.29289 3.70711L3.70711 2.29289ZM20.2929 21.7071C20.6834 22.0976 21.3166 22.0976 21.7071 21.7071C22.0976 21.3166 22.0976 20.6834 21.7071 20.2929L20.2929 21.7071ZM12 6C15.5863 6 18.8556 7.34716 21.3331 9.5646L22.6669 8.07433C19.8369 5.54138 16.0972 4 12 4V6ZM12 21H12.01V19H12V21ZM12 16C13.0367 16 13.9793 16.3931 14.6905 17.04L16.0363 15.5605C14.9713 14.5918 13.5536 14 12 14V16ZM9.3095 17.04C10.0207 16.3931 10.9633 16 12 16V14C10.4464 14 9.02872 14.5918 7.96372 15.5605L9.3095 17.04ZM10.747 6.05525C11.1596 6.01869 11.5775 6 12 6V4C11.5185 4 11.0417 4.0213 10.5705 4.06305L10.747 6.05525ZM16.3673 12.1277C16.9757 12.466 17.5412 12.874 18.0539 13.3403L19.3997 11.8608C18.7751 11.2927 18.0844 10.7941 17.3393 10.3798L16.3673 12.1277ZM2.29289 3.70711L5.46648 6.8807L6.8807 5.46648L3.70711 2.29289L2.29289 3.70711ZM2.66691 9.5646C3.81213 8.53961 5.12648 7.70074 6.56232 7.09494L5.78486 5.25224C4.14251 5.94517 2.64069 6.904 1.33309 8.07433L2.66691 9.5646ZM5.46648 6.8807L9.46042 10.8746L10.8746 9.46042L6.8807 5.46648L5.46648 6.8807ZM9.46042 10.8746L20.2929 21.7071L21.7071 20.2929L10.8746 9.46042L9.46042 10.8746ZM5.94611 13.3403C7.15939 12.2367 8.67355 11.4612 10.3496 11.1508L9.98543 9.18424C7.93271 9.5644 6.08108 10.5139 4.60034 11.8608L5.94611 13.3403Z" fill="currentColor"/></svg>',
  // The rune itself with a line struck through it, rather than a rune redrawn as half of itself:
  // the broken-up version read as a different symbol altogether, which is the one thing an off face
  // may not do — it has to be recognised as the same thing, switched off.
  bluetooth: '<svg viewBox="0 0 24 24" fill="none"><path d="M7 17L17 7L12 2V22L17 17L7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path d="M3.5 3.5L20.5 20.5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
  mobile: '<svg viewBox="0 0 24 24"><path d="M4 16h3v4H4zm5-3h3v7H9zm5-4h3v11h-3zm5-5h3v16h-3z" opacity="0.55"/><path fill="none" d="M3.5 3.5L20.5 20.5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
  mic: '<svg viewBox="0 0 24 24" fill="none"><path fill-rule="evenodd" clip-rule="evenodd" d="M9.00004 7.91421V11C9.00004 12.6569 10.3432 14 12 14C12.8503 14 13.6179 13.6463 14.1638 13.078L12.7487 11.6629C12.5655 11.8697 12.298 12 12 12C11.4478 12 11 11.5523 11 11V9.91422L9.00004 7.91421ZM13 9.08579V5C13 4.44772 12.5523 4 12 4C11.4478 4 11 4.44772 11 5V7.08579L9.00004 5.08579V5C9.00004 3.34315 10.3432 2 12 2C13.6569 2 15 3.34315 15 5V11C15 11.0283 14.9997 11.0565 14.9989 11.0846L13 9.08579ZM15.5782 14.4924C15.4023 14.6727 15.2121 14.8402 15.0091 14.9932C14.1658 15.6286 13.143 15.9808 12.0873 15.9992C12.0594 15.9997 12.0315 16 12.0036 16C10.977 16.0007 9.97424 15.6854 9.13216 15.0958C8.26722 14.4901 7.61622 13.6262 7.27245 12.6278C7.09264 12.1056 6.52356 11.8281 6.00136 12.0079C5.47917 12.1877 5.20161 12.7568 5.38141 13.279C5.86269 14.6767 6.77409 15.8862 7.98501 16.7341C8.88694 17.3656 9.92054 17.7724 11 17.9282V20H9.00004C8.44776 20 8.00004 20.4477 8.00004 21C8.00004 21.5523 8.44776 22 9.00004 22H12H15C15.5523 22 16 21.5523 16 21C16 20.4477 15.5523 20 15 20H13V17.9282C14.1618 17.7605 15.2678 17.3025 16.2127 16.5904C16.4905 16.3812 16.7509 16.1525 16.9925 15.9067L15.5782 14.4924ZM18.1876 14.2733L16.6785 12.7642C16.716 12.6648 16.7504 12.5639 16.7816 12.4619C16.943 11.9337 17.5021 11.6365 18.0302 11.7979C18.5584 11.9594 18.8556 12.5184 18.6942 13.0466C18.5639 13.4729 18.3938 13.8834 18.1876 14.2733Z" fill="currentColor"/><path d="M5 5L19 19" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
};

/** Which face a control wears at the end it is currently at. */
const glyphFor = (name, isOn) => (isOn ? GLYPHS[name] : GLYPHS_OFF[name] || GLYPHS[name]) || '';

/**
 * The bolt, and only the bolt. The panel draws the cell itself now and the bar's announcement is
 * the one place a whole battery is still drawn, so the four steps of charge went with the box:
 * a picture of a level standing inside the level is the same reading twice.
 */
const BATTERY_GLYPHS = {
  charging: '<svg viewBox="0 0 24 24"><path fill="none" d="M12.5 6L8.5 12H14.5L10.5 18M21 13V11M7.7 6H6.2C5.0799 6 4.51984 6 4.09202 6.21799C3.71569 6.40973 3.40973 6.71569 3.21799 7.09202C3 7.51984 3 8.0799 3 9.2V14.8C3 15.9201 3 16.4802 3.21799 16.908C3.40973 17.2843 3.71569 17.5903 4.09202 17.782C4.51984 18 5.0799 18 6.2 18H6.5M16.5 6H16.8C17.9201 6 18.4802 6 18.908 6.21799C19.2843 6.40973 19.5903 6.71569 19.782 7.09202C20 7.51984 20 8.0799 20 9.2V14.8C20 15.9201 20 16.4802 19.782 16.908C19.5903 17.2843 19.2843 17.5903 18.908 17.782C18.4802 18 17.9201 18 16.8 18H15.31" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
};

/**
 * What the announcement wears, one face per state, and all three are the same drawing: the cell
 * with a gap top and bottom and something passing through it. The bolt is being charged; a bar
 * standing alone in an otherwise empty cell is what is left; the bar with a dot under it is the
 * same reading once there is too little of it to be a reading any more. Adapted rather than
 * redrawn, because the three are one announcement in three moods and have to be recognised as
 * each other at a glance on a bar nobody is looking at.
 */
const ANNOUNCE_CELL = 'M21 13V11M7.7 6H6.2C5.0799 6 4.51984 6 4.09202 6.21799C3.71569 6.40973 3.40973 6.71569 3.21799 7.09202C3 7.51984 3 8.0799 3 9.2V14.8C3 15.9201 3 16.4802 3.21799 16.908C3.40973 17.2843 3.71569 17.5903 4.09202 17.782C4.51984 18 5.0799 18 6.2 18H6.5M16.5 6H16.8C17.9201 6 18.4802 6 18.908 6.21799C19.2843 6.40973 19.5903 6.71569 19.782 7.09202C20 7.51984 20 8.0799 20 9.2V14.8C20 15.9201 20 16.4802 19.782 16.908C19.5903 17.2843 19.2843 17.5903 18.908 17.782C18.4802 18 17.9201 18 16.8 18H15.31';
const announceGlyph = mark =>
  '<svg viewBox="0 0 24 24"><path fill="none" d="' + mark + ANNOUNCE_CELL +
  '" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';

const ANNOUNCE_GLYPHS = {
  charging: BATTERY_GLYPHS.charging,
  low: announceGlyph('M12 9.2V14.8'),
  critical: announceGlyph('M12 8.4V13M12 16.3V16.4'),
};

/** What each of them takes the bubble to. Once the shape has said "battery", the colour is the whole difference between running low and being nearly out. */
const ANNOUNCE_COLOURS = {
  charging: '#5bfd5b',
  low: '#ffb300',
  critical: '#ff3b30',
};


/**
 * A Modus is what is riding on top of the link — a paired device, a hotspot someone is using,
 * the phone being kept quiet. It colours the whole bubble and stands at its own slot, so the
 * bubble says what it is at a glance rather than by being read. Only one at a time: they are
 * ordered by how much the phone is currently doing for something else, which is what a
 * passenger cares about, and do not disturb comes last because it is the phone doing less.
 */
const MODUS_ORDER = ['hotspot', 'bluetooth', 'zen'];

/**
 * Which mode the phone is being kept quiet by, read off the name the person gave it. One UI's
 * Modes are one zen rule each and Samsung keeps its own icon for them out of the rule, so the
 * name is the only handle there is — and it is a good one, because the person wrote it. German
 * first because the phone is German, English beside it because half of these modes are named in
 * it anyway, and the heart is matched on what people actually type rather than on the word.
 */
const ZEN_MODES = [
  { glyph: 'zenSleep', words: ['schlaf', 'nacht', 'sleep', 'night', 'bett', 'bed'] },
  { glyph: 'zenHeart', words: ['<3', '❤', '♥', 'herz', 'liebe', 'love', 'heart'] },
  { glyph: 'zenWork', words: ['arbeit', 'work', 'büro', 'buro', 'office', 'fokus', 'focus', 'lern', 'study'] },
  { glyph: 'zenDrive', words: ['fahr', 'auto', 'driv', 'car', 'pendel', 'commut'] },
  { glyph: 'zenExercise', words: ['sport', 'training', 'workout', 'fitness', 'lauf', 'run', 'gym'] },
  { glyph: 'zenGame', words: ['spiel', 'gam', 'zock'] },
];

/** The glyph for however the phone is being kept quiet, and the plain ring for a mode unread. */
function zenGlyph() {
  const name = (attached && attached.zenName ? attached.zenName : '').toLowerCase();
  const found = name && ZEN_MODES.find(mode => mode.words.some(word => name.includes(word)));
  return GLYPHS[found ? found.glyph : 'zen'];
}
const MODUS_COLOUR = {
  bluetooth: '#3d8bff',
  hotspot: '#ff9b3d',
  zen: '#a07dff',
};

let attached = null;
/**
 * The file in flight, or null. It is this bubble's now: a transfer is a thing the phone is doing
 * for something attached to it, and it used to stand in the clock beside a torch and a recording —
 * which are things the *person* started. It is one slot and nothing else here yet: no reading, no
 * timeline, no tap. What it says at this size is that a file is moving and which way.
 */
let transfer = null;
let charge = -1;
let isPlugged = false;
let isBorn = false;

/** Which connection a hold on the bubble is about: the Modus if there is one, else the link. */
function subject() {
  if (!attached) return 'none';
  return MODUS_ORDER.find(kind => hasModus(kind)) || attached.link;
}

function hasModus(kind) {
  if (!attached) return false;
  if (kind === 'bluetooth') return Boolean(attached.bluetooth);
  if (kind === 'hotspot') return Boolean(attached.hotspot);
  if (kind === 'zen') return Boolean(attached.zen);
  return false;
}

/**
 * What each slot holds right now, in slot order, or nothing where there is nothing true.
 *
 * The wifi glyph is not drawn when there is no wifi, and there is no crossed-out one either:
 * a link that is down is not a thing the phone is attached to, and an icon for it is the
 * status bar telling the user about an absence. Either the wifi, or which generation the
 * mobile link is, or that slot simply is not there.
 */
function wanted() {
  const shown = [];
  if (attached && attached.usb) shown.push({ name: 'usb', html: GLYPHS.usb });
  if (transfer) {
    shown.push({
      name: 'transfer',
      html: transfer.isDone ? GLYPHS.transferDone : GLYPHS[transfer.mod],
    });
  }
  const modus = MODUS_ORDER.find(kind => hasModus(kind));
  if (modus) shown.push({ name: 'modus', html: modus === 'zen' ? zenGlyph() : GLYPHS[modus] });
  if (attached && attached.link === 'wifi') {
    shown.push({ name: 'link', html: GLYPHS.wifi, level: attached.level });
  } else if (attached && attached.link === 'ethernet') {
    shown.push({ name: 'link', html: GLYPHS.ethernet });
  } else if (attached && attached.link === 'mobile') {
    // The generation when the phone will name it, the bars when it will not: a link that is up
    // is a thing the phone is attached to either way, and leaving the slot out for want of a
    // word made a mobile connection read as no connection at all.
    if (attached.generation) shown.push({ name: 'link', text: attached.generation });
    else shown.push({ name: 'link', html: GLYPHS.mobile });
  }
  // The paired device's charge stands in front of the phone's own, because it is the one that
  // is news: a phone is charged by the person holding it and a pair of headphones runs out in
  // the middle of something. The phone's own is always rightmost either way.
  const paired = attached && attached.bluetooth && attached.bluetooth.charge >= 0
    ? attached.bluetooth.charge
    : -1;
  // The phone's own charge is wrapped rather than written flat, because it is the half that goes yellow and then red and the paired device's must not go with it — two readings in one slot, one of which has an alarm.
  const own = charge >= 0
    ? '<span class="own-charge" data-charge="' + chargeBand() + '">' + charge + '%</span>'
    : '';
  const reading = (paired >= 0 ? paired + '% · ' : '') + own;
  if (reading) shown.push({ name: 'battery', html: reading, isText: true });
  return shown.sort((one, two) => SLOTS[one.name] - SLOTS[two.name]);
}

/**
 * The bubble redrawn as a reconciliation rather than as a repaint.
 *
 * An icon that is simply written into the row appears and disappears between two frames, which
 * on a bar full of things that move reads as a glitch rather than as news. So a slot that is
 * new is put in closed and let go on the next frame — it grows from nothing while rising into
 * place, and the ones beside it are pushed along by its width rather than being repositioned —
 * and a slot that is gone is closed and dropped before it is taken out of the DOM.
 *
 * The bubble answers a departure with a bounce inwards: something left it, so it is briefly
 * smaller than the room it now needs. Nothing answers an arrival, because the arrival is
 * already the whole of the motion — the glyph is the cause and the width is the consequence,
 * and a bounce on top of that is the bubble reacting to itself.
 */
function paintStatus() {
  if (!attached) return;
  const modus = MODUS_ORDER.find(kind => hasModus(kind));
  statusPill.classList.toggle('modal', Boolean(modus));
  root.style.setProperty('--status-accent', modus ? MODUS_COLOUR[modus] : 'transparent');
  statusPill.classList.toggle('plugged', isPlugged);
  paintBattery();
  paintModeLabels();
  paintQuickMedia();

  const shown = wanted();
  const names = shown.map(slot => slot.name);
  let hasLeft = false;
  for (const slot of [...statusClosed.children]) {
    if (names.includes(slot.dataset.slot) || slot.classList.contains('leaving')) continue;
    hasLeft = true;
    slot.classList.add('leaving');
    setTimeout(() => slot.remove(), SLOT_MOVE);
  }

  shown.forEach(want => {
    let slot = statusClosed.querySelector(`[data-slot="${want.name}"]:not(.leaving)`);
    if (!slot) {
      slot = document.createElement('div');
      slot.className = 'status-slot arriving';
      slot.dataset.slot = want.name;
      slot.appendChild(document.createElement('div')).className = 'status-slot-inner';
      // In slot order rather than appended: a reading that arrives between two that are
      // already standing there belongs where its number says, not at the end.
      const after = [...statusClosed.children].find(
        other => SLOTS[other.dataset.slot] > SLOTS[want.name]
      );
      statusClosed.insertBefore(slot, after || null);
      // Read a layout property back before letting go, which is what forces the closed state to be *computed* rather than merely written — a rAF is not enough here and never was: its callback runs before the frame's style recalc, so the class going on and coming off collapse into one computation and the browser has nothing to animate from, which is why slots appeared on the spot instead of growing.
      void slot.offsetWidth;
      slot.classList.remove('arriving');
    }
    const inner = slot.firstElementChild;
    const drawn = want.text ? want.text : want.html;
    if (inner.dataset.drawn !== drawn) {
      inner.dataset.drawn = drawn;
      if (want.text) inner.textContent = want.text;
      else inner.innerHTML = want.html;
    }
    inner.classList.toggle('is-text', Boolean(want.text) || Boolean(want.isText));
    if (want.level >= 0) inner.dataset.level = want.level;
    else delete inner.dataset.level;
  });

  if (hasLeft && isBorn) {
    statusPill.classList.add('settling');
    setTimeout(() => statusPill.classList.remove('settling'), SLOT_BOUNCE);
  }
  fitStatusProxy();
  stirLiquid(SLOT_MOVE + 200);
}

/* ── The charge announcement ───────────────────────────────────────────────────────────── */

/**
 * Being plugged in is announced *here*, by the bubble that already carries the charge, and it
 * is a takeover rather than a second shape: for a few seconds the status bar stops reporting
 * and says one thing instead.
 *
 * It used to be the Double's, at the bottom of the screen, and that was a bubble opening at the
 * far end of the phone to say something about a reading standing at the top of it — the news and
 * the thing it is news about were nowhere near each other. The Double keeps what has no home on
 * the bar: a battery running out, a recording that ended.
 *
 * The order is the rule and it is [motion.md]'s glyph-as-cause read at bubble scale. **What is
 * there leaves before what is arriving comes**: each reading shakes where it stands and is then
 * pulled inwards — one after another, left to right, so it reads as the row being cleared rather
 * than as everything blinking off together — and only once the bubble is empty does it open out
 * around the bolt. The width is the consequence of the icon, as always.
 */
const CHARGE_POP_STAGGER = 34;
const CHARGE_POP_SHAKE = 150;
const CHARGE_POP_COLLAPSE = 190;

/** Coming back is the opposite mood: each reading is thrown back in turned a little off true and swings straight, which is a shape landing rather than a shape being shown. The angle is random per slot so a row of them does not read as one rehearsed move. */
const CHARGE_POP_IN_STAGGER = 42;
const CHARGE_POP_IN = 460;
const CHARGE_POP_IN_TILT = 14;

/** How long the bolt stands there before the readings come back. */
const CHARGE_DWELL = 2600;

/** What the bubble opens out to while it is holding the bolt. Mirrors --status-charge-width. */
const CHARGE_WIDTH = 86;
root.style.setProperty('--status-charge-width', CHARGE_WIDTH + 'px');

let chargeDwell = null;
let isAnnouncing = false;
/** Which of the three is currently standing, so a second one arriving under it can be seen to be a different one. */
let announcingState = null;

export function announceCharge(state = 'charging') {
  // Not over an open panel: the panel owns the faces and the width while it is up, and news
  // arriving under the thing the finger is working is news drawn where nobody is looking.
  if (!isBorn || statusOpen) return;
  clearTimeout(chargeDwell);
  // Written even while one is already standing, since a charge falling from low to critical under
  // its own announcement is the one thing that reading is there to say.
  root.style.setProperty('--status-announce', ANNOUNCE_COLOURS[state] || ANNOUNCE_COLOURS.charging);
  // Already standing: the dwell is pushed out rather than the whole run played again, exactly as
  // an alert arriving on top of an alert does not announce itself twice.
  if (!isAnnouncing) {
    isAnnouncing = true;
    announcingState = state;
    const slots = [...statusClosed.children];
    slots.forEach((slot, index) => {
      slot.style.setProperty('--pop-at', index * CHARGE_POP_STAGGER + 'ms');
      slot.classList.add('popping');
    });
    const emptyAt = slots.length * CHARGE_POP_STAGGER + CHARGE_POP_SHAKE;
    setTimeout(() => {
      if (!isAnnouncing) return;
      // `announcingState` rather than this run's `state`: a second announcement can land while the
      // row is still emptying, and the glyph owed is the latest one and not the one that started it.
      statusFaces.charge.innerHTML =
        ANNOUNCE_GLYPHS[announcingState] || ANNOUNCE_GLYPHS.charging;
      statusPill.classList.add('announcing');
      showStatusFace('charge');
      bridge.triggerHaptic('notification');
      fitStatusProxy();
      stirLiquid(CHARGE_POP_COLLAPSE + 400);
    }, emptyAt);
    stirLiquid(emptyAt + CHARGE_POP_COLLAPSE + 400);
    chargeDwell = setTimeout(retireCharge, emptyAt + CHARGE_DWELL);
    return;
  }
  // The colour was being written and the glyph was not, so a charge falling past two marks in one
  // dwell — which is every fall now that the marks are 30 and 10 and only twenty percent apart —
  // stood there wearing the low battery's bar while the bubble had already gone red underneath it.
  // The glyph is the reading, so it is swapped, and replacing the node is also what re-runs
  // charge-land: the new shape rises into place the way the first one did, which is the icon being
  // the cause of the change rather than a picture quietly edited under a colour.
  if (state !== announcingState) {
    announcingState = state;
    statusFaces.charge.innerHTML = ANNOUNCE_GLYPHS[state] || ANNOUNCE_GLYPHS.charging;
    bridge.triggerHaptic('notification');
  }
  chargeDwell = setTimeout(retireCharge, CHARGE_DWELL);
}

/**
 * The readings come back the way any of them arrives: the slots are taken out of the DOM and
 * `paintStatus` builds them again, so each one grows from nothing through the same `arriving`
 * state a wifi icon uses. Leaving the popped slots standing and merely un-popping them would
 * put the whole row back in one frame, which is the one thing this bubble never does.
 */
function retireCharge() {
  if (!isAnnouncing) return;
  isAnnouncing = false;
  announcingState = null;
  statusPill.classList.remove('announcing');
  showStatusFace('closed');
  statusClosed.replaceChildren();
  paintStatus();
  // The slots grow their own width back through `arriving`; what is written here is the mood on
  // top of it, on the inner element so the two compose instead of one owning the other.
  [...statusClosed.children].forEach((slot, index) => {
    const tilt = (Math.random() * 0.6 + 0.4) * CHARGE_POP_IN_TILT * (Math.random() < 0.5 ? -1 : 1);
    slot.style.setProperty('--pop-at', index * CHARGE_POP_IN_STAGGER + 'ms');
    slot.style.setProperty('--pop-tilt', tilt + 'deg');
    slot.classList.add('popping-in');
    setTimeout(() => slot.classList.remove('popping-in'), index * CHARGE_POP_IN_STAGGER + CHARGE_POP_IN);
  });
  stirLiquid(statusClosed.children.length * CHARGE_POP_IN_STAGGER + CHARGE_POP_IN + 200);
}

/** The proxy is exactly the bubble — the panel's box when it is open, its own when it is not. */
export function fitStatusProxy() {
  if (!isBorn) {
    bridge.setStatusProxy(0, 0, 0);
    return;
  }
  // Open, the panel's own numbers rather than the measured box. The box is measured the frame
  // the growth *starts*, so it still reads as the closed bubble — which left the proxy the size
  // of the bar while the panel stood four times as tall underneath it, and every switch below
  // the first row was drawn perfectly and could not be pressed. It is also the rule about
  // asking for room before the animation rather than during it.
  if (statusOpen) {
    bridge.setStatusProxy(
      // Plus the pad the panel stands lower by: the host hangs this window at the top of the screen, so the room the drop costs comes out of the height or the last rows are drawn where nothing can be touched.
      STATUS_PANEL.width, STATUS_PANEL.height + GROWN_PAD, Math.round(statusPanelLeft())
    );
    return;
  }
  // On its way home, and therefore nobody's to touch. `getBoundingClientRect()` reads the box as it
  // stands *this frame*, and the frame the collapse starts on is still the panel's full size — width
  // and height are both transitioned — so "the proxy comes down with the panel" below was measuring
  // the panel and handing the host a window most of the bar wide for the whole 420ms of the close.
  // That is the window that ate the shade swipe, and a tap or a pull landing in it went to
  // `statusTouch` with `statusOpen` already false, which is `openStatusPanel()` — the panel snapping
  // back open out of a gesture that was asking for anything but. Nothing is owed a touch while it is
  // travelling: the room is given straight back the moment the ask ends, and taken again at the
  // settle. Measuring after the transition instead would be the same window held for the same 420ms.
  if (statusPill.classList.contains('panel-closing')) {
    bridge.setStatusProxy(0, 0, 0);
    return;
  }
  const box = statusPill.getBoundingClientRect();
  if (!box.width) {
    bridge.setStatusProxy(0, 0, 0);
    return;
  }
  bridge.setStatusProxy(Math.round(box.width), Math.round(box.height), Math.round(box.left));
}

/**
 * Born at the punch hole like everything else here, and only once: the bubble does not come
 * and go with the connection, it is the connection's place on the bar. What travels is the
 * bubble itself, from the middle of the screen out to its spot, small and growing on the
 * way — a bubble that appears at its own position is a box being shown.
 */
function bornStatus() {
  if (isBorn) return;
  isBorn = true;
  const box = statusPill.getBoundingClientRect();
  const home = screenWidth() / 2 - (box.left + box.width / 2);
  root.style.setProperty('--status-fly', Math.round(home) + 'px');
  root.style.setProperty('--status-pop', 0.5);
  requestAnimationFrame(() => {
    statusPill.classList.add('lit');
    root.style.setProperty('--status-fly', '0px');
    root.style.setProperty('--status-pop', 1);
  });
  setTimeout(() => {
    bridge.triggerHaptic('tap');
    fitStatusProxy();
  }, STATUS_TRAVEL * STATUS_LAND);
  stirLiquid(STATUS_TRAVEL + 300);
}

/** Whether a point on the bar belongs to this bubble, asked the same way the light is. */
export function statusHolds(x, y) {
  if (!isBorn) return false;
  // The other half of the collapse. Zeroing this bubble's own proxy stops it hearing anything
  // directly, but the row's window covers the bar too and `bridge.js` asks *this* whether a touch
  // it heard belongs here — off a rect that is still the open panel's for the length of the close.
  // So a swipe on the row near the top of the screen was claimed by a bubble that is not there any
  // more and read as a tap, which reopens the panel. A bubble on its way to idle holds nothing.
  if (statusPill.classList.contains('panel-closing')) return false;
  const box = statusPill.getBoundingClientRect();
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
}

/* ── The panel ─────────────────────────────────────────────────────────────────────────── */

export let statusOpen = false;

function showStatusFace(name) {
  Object.entries(statusFaces).forEach(([key, face]) => {
    face.classList.toggle('showing', key === name);
  });
}

/** Where the open panel's left edge belongs: the middle of the screen, and nothing else. */
function statusPanelLeft() {
  return (screenWidth() - STATUS_PANEL.width) / 2;
}

/**
 * The modules, which are bubbles rather than groups inside a box. The frame they stand in is
 * never seen and never touched — see the comment over #quick-panel in pill.html — so what this
 * list is for is the two things only the script can decide: what order they arrive in, and where
 * each of them is aiming when it goes home.
 */
const quickPanel = document.getElementById('quick-panel');
const quickScrim = document.getElementById('quick-scrim');
// The charge is deliberately not in this list. It was, on the argument that a set where fifteen things are thrown and one is placed reads as one thing being placed — but it is not one of the fifteen: it is the Status bubble's own face, and the bubble opening out into it is already its arrival. In the list it took a rank, a tilt and a throw on top of that growth, so the one shape with a real animation behind it was also the one flying in from somewhere, which is what the charge sliding in from the side of an expanding bubble was. What is placed here is nothing; what grows is the bubble.
const quickBubbles = [...quickPanel.querySelectorAll('.quick-bubble')];

/**
 * Five milliseconds between one module arriving and the next. Mirrors the `* 5ms` the arrival
 * and the sling in pill.css are both written against — the rank is a number here and a multiplier
 * there, so the two have to agree on what a rank is worth. It was sixty, which put the last of six
 * modules three hundred milliseconds behind the first before its own pop had even started; the
 * whole open is four hundred and fifty and a stagger cannot be two thirds of it.
 *
 * Eighteen until the groups were split, then eleven. A stagger is multiplied by however many things
 * there are, and fifteen of them at eleven is a hundred and sixty-five milliseconds of queue behind a
 * hundred and fifty of pop — the tail was longer than the animation it was a tail of, which is what
 * "too slow" actually was here. Five puts the last module seventy-five behind the first and the whole
 * set on screen inside a quarter second. motion.md's rule holds: the term to cut in a slow sequence
 * is the stagger, not the thing being staggered.
 */
const QUICK_STAGGER = 5;

/** The same on the way out, where it is tighter. Mirrors the `* 4ms` in `#quick-panel.leaving .quick-bubble`. */
const QUICK_DROP_STAGGER = 4;

/** How long one module takes to grow, which is the clock the Extended states take (`--grow-ms: 460ms`) because the arrival *is* the bubble's own growth written for a smaller box. Mirrors `--quick-pop` in pill.css, and it is named on this side because the liquid mirror has to be stirred for exactly as long as the last module is still moving — a growth that outlives the stir is a growth whose glass stops being sent part-way through it. */
const QUICK_POP = 460;

/**
 * How long the frost behind the panel takes to reach nothing. Mirrors the `--scrim-frost` transition
 * on `#quick-scrim` in pill.css, and it is here for one reason: the ramp is only *sent* to the host
 * while the liquid mirror is running, so the script has to know how long to keep the frames coming.
 */
const SCRIM_FROST_MS = 450;

/**
 * A fresh order every time the panel is asked for. A fixed one is a sequence, and a sequence that
 * can be learnt is read as a loading bar rather than as fifteen things landing at once-ish. The rank
 * is the only thing written per module now: a module grows where it stands, so there is no start
 * position to measure and no tilt to correct — both of those belonged to a flight the arrival no
 * longer is. See `bubble-grow` in pill.css.
 */
function shuffleQuickRanks() {
  const ranks = quickBubbles.map((bubble, index) => index);
  for (let at = ranks.length - 1; at > 0; at -= 1) {
    const swap = Math.floor(Math.random() * (at + 1));
    [ranks[at], ranks[swap]] = [ranks[swap], ranks[at]];
  }
  quickBubbles.forEach((bubble, index) => bubble.style.setProperty('--pop-rank', ranks[index]));
}

/** The tidy-up at the end of a collapse, held so a panel asked for again mid-close can call it off. */
let panelSettle = null;
/** The same, for the modules: they land later than the bubble does, so putting them away is its own clock. */
let quickSettle = null;

export function openStatusPanel() {
  if (statusOpen) return;
  // Opened again before the last close had finished. The settle was going to take `--liquid-tall`
  // back off in a few hundred milliseconds — the room this function is about to ask for — and
  // `panel-closing` is what makes the bubble hold and hear nothing, so a panel opening under it
  // would stand there deaf until a stale timeout happened to free it.
  clearTimeout(panelSettle);
  clearTimeout(quickSettle);
  statusPill.classList.remove('panel-closing');
  // A finger outranks an announcement: the panel takes the bubble's faces and its width, so the
  // charge is put away rather than left holding a class the panel is about to disagree with.
  clearTimeout(chargeDwell);
  retireCharge();
  // One bubble is extended at a time: two open panels are two answers to the same screen, and
  // the one that was already standing there is the one nobody is looking at any more. The rule
  // belongs to whichever bubble is arriving, which is why it is here and not in a watcher.
  if (shared.state === 'extended') toClosed();
  if (nowOpen) closeNowPanel();
  // The screen is the box, so it is measured on the way open rather than at load: at load this
  // bubble has not been born yet and there is nothing to measure the top off.
  fitStatusPanel();
  statusOpen = true;
  // The panel opens on what it was last told and corrects itself when the shell answers: a
  // switch that is a frame behind is better than a panel that waits for a shell to open.
  bridge.requestToggles();
  // The filter's region is the layer's own box, and this panel is several times the height of
  // the bar it grew out of: the room has to be there before it opens, or the skin is cut off
  // at the region's edge while the bubble goes on painting its own background underneath.
  root.style.setProperty('--liquid-tall', (STATUS_PANEL.height + GROWN_PAD + 24) + 'px');
  // Before the growth, not with it: the window is resized a frame or two after being asked, and
  // a proxy that arrives late is a panel that is briefly untouchable.
  //
  // Except under a live finger. Moving a window out from under a touch makes the system cancel
  // the gesture — the same trap the hold already knows about — and the pull opens this panel
  // *mid-drag*, so doing it here killed the very gesture that asked for it. It is done on the
  // release instead, which is late by one finger-lift and correct.
  if (!statusDownAt) fitStatusProxy();
  // The anchor changes from the screen's right edge to its middle, and a changed anchor is a
  // relayout, which no transition eases. So it is allowed to jump and then put back: the flight
  // holds the bubble at the pixel it was already standing on for one frame, and letting go of
  // that on the next frame is what the eye reads as the move.
  const was = statusPill.getBoundingClientRect().left;
  root.style.setProperty('--status-fly-ms', '0ms');
  statusPill.classList.add('open');
  // **The bubble's own new left, measured after the class lands** — not `statusPanelLeft()`, which is
  // where the *frame* of modules begins. The two are different boxes: the frame starts at the middle
  // of the screen and the charge is right-anchored ten pixels off the edge, so the offset this was
  // handed was a hundred and fifty pixels of a journey the bubble was never making. It jumped left,
  // then flew back right across most of the screen to a place it had already been standing next to.
  // The close has always measured it this way; the open is what disagreed.
  root.style.setProperty('--status-fly', Math.round(was - statusPill.getBoundingClientRect().left) + 'px');
  // Read back, so the browser has actually computed the frame we are about to move away from.
  void statusPill.offsetWidth;
  // `bubble-collapse` is filled `both`, so a panel asked for again inside the close's own quarter second finds every module still holding the last frame of it, shrunk to a fifth and invisible. The class comes off here rather than being left to the settle timer that this open has already cancelled.
  quickPanel.classList.remove('leaving');
  // A fresh order for the modules, and it is chosen before the frame they start on rather than
  // during it: the delay is a custom property the animation reads once when it begins.
  shuffleQuickRanks();
  requestAnimationFrame(() => {
    root.style.setProperty('--status-fly-ms', STATUS_PANEL.ms + 'ms');
    root.style.setProperty('--status-fly', '0px');
    showStatusFace('panel');
    quickPanel.classList.add('showing');
  });
  // Long enough to cover the last module landing as well as the charge growing: the modules are
  // bubbles in the same body of liquid, so the skin has to go on being mirrored until the slowest
  // of them has stopped moving. Fifteen ranks of stagger plus the growth itself, on top of the
  // bubble's own. It covers the frost's own ramp several times over, which is why that is not a
  // term here.
  stirLiquid(STATUS_PANEL.ms + quickBubbles.length * QUICK_STAGGER + QUICK_POP);
}

export function closeStatusPanel() {
  if (!statusOpen) return;
  statusOpen = false;
  // The clip has to outlive `open`, which is the class whose removal starts the collapse: the panel
  // inside is its own fixed size rather than the bubble's, so without this it stands at full width
  // over a bubble that is already most of the way home. Taken off with the rest of the tidying up
  // at the end of the collapse, below.
  statusPill.classList.add('panel-closing');
  showStatusFace('closed');
  // The tilt each module wears is left exactly as it arrived rather than shuffled again: a set that
  // came in leaning one way and went out leaning another is two different sets of things.
  quickPanel.classList.remove('showing');
  quickPanel.classList.add('leaving');
  // The same jump, put back the same way: taking `open` off moves the anchor back to the screen's
  // right edge, so the flight holds it where the panel was standing for one frame first.
  const was = statusPill.getBoundingClientRect().left;
  root.style.setProperty('--status-fly-ms', '0ms');
  statusPill.classList.remove('open');
  root.style.setProperty('--status-fly', Math.round(was - statusPill.getBoundingClientRect().left) + 'px');
  void statusPill.offsetWidth;
  requestAnimationFrame(() => {
    root.style.setProperty('--status-fly-ms', STATUS_PANEL.ms + 'ms');
    root.style.setProperty('--status-fly', '0px');
  });
  // The proxy comes off with the panel rather than after it: `panel-closing` is already on, so this
  // hands the room straight back and the collapse happens under a bubble that answers to nothing.
  fitStatusProxy();
  // Mirrors `bubble-collapse` in pill.css: the last module starts at its rank and takes 180ms to go.
  const dropped = quickBubbles.length * QUICK_DROP_STAGGER + 180;
  // The frost is still the longest thing on the screen — it takes 450ms to reach nothing and
  // the mirror is what sends the host each step of that, so a stir that stopped with the modules
  // would leave the blur standing at whatever strength the last frame it sent happened to catch.
  // Mirrors the `--scrim-frost` ramp in pill.css.
  stirLiquid(Math.max(STATUS_PANEL.ms, dropped, SCRIM_FROST_MS) + 160);
  panelSettle = setTimeout(() => {
    // The class first and the fit second, and never the other way round: `panel-closing` is what
    // makes `fitStatusProxy` answer nothing, so fitting under it would hand back zero and leave the
    // settled bubble untouchable until the next reading happened to arrive.
    statusPill.classList.remove('panel-closing');
    fitStatusProxy();
    // Back to the bar's own height once the panel has finished coming in, and not before: a
    // filter region that shrinks mid-collapse clips what is still moving.
    root.style.removeProperty('--liquid-tall');
  }, Math.max(STATUS_PANEL.ms, dropped) + 40);
  // The modules are put away on their own clock, and it is the later of the two: `leaving` is what
  // holds them visible through the sling, so taking it off with the bubble would be six of them
  // blinking out mid-flight. The bubble's own tidy-up may not wait for them either — that is the
  // proxy coming back, and a Status bubble that cannot be touched for three quarters of a second
  // after a close is a bubble that ignores the tap asking for the panel again.
  quickSettle = setTimeout(() => quickPanel.classList.remove('leaving'), dropped + 40);
}

/**
 * A tap on the open panel closes it, exactly as a tap on an open bubble does. The panel is
 * reached the ordinary way once it is open — the switches in it are clicked — so this is a
 * listener rather than a branch in statusTouch, and each switch stops the click before it
 * gets here.
 */
statusPill.addEventListener('click', () => {
  if (!statusOpen) return;
  bridge.triggerHaptic('tap');
  closeStatusPanel();
});

/* The panel's own background does **not** close it, and that is the deliberate half of the pair
   above. A tap on an open *bubble* closes it because the bubble is the thing that was opened; the
   scrim is not the panel, it is the room the panel is standing in, and this panel fills the screen
   to every edge — so most of what a thumb lands on while reaching the bottom row is background, and
   a background that dismisses turns every missed press into a panel that vanished for no reason the
   finger can account for. The scrim keeps its `pointer-events` regardless: it is what the flick that
   puts the panel away has to start on, since that gesture may now begin nowhere else. */

/* ── The switches ──────────────────────────────────────────────────────────────────────── */

const quickModes = [...document.querySelectorAll('.quick-mode')];
const quickKnobs = [...document.querySelectorAll('.quick-knob')];

/** Where a control's glyph is written, which is the button itself for a link and the span inside it for a knob. */
const faceOf = control => control.querySelector('.quick-icon') || control;

[...quickModes, ...quickKnobs].forEach(control => {
  faceOf(control).innerHTML = glyphFor(control.dataset.toggle, false);
});

/**
 * Every control drawn from one reading. None of them are switches any more: a link is up or it
 * is down and a state is on or it is off, and neither has a middle for a knob to travel across.
 * What says they are on is that they are lit and wearing their on face.
 */
function paintToggles(state) {
  toggles = state;
  quickModes.forEach(button => {
    const name = button.dataset.toggle;
    setControl(button, Boolean(state[name]), false);
    // The same lie the knobs are protected from: with no shell there is no reading behind a
    // connector either, and a button sitting at "off" for that reason is one the user can act on.
    // USB and the hotspot are left out because neither is judged on a setting — see paintModeLabels.
    if (name !== 'usb' && name !== 'hotspot') button.classList.toggle('unavailable', !(name in state));
  });
  paintModeLabels();
  paintLevels(state);
  quickKnobs.forEach(knob => {
    const name = knob.dataset.toggle;
    const value = name === 'recording' ? isRecordingLive() : state[name];
    setControl(knob, Boolean(value), false);
    // Nothing to say and nothing to do: with no shell there is no reading behind these, and a
    // control sitting at "off" for that reason is a lie the user can act on.
    knob.classList.toggle('unavailable', !(name in state) && name !== 'recording');
  });
  // Saving is a state of the battery as much as it is a switch, and the panel says it in both places off the one reading.
  batteryBox.classList.toggle('saving', Boolean(state.saver));
}

/**
 * The last reading the shell gave for the switches, kept because the labels under the three links
 * are written from two sources at once — the shell says whether a radio is on, the connectivity
 * payload says what it found — and the two arrive on their own clocks. Without it, whichever
 * landed second wiped the half the other one knew.
 */
let toggles = {};

/**
 * What each link is attached to, under its name. A colour says a radio is on and nothing else,
 * and "on" is the half of a link nobody has to be told — the network, the pair of headphones, the
 * cable are the reading.
 *
 * Nothing here writes "off". A cell that is dark, unlit and carrying nothing under its name has
 * already said it, and the one line a cell has is worth more spent on the half that is news: the
 * blank is the reading rather than a label that failed to load, because the name above it is now
 * always there to be the thing that did load. Every branch that used to end in the word ends in
 * an empty string, and only what is genuinely attached — or genuinely doing something, like a
 * hotspot nobody has joined — is written at all.
 */
function paintModeLabels() {
  const paired = attached && attached.bluetooth;
  const text = {
    // A cable is not a switch: it is either in the phone or it is not, and what the switch under
    // it does is decide what the cable is allowed to carry.
    usb: !(attached && attached.usb) ? 'no cable' : toggles.usb ? 'file transfer' : 'charging only',
    bluetooth: paired ? (paired.name || 'connected') : toggles.bluetooth ? 'nothing paired' : '',
    wifi: attached && attached.link === 'wifi' && attached.ssid
      ? attached.ssid
      : toggles.wifi ? 'not connected' : '',
    // The generation is the name of a mobile link the way an SSID is the name of a wifi one — it is
    // what the phone will say about the network it is on, and it is already on the bar.
    mobile: !toggles.mobile ? ''
      : attached && attached.link === 'mobile' ? (attached.generation || 'connected')
      : 'standby',
    // Whether anything is actually using the phone as its way out, which is the half of a hotspot
    // that matters: a hotspot nobody joined is a radio burning for nothing.
    hotspot: attached && attached.hotspot ? 'sharing' : toggles.hotspot ? 'nobody joined' : '',
    // The mode, and it says what it *does* rather than repeating its own name back: every radio is
    // down is the reading, and "aeroplane mode" under an aeroplane is the label said twice.
    plane: toggles.plane ? 'radios off' : '',
    // Nothing to write in either direction: lit is on and dark is off, and with the name standing
    // over the cell the word "on" under it is the cell saying its own state in place of a reading.
    gps: '',
  };
  quickModes.forEach(button => {
    const name = button.dataset.toggle;
    // The cable has no label to write — it is the one connector with no name to give, so it stands
    // in the narrow column wearing its glyph alone. Every other button here has one.
    const label = button.querySelector('.quick-label');
    if (label) label.textContent = text[name] || '';
    // The USB switch answers to a cable rather than to a setting: with nothing plugged in there is
    // nothing for it to grant. It is no longer dimmed for that — it stands at the foot of the media
    // module now, where a permanently greyed row would be height spent on the absence of a thing —
    // so it is drawn or it is not, and the module gives its height back either way.
    if (name === 'usb') {
      const cabled = Boolean(attached && attached.usb);
      button.classList.toggle('attached', cabled);
      // Only when it actually changes: this runs on both clocks and on every reading, and a stir per
      // reading is the mirror never being allowed to stop on a panel that is standing still.
      if (cabled !== quickMedia.classList.contains('cabled')) {
        quickMedia.classList.toggle('cabled', cabled);
        stirLiquid(QUICK_MEDIA_GROW + 120);
      }
    }
  });
  // The hotspot's truth is not a setting. Samsung keeps no key for it, and the tether broadcast
  // ConnectivityWatch is already listening to is what says whether the phone is lending its link
  // out — so this one switch is lit from the connectivity payload rather than from the shell's
  // reading, and it is painted here rather than in paintToggles because this function runs on both
  // clocks and paintToggles only ever runs on the shell's.
  const hotspot = quickModes.find(button => button.dataset.toggle === 'hotspot');
  if (hotspot && attached) setControl(hotspot, Boolean(attached.hotspot), false);
}

/** Whether the Now bubble is currently carrying a recording, which is where that truth lives. */
function isRecordingLive() {
  // Read off the bubble rather than imported: the clock bubble already wears the class while a
  // recording is its mod, and that class is the same truth the switch wants.
  return document.getElementById('clock').classList.contains('recording');
}

/* ── The battery ───────────────────────────────────────────────────────────────────────── */

const batteryBox = document.getElementById('quick-battery');
const batteryGlyphBox = document.getElementById('battery-glyph');
const batteryReading = document.getElementById('battery-reading');

/**
 * The colour the level is drawn in, in five bands rather than as a gradient: a band is a reading
 * and a gradient is a number said twice, and what a glance wants off a battery is which of the
 * five situations it is in. The bands: 100-86, 85-61, 60-31, 30-16, 15-0.
 * The 31 is a mirror of `BatteryWatch.LOW`, which is 30: the announcement and the colour are one
 * reading, and they were half a band apart — the bubble went orange at 35 and only said so at 30,
 * which reads as the panel and the bar disagreeing about the same phone.
 */
function chargeColour() {
  // Plugged in outranks every band, the same way it outranks them on the bar: the level is no
  // longer the news, what is being done to it is — and it is the announcement's own green, so the
  // bubble saying "charging" and the panel showing it are one colour rather than two greens.
  if (isPlugged) return ANNOUNCE_COLOURS.charging;
  if (charge >= 86) return '#5bfd5b';
  if (charge >= 61) return '#2fbf2f';
  if (charge >= 31) return '#ffd429';
  if (charge >= 16) return '#ff9020';
  return '#e5342b';
}

/**
 * Which of the three the bar's own reading is in. Three and not the panel's five: the panel is
 * being looked at and can afford a scale, the bar is being glanced past and only has room for
 * the question "is this fine, is this getting on, is this a problem". The two boundaries are the
 * bottom two of chargeColour()'s, so a charge crossing 31 or 16 changes in both places at once.
 */
function chargeBand() {
  if (charge >= 31) return 'ok';
  if (charge >= 16) return 'low';
  return 'critical';
}

/** The charge, drawn where it can be read across the room rather than counted off a bar. */
function paintBattery() {
  // The bolt and nothing else: the cell around the level is the battery glyph now, so the four
  // steps of charge would be a drawing of the level standing inside the level itself.
  batteryGlyphBox.innerHTML = isPlugged ? BATTERY_GLYPHS.charging : '';
  batteryReading.textContent = charge >= 0 ? charge + '%' : '--';
  batteryBox.style.setProperty('--charge-height', Math.max(0, charge) + '%');
  batteryBox.style.setProperty('--charge-color', chargeColour());
  // Low is the battery's own alarm and belongs to the reading, not to a slot beside it. Not while it is plugged in: a phone at 9% on a cable is a phone being fixed.
  batteryBox.classList.toggle('low', !isPlugged && charge >= 0 && charge < 15);
}

/** The control behind the battery's tap: one truth, worked from two places in the same panel. */
const saverSwitch = () => document.querySelector('.quick-knob[data-toggle=saver]');

let batteryHeld = false;
let batteryHoldTimer = 0;

batteryBox.addEventListener('touchstart', () => {
  batteryHeld = false;
  batteryBox.classList.add('pressing');
  // The same meaning a hold has on every bubble here: out to the app behind what is being shown. Behind a charge that is Samsung's battery screen, which is where the charging limit lives — the panel is not going to grow a second copy of it.
  batteryHoldTimer = setTimeout(() => {
    batteryHeld = true;
    batteryBox.classList.remove('pressing');
    bridge.triggerHaptic('expand');
    bridge.openConnectionSettings('battery');
    closeStatusPanel();
  }, HOLD_MILLIS);
}, { passive: true });

['touchend', 'touchcancel'].forEach(type => {
  batteryBox.addEventListener(type, () => {
    clearTimeout(batteryHoldTimer);
    batteryBox.classList.remove('pressing');
  }, { passive: true });
});

batteryBox.addEventListener('click', event => {
  event.stopPropagation();
  // The hold has already acted, and the click that follows the lift is not the gesture asking twice.
  if (batteryHeld) return;
  pressToggle('saver', saverSwitch());
  batteryBox.classList.toggle('saving', saverSwitch().classList.contains('on'));
});

window.onTogglesChanged = paintToggles;

/* ── The levels ────────────────────────────────────────────────────────────────────────── */

/**
 * Brightness and volume, and they are the two readings in this panel that are neither a state nor
 * something the phone decided: they are amounts a hand sets. So they are drawn as amounts — an
 * upright column filled to its share, which is the same drawing the charge beside them is — and
 * they answer a *drag* and never a tap. A bar that jumps to wherever it was touched changes the
 * screen's brightness because a finger came down on its way to something else, and on a panel that
 * closes when the background is hit, that is a change nobody saw and nobody asked for.
 */
const LEVEL_GLYPHS = {
  brightness: '<svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="4" stroke="currentColor" stroke-width="2"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M19.1 4.9l-1.4 1.4M6.3 17.7l-1.4 1.4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
  volume: '<svg viewBox="0 0 24 24"><path d="M11 4.5 6.5 8.5H3.5v7h3l4.5 4zM15.4 8.6a4.8 4.8 0 0 1 0 6.8l1.4 1.4a6.8 6.8 0 0 0 0-9.6zM18.2 5.8a8.8 8.8 0 0 1 0 12.4l1.4 1.4a10.8 10.8 0 0 0 0-15.2z"/></svg>',
};

const levels = [...document.querySelectorAll('.level')];
levels.forEach(level => {
  level.querySelector('.level-icon').innerHTML = LEVEL_GLYPHS[level.dataset.level];
});

/** Below this a touch is a touch: the panel closes on its background and a bar is a big target. */
const LEVEL_SLOP = 8;

/** How coarse a step is worth sending. A finger crosses a 300px bar in a few dozen of these, and every one of them is a shell command — a percent per frame is a queue the hand outruns. */
const LEVEL_STEP = 2;

/**
 * One bar drawn at a share. Written as a percentage rather than a pixel height so the fill means
 * the same thing on any screen, and `lit` is the whole of what says a bar has something in it.
 */
function paintLevel(level, share) {
  level.style.setProperty('--level', share + '%');
  level.classList.toggle('lit', share > 0);
}

levels.forEach(level => {
  const name = level.dataset.level;
  let from = null;
  let share = 0;
  let sent = -1;

  level.addEventListener('touchstart', event => {
    event.stopPropagation();
    from = event.touches[0].clientY;
    share = parseFloat(level.style.getPropertyValue('--level')) || 0;
    sent = share;
  }, { passive: true });

  level.addEventListener('touchmove', event => {
    if (from === null) return;
    event.stopPropagation();
    const travelled = from - event.touches[0].clientY;
    if (!level.classList.contains('dragging')) {
      if (Math.abs(travelled) < LEVEL_SLOP) return;
      level.classList.add('dragging');
      bridge.triggerHaptic('tap');
    }
    // Off the bar's own height, so the whole of it is the whole of the range and the value is
    // under the thumb rather than a distance away from where it started.
    const next = Math.round(share + (travelled / level.getBoundingClientRect().height) * 100);
    const clamped = Math.max(0, Math.min(100, next));
    paintLevel(level, clamped);
    if (Math.abs(clamped - sent) < LEVEL_STEP && clamped !== 0 && clamped !== 100) return;
    sent = clamped;
    bridge.setLevel(name, clamped);
  }, { passive: true });

  ['touchend', 'touchcancel'].forEach(type => {
    level.addEventListener(type, () => {
      from = null;
      level.classList.remove('dragging');
    }, { passive: true });
  });

  // A tap on a bar is a tap on the panel's background everywhere else, and here it has to be
  // nothing at all: the panel closing under a finger that meant to set the volume is the one
  // outcome a drag-only control exists to prevent.
  level.addEventListener('click', event => event.stopPropagation());
});

/** Both bars from the same reading every other control here is painted from. */
function paintLevels(state) {
  levels.forEach(level => {
    const value = state[level.dataset.level];
    // A level the shell would not answer is left where it stands rather than dropped to the floor:
    // an empty bar is a reading, and "we could not ask" is not one.
    if (typeof value === 'number' && !level.classList.contains('dragging')) paintLevel(level, value);
  });
}

/**
 * Mirrors the 460ms turn on .quick-knob.turning in pill.css. The glyph is swapped at the half
 * turn — where it is upside down and least readable, so what the eye keeps is the icon it ends
 * on rather than a cut between two.
 */
const KNOB_TURN = 460;

/**
 * One control put at one end, whether that came from a finger or from the shell answering. The
 * turn is only for the finger: a correction arriving from `onTogglesChanged` a frame later must
 * not spin a button nobody touched, and neither must the paint that runs when the panel opens.
 */
function setControl(control, isOn, isTurning) {
  const wasOn = control.classList.contains('on');
  control.classList.toggle('on', isOn);
  if (!isTurning) {
    if (wasOn !== isOn) faceOf(control).innerHTML = glyphFor(control.dataset.toggle, isOn);
    return;
  }
  // Restarted rather than added: a second tap inside the first turn found the class already
  // there and got no animation at all, so the button sat still for the one press that asked
  // hardest for an answer.
  control.classList.remove('turning');
  void control.offsetWidth;
  control.classList.add('turning');
  setTimeout(() => {
    faceOf(control).innerHTML = glyphFor(control.dataset.toggle, control.classList.contains('on'));
  }, KNOB_TURN / 2);
}

function pressToggle(name, element) {
  const next = !element.classList.contains('on');
  setControl(element, next, true);
  // Each control answers under the finger rather than after the shell has: the buzz is the
  // acknowledgement and the correction comes back on its own from onTogglesChanged. Its own
  // kind of buzz, not the tap every bubble gets — a switch is thrown rather than pressed, and
  // this is the one place on the bar where the finger changes the phone rather than the view.
  bridge.triggerHaptic('toggle');
  bridge.setToggle(name, next);
}

[...quickModes, ...quickKnobs].forEach(control => {
  control.addEventListener('click', event => {
    event.stopPropagation();
    if (control.classList.contains('unavailable')) return;
    pressToggle(control.dataset.toggle, control);
  });
});

/* ── The gestures ──────────────────────────────────────────────────────────────────────── */

let statusHeld = false;
let statusDownAt = null;
let statusHoldTimer = 0;
let statusPulled = false;
/** The furthest the finger got downwards, kept so a confiscated gesture can still be read. */
let statusDrop = 0;

/**
 * The same three answers every bubble here gives, in the same order they mean things elsewhere
 * on the bar. A tap is Active and opens the panel this bubble owns. A pull downwards is the
 * same ask made with the hand — it is what the shade underneath does, and the bubble standing
 * on the shade's own icons should answer it the same way. A hold is the way out to the app
 * behind it, which for this bubble is the system's own settings screen for whatever it is
 * currently reporting; a panel of ours that toggled the radios *and* named the networks would
 * be a second settings app rather than a status bubble.
 */
/**
 * The Push on the open panel: a flick upwards puts it away, which is the state every extended
 * bubble owes and the one this bubble looked like it had. It used to live inside statusTouch,
 * behind an `if (statusOpen)` — and statusTouch is only ever called while the panel is *closed*,
 * because an open panel is full of switches whose touches have to reach the elements that know
 * what to do with them. So the branch could never run and the flick did nothing at this end of
 * the bar while meaning dismiss at every other. It is its own function now and bridge.js calls
 * it alongside the ordinary routing rather than instead of it: a flick and a tap on a switch are
 * told apart by how far the finger went, which is a thing both readings can agree on without
 * either having to win the gesture outright.
 *
 * Latched, because a touchmove is a stream: unlatched, every frame the finger stayed above the
 * line fired the dismiss again.
 */
let panelPushFrom = null;
let panelPushed = false;

export function statusPanelPush(action, x, y) {
  if (action === 'down') {
    // **From the panel's own background and from nowhere else.** A gesture that starts on a control
    // belongs to that control, and this used to say so about the levels alone — the one place the
    // clash had actually been noticed, because a brightness drag upwards is a dismiss drawn in the
    // same stroke. But every module in this panel is grabbed, not only the two bars: a knob pressed
    // and held wanders, a link is pushed at, the charge is held to reach Samsung's own screen, and
    // all of them travel the twenty-four pixels this reads as a flick sooner or later. So every one
    // of them was a dismiss waiting to happen, and what it looked like from the outside is that
    // nothing in the panel could be grabbed at all — whatever the finger did, the panel went away.
    // The scrim is the only thing under a finger that means "not a control", so it is the only
    // thing the push may begin on.
    const on = document.elementFromPoint(x, y);
    panelPushFrom = on === quickScrim ? { x, y } : null;
    panelPushed = false;
    return;
  }
  if (action !== 'move' || !panelPushFrom || panelPushed) return;
  if (y - panelPushFrom.y < -24 && Math.abs(x - panelPushFrom.x) < 40) {
    panelPushed = true;
    panelPushFrom = null;
    bridge.triggerHaptic('dismiss');
    closeStatusPanel();
  }
}

export function statusTouch(action, x, y) {
  if (action === 'down') {
    statusHeld = false;
    statusPulled = false;
    statusDrop = 0;
    statusDownAt = { x, y };
    statusPill.classList.add('pressing');
    clearTimeout(statusHoldTimer);
    statusHoldTimer = setTimeout(() => {
      statusHeld = true;
      statusPill.classList.remove('pressing');
      bridge.triggerHaptic('expand');
      bridge.openConnectionSettings(subject());
    }, HOLD_MILLIS);
    return;
  }
  if (action === 'move') {
    if (!statusDownAt) return;
    if (statusPulled) return;
    const across = x - statusDownAt.x;
    const down = y - statusDownAt.y;
    statusDrop = Math.max(statusDrop, down);
    // The band first, so the bubble is an object being pulled rather than a control waiting for
    // a threshold. The pull runs on top of it and is measured from the finger, not from where
    // the bubble ended up.
    toy(statusPill, '--status-drag', across, down);
    if (rubberBandPast(across, down)) {
      clearTimeout(statusHoldTimer);
      statusPill.classList.remove('pressing');
    }
    if (down > STATUS_PULL && down > Math.abs(across)) {
      // Felt at the crossing rather than on the lift: the crossing is the moment there was
      // still a decision to make.
      statusPulled = true;
      clearTimeout(statusHoldTimer);
      statusPill.classList.remove('pressing');
      untoy(statusPill, '--status-drag');
      bridge.triggerHaptic('expand');
      openStatusPanel();
    }
    return;
  }
  clearTimeout(statusHoldTimer);
  statusPill.classList.remove('pressing');
  untoy(statusPill, '--status-drag');
  const travelled = statusDownAt ? Math.hypot(x - statusDownAt.x, y - statusDownAt.y) : Infinity;
  statusDownAt = null;
  // The finger is off, so the window may be moved now: a panel opened by the pull has been
  // standing there with the closed bubble's proxy over it since the crossing.
  if (statusOpen) fitStatusProxy();
  // Taken away rather than finished, with the finger already on its way down: the shade has the
  // pointer now and there will be no lift to wait for, so the ask is answered here.
  if (action === 'cancel') {
    if (!statusHeld && !statusPulled && !statusOpen && statusDrop > STATUS_PULL_STOLEN) {
      bridge.triggerHaptic('expand');
      openStatusPanel();
    }
    return;
  }
  if (action !== 'up' || statusHeld || statusPulled || travelled >= PROXY_TAP_SLOP) return;
  bridge.triggerHaptic('tap');
  if (statusOpen) closeStatusPanel();
  else openStatusPanel();
}

/** A finished transfer stands there wearing its tick for this long, then goes. Mirrors NOW_DONE_DWELL, which is what it was before the mod moved here. */
const TRANSFER_DONE_DWELL = 1100;

let transferTick = null;

/**
 * The file in flight, handed over by `now.js` — the host reads a recording and a transfer off the
 * same shade in one pass, so one payload arrives and the two halves of it are shown in two bubbles.
 *
 * A transfer that reaches its total is not dropped on the spot: it stands there wearing a tick for
 * a moment first, or the only thing ever seen of a finished download is an icon disappearing. Nor
 * is one that simply left the shade — apps take a progress notification away the instant it
 * completes, so anything that was nearly there when it went finished, and is owed the same tick.
 */
export function setTransfer(payload) {
  if (payload) {
    const isDone = payload.total > 0 && payload.done >= payload.total;
    transfer = Object.assign({ isDone }, payload);
    if (isDone) holdTheTick();
    paintStatus();
    return;
  }
  if (transfer && transfer.isDone) return;
  if (transfer && transfer.total && transfer.done / transfer.total >= 0.95) {
    transfer = Object.assign({}, transfer, { isDone: true });
    holdTheTick();
    paintStatus();
    return;
  }
  transfer = null;
  paintStatus();
}

/* ── The module at the foot ────────────────────────────────────────────────────────────── */

/** How long the foot module takes to change height. Mirrors the `transition` on `#quick-media` in pill.css; the mirror only paints while it is stirred, so a height that moves without one moves under glass that has stopped following it. */
const QUICK_MEDIA_GROW = 320;

const quickMedia = document.getElementById('quick-media');
const quickMediaKind = document.getElementById('quick-media-kind');
const quickMediaReading = document.getElementById('quick-media-reading');
const quickMediaArt = document.getElementById('quick-media-art');
const quickMediaTitle = document.getElementById('quick-media-title');
const quickMediaArtist = document.getElementById('quick-media-artist');
const quickMediaPlayPath = document.getElementById('quick-media-play-path');

/**
 * The foot module, wearing whichever of its two readings is true.
 *
 * Media is the base mod and a transfer takes it over, which is the way round the panel needed
 * rather than the way it was built: a file in flight is a thing that happens a few times a week and
 * a song is a thing that is playing most of the time the panel is opened at all, so the bubble that
 * used to say "No transfer" nine openings out of ten now says what is playing.
 *
 * A transfer outranks a song while it is running, for the same reason it outranks it on the closed
 * bubble: a song is a state and a transfer is a thing in progress, and what is in progress is what
 * a panel opened mid-download was opened to look at.
 *
 * The share is a share and never a size: `done` and `total` are the notification's own progress
 * ints, in whatever unit the app that posted them chose, so bytes written beside them would be a
 * number this page invented. A percentage is the one reading those two are.
 */
export function paintQuickMedia() {
  const running = Boolean(transfer);
  const song = !running && shared.media ? shared.media : null;
  quickMedia.classList.toggle('running', running);
  // The same debt the cable's row owes and for the same reason: a song starting takes this module
  // from 54 to 135 over 320ms, and the glass behind it only follows while the mirror is running.
  if (Boolean(song) !== quickMedia.classList.contains('sounding')) {
    quickMedia.classList.toggle('sounding', Boolean(song));
    stirLiquid(QUICK_MEDIA_GROW + 120);
  }
  if (song) {
    quickMediaTitle.textContent = song.title || '';
    quickMediaArtist.textContent = song.artist || '';
    // Absent rather than empty: an `<img>` with no source draws the browser's own broken glyph,
    // which is the one thing worse than a blank square where a cover belongs.
    if (song.artBase64) quickMediaArt.src = song.artBase64;
    else quickMediaArt.removeAttribute('src');
    quickMediaPlayPath.setAttribute('d', song.isPlaying ? 'M6 5h4v14H6zm8 0h4v14h-4z' : 'M8 5v14l11-7z');
    return;
  }
  if (!running) {
    quickMediaKind.textContent = 'Nothing playing';
    quickMediaReading.textContent = '';
    quickMedia.style.setProperty('--transfer-share', '0%');
    return;
  }
  quickMediaKind.textContent = transfer.mod === 'upload' ? 'Upload' : 'Download';
  // A finished one keeps its full bar for the moment it stands there wearing the tick, because the
  // bar going back to nothing is the one thing that would read as the transfer having failed.
  const share = transfer.isDone || !transfer.total
    ? 100
    : Math.round((transfer.done / transfer.total) * 100);
  quickMediaReading.textContent = transfer.isDone ? 'Done' : share + '%';
  quickMedia.style.setProperty('--transfer-share', share + '%');
}

/* The transport, straight through to the host. It is the same three commands the player face and
   the lock screen's bubble send — one name each, no state of its own — so nothing here has to know
   what is playing beyond what paintQuickMedia has already drawn. */
[
  ['quick-media-previous', () => 'previous'],
  // The two ends of one control, asked as one question, so the glyph and the request cannot
  // disagree. Nothing is painted here on the press: onMediaUpdate comes back and repaints, which is
  // the button saying what it did rather than what it asked for.
  ['quick-media-play', () => (shared.media && shared.media.isPlaying ? 'pause' : 'play')],
  ['quick-media-next', () => 'next'],
].forEach(([id, command]) => {
  document.getElementById(id).addEventListener('click', event => {
    event.stopPropagation();
    bridge.triggerHaptic('tap');
    bridge.mediaControl(command());
  });
});

function holdTheTick() {
  clearTimeout(transferTick);
  transferTick = setTimeout(() => {
    transfer = null;
    paintStatus();
  }, TRANSFER_DONE_DWELL);
}

window.onConnectivity = state => {
  attached = state;
  bornStatus();
  paintStatus();
};

/** The charge, which arrives on its own clock: the battery mod is an event, this is a level. */
window.onCharge = (level, plugged) => {
  charge = level;
  isPlugged = plugged;
  paintStatus();
};

// The bubble stands where the screen's right edge is, and the page is the side that knows
// what a resize did to that.
window.addEventListener('resize', () => {
  if (!isBorn) return;
  fitStatusPanel();
  fitStatusProxy();
});

root.style.setProperty('--status-right', STATUS_RIGHT + 'px');

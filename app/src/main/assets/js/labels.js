/**
 * The one place that decides whether a piece of text has run out of room.
 *
 * Every reading on this bar fades at the end it overflows — the DNA rule is fade, never an
 * ellipsis — and the fade used to be written straight into each label's CSS, unconditionally. So
 * a two-letter app name faded out as hard as a forty-letter one: the mask does not know how wide
 * the text is, only how wide the box is, and it was dissolving the last quarter of readings that
 * fitted perfectly well. That is what "text labels fade out everywhere" was. The fade is right,
 * it just has to be *conditional*, and only one side of the app can answer the condition, because
 * only the layout knows whether the glyphs actually exceeded the box.
 *
 * So a label is measured, and `.runs-out` is put on it when — and only when — it does. The CSS
 * hangs the mask off that class, and hangs a scroll off it too: a reading too long to be read is
 * not really shown by fading it, so after a beat it travels far enough to show the rest and comes
 * back. It travels by `text-indent` rather than by a transform, because the clip and the mask live
 * on the same box as the text — a transform would carry the window along with what is inside it
 * and nothing would appear to move at all.
 *
 * Which way it travels is read off the text's own anchor rather than configured per label: a
 * left-set reading overflows on its right and has to pull left to show the end, a right-set one
 * overflows on its left and pushes right. That is already the rule the masks follow, so there is
 * one fact here and not two that can disagree.
 */

/**
 * Every reading on the bar that is allowed to run out of room. A list rather than a marker class
 * on each element, because half of these are written by the host into markup that is otherwise
 * untouched, and a list in one file is easier to keep true than a class in ten.
 */
const LABELS = [
  '#app-name', '#title', '#call-name',
  '#player-title', '#player-artist',
  '#lock-title', '#lock-artist',
  '.lock-note-who', '.lock-note-said',
  '#timer-remaining', '#timer-label',
  '.quick-label',
].join(', ');

/** Below this the text is inside its box and the difference is a sub-pixel rounding, not a word. */
const RUNS_OUT_SLACK = 2;

/** How fast the reading travels while it is showing its far end, in pixels per second. Slow enough to be read at a glance from the top of the screen, which is the only place this is ever seen from. */
const SCROLL_SPEED = 34;

/** Mirrors the hold shares in the label-scroll keyframes in pill.css: the fraction of the cycle spent standing still at the near end, travelling, and standing still at the far end. The wait before the first travel is the animation's delay and is written there too. */
const SCROLL_HOLD = 0.18;
const SCROLL_TRAVEL = 0.37;

function measure(label) {
  const over = label.scrollWidth - label.clientWidth;
  if (over <= RUNS_OUT_SLACK) {
    label.classList.remove('runs-out');
    label.style.removeProperty('--label-shift');
    label.style.removeProperty('--label-scroll-ms');
    return;
  }
  // Right-set text hangs off its left edge, so it shows its far end by moving right, and the sign
  // of the shift is the whole difference between the two cases.
  const isRightSet = getComputedStyle(label).textAlign === 'right';
  label.style.setProperty('--label-shift', (isRightSet ? over : -over) + 'px');
  // One cycle is the two holds plus the two crossings, so the crossing itself keeps its speed
  // whatever the reading's length — a long name takes longer to travel, which is the point.
  const crossing = (over / SCROLL_SPEED) * 1000;
  label.style.setProperty('--label-scroll-ms', Math.round(crossing / SCROLL_TRAVEL) + 'ms');
  label.classList.add('runs-out');
}

let pending = 0;

/**
 * One observer for every label's own box, rather than one for the page: a reading runs out of room
 * because the bubble around it grew, and that growth is a transition on somebody else's element —
 * nothing about it is a mutation this file could see. Watching the label itself catches it whatever
 * caused it. `.runs-out` and the indent do not change the border box, so writing them from inside
 * the callback cannot feed itself.
 */
const boxes = new ResizeObserver(() => fitLabels());

/**
 * Re-measure everything, coalesced to one frame. Everything rather than only what changed: there
 * are a couple of dozen of these on the whole bar, `scrollWidth` on a box that is already laid out
 * is a read and not a relayout, and the alternative is a dependency graph between every label and
 * every animation that could have resized the box under it.
 */
export function fitLabels() {
  if (pending) return;
  pending = requestAnimationFrame(() => {
    pending = 0;
    for (const label of document.querySelectorAll(LABELS)) {
      // Re-observing one already watched is a no-op, so a note that has just been written into the
      // lock screen is picked up here without anyone having to announce it.
      boxes.observe(label);
      measure(label);
    }
  });
}

/**
 * Nothing has to call this. A reading changes because the host wrote new text into it, and a box
 * changes because something else on the bar grew — both are visible from here, and asking every
 * mod to remember to re-fit its own labels is the kind of thing that is right on the day it is
 * written and wrong two mods later.
 */
new MutationObserver(fitLabels).observe(document.body, {
  subtree: true,
  childList: true,
  characterData: true,
});
fitLabels();

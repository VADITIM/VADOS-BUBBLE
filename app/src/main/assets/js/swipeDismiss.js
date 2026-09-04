import { bridge } from './state.js';

/** How far a row has to travel before the release counts as "gone". */
export const SWIPE_AWAY = 90;

/** How much of a row's travel the rows beside it are dragged along by. */
const ROW_PULL = 0.18;

/**
 * A row dismissed sideways, the way the shade itself dismisses one, with its neighbours
 * leaning after it while it goes. Shared by the notification tab and the lock screen's own
 * list: both hold a scrollable column of swipeable rows and answer a horizontal drag the same
 * way, and the gesture math is the part worth sharing — what happens to the list once a row is
 * actually gone differs enough between the two (a panel that resizes itself against the rest
 * of the screen versus a list that only ever closes its own gap) that it stays the caller's.
 *
 * `rowSelector` is every row in the same list as this one, so a drag on one can lean its
 * neighbours regardless of which page is asking. `afterRemove(below, gapPixels)` runs once the
 * row has actually left the DOM, with every sibling that stood after it — read before the
 * removal, since after it they are all `rowSelector` has left — and the height the gone row
 * was taking up, which is exactly the distance those siblings are about to be short by.
 */
export function swipeToDismiss(row, keys, { rowSelector, afterRemove }) {
  let originX = 0;
  let originY = 0;
  let travel = 0;
  let isHorizontal = false;

  row.addEventListener('touchstart', event => {
    originX = event.touches[0].clientX;
    originY = event.touches[0].clientY;
    travel = 0;
    isHorizontal = false;
    row.dataset.swiped = 'false';
    row.dataset.scrolled = 'false';
    row.style.transition = 'none';
  }, { passive: true });

  row.addEventListener('touchmove', event => {
    const dx = event.touches[0].clientX - originX;
    const dy = event.touches[0].clientY - originY;
    // Scrolling the list is not choosing a row — see tabs.js's own copy of this reasoning,
    // which is the one this was lifted from.
    if (Math.abs(dy) > 8 && Math.abs(dy) > Math.abs(dx)) row.dataset.scrolled = 'true';
    if (!isHorizontal && Math.abs(dx) < 12) return;
    if (row.dataset.scrolled === 'true') return;
    isHorizontal = true;
    travel = dx;
    if (Math.abs(dx) > 12) row.dataset.swiped = 'true';
    row.style.transform = 'translateX(' + dx + 'px)';
    row.style.opacity = String(Math.max(0, 1 - Math.abs(dx) / (SWIPE_AWAY * 2)));
    dragNeighbours(row, dx, rowSelector);
  }, { passive: true });

  row.addEventListener('touchend', () => {
    row.style.transition = 'transform 200ms ease-out, opacity 200ms ease-out';
    if (Math.abs(travel) < SWIPE_AWAY) {
      row.style.transform = '';
      row.style.opacity = '';
      releaseNeighbours(rowSelector);
      return;
    }
    keys.forEach(one => bridge.dismissNotification(one));
    bridge.triggerHaptic('dismiss');
    row.style.transform = 'translateX(' + (travel > 0 ? 400 : -400) + 'px)';
    row.style.opacity = '0';
    releaseNeighbours(rowSelector);
    row.addEventListener('transitionend', () => {
      // Measured while it is still in the flow: once it is out, the height it was
      // taking up is exactly the distance everything under it is about to jump.
      const gap = row.offsetHeight;
      const rows = [...document.querySelectorAll(rowSelector)];
      const below = rows.slice(rows.indexOf(row) + 1);
      row.remove();
      afterRemove(below, gap);
    }, { once: true });
  }, { passive: true });
}

/**
 * The rows either side leaning after the one being pulled. A list where only the row under
 * the finger moves is a list of separate cards; one where its neighbours are tugged a little
 * is a single sheet with something being drawn out of it.
 */
function dragNeighbours(row, dx, rowSelector) {
  const rows = [...document.querySelectorAll(rowSelector)];
  const at = rows.indexOf(row);
  rows.forEach((other, index) => {
    if (other === row) return;
    const distance = Math.abs(index - at);
    if (distance > 2) return;
    other.style.transition = 'none';
    other.style.transform = 'translateX(' + (dx * ROW_PULL / distance).toFixed(2) + 'px)';
  });
}

function releaseNeighbours(rowSelector) {
  for (const other of document.querySelectorAll(rowSelector)) {
    other.style.transition = 'transform 260ms var(--ease-grow)';
    other.style.transform = '';
  }
}

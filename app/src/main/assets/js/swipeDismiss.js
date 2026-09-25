import { bridge } from './state.js';


export const SWIPE_AWAY = 90;


const ROW_PULL = 0.18;

const FLING_TO = 400;
const FLING_SLOWEST = 120;
const FLING_LONGEST = 320;















export function swipeToDismiss(row, keys, { rowSelector, afterRemove }) {
  let originX = 0;
  let originY = 0;
  let travel = 0;
  let isHorizontal = false;
  let lastX = 0;
  let lastAt = 0;
  let speed = 0;

  row.addEventListener('touchstart', event => {
    originX = event.touches[0].clientX;
    originY = event.touches[0].clientY;
    lastX = originX;
    lastAt = event.timeStamp;
    speed = 0;
    travel = 0;
    isHorizontal = false;
    row.dataset.swiped = 'false';
    row.dataset.scrolled = 'false';
    row.style.transition = 'none';
  }, { passive: true });

  row.addEventListener('touchmove', event => {
    const dx = event.touches[0].clientX - originX;
    const dy = event.touches[0].clientY - originY;
    
    
    if (Math.abs(dy) > 8 && Math.abs(dy) > Math.abs(dx)) row.dataset.scrolled = 'true';
    if (!isHorizontal && Math.abs(dx) < 12) return;
    if (row.dataset.scrolled === 'true') return;
    isHorizontal = true;
    const elapsed = event.timeStamp - lastAt;
    if (elapsed > 0) speed = Math.abs(event.touches[0].clientX - lastX) / elapsed;
    lastX = event.touches[0].clientX;
    lastAt = event.timeStamp;
    travel = dx;
    if (Math.abs(dx) > 12) row.dataset.swiped = 'true';
    row.style.transform = 'translateX(' + dx + 'px)';
    row.style.opacity = String(Math.max(0, 1 - Math.abs(dx) / (SWIPE_AWAY * 2)));
    dragNeighbours(row, dx, rowSelector);
  }, { passive: true });

  row.addEventListener('touchend', () => {
    if (Math.abs(travel) < SWIPE_AWAY) {
      // The row came home on 200ms ease-out while the neighbours it had dragged came home on 260ms --ease-grow, so one gesture let go of on two clocks and the pair visibly parted on the way back.
      row.style.transition = 'transform 260ms var(--ease-grow), opacity 200ms ease-out';
      row.style.transform = '';
      row.style.opacity = '';
      releaseNeighbours(rowSelector);
      return;
    }
    // The fling was 400px in 200ms whatever the hand had done, so a slow deliberate drag past the threshold was thrown off at several thousand pixels a second. `ease-out` leaves at 1.72 times its average speed, so the duration is chosen to leave at the finger's own.
    const fling = Math.min(FLING_LONGEST, Math.max(FLING_SLOWEST, 1.72 * (FLING_TO - Math.abs(travel)) / Math.max(speed, 0.5)));
    row.style.transition = 'transform ' + fling + 'ms ease-out, opacity ' + fling + 'ms ease-out';
    keys.forEach(one => bridge.dismissNotification(one));
    bridge.triggerHaptic('dismiss');
    row.style.transform = 'translateX(' + (travel > 0 ? FLING_TO : -FLING_TO) + 'px)';
    row.style.opacity = '0';
    releaseNeighbours(rowSelector);
    row.addEventListener('transitionend', () => {
      
      
      const gap = row.offsetHeight;
      const rows = [...document.querySelectorAll(rowSelector)];
      const below = rows.slice(rows.indexOf(row) + 1);
      row.remove();
      afterRemove(below, gap);
    }, { once: true });
  }, { passive: true });
}






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

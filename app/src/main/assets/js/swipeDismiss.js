import { bridge } from './state.js';


export const SWIPE_AWAY = 90;


const ROW_PULL = 0.18;















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

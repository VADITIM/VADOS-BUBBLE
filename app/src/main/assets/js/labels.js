




























const LABELS = [
  '#app-name', '#title', '#call-name',
  '#player-title', '#player-artist',
  '#lock-title', '#lock-artist',
  '#timer-remaining', '#timer-label',
].join(', ');


const RUNS_OUT_SLACK = 2;


const SCROLL_SPEED = 34;


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
  
  
  const isRightSet = getComputedStyle(label).textAlign === 'right';
  label.style.setProperty('--label-shift', (isRightSet ? over : -over) + 'px');
  
  
  const crossing = (over / SCROLL_SPEED) * 1000;
  label.style.setProperty('--label-scroll-ms', Math.round(crossing / SCROLL_TRAVEL) + 'ms');
  label.classList.add('runs-out');
}

let pending = 0;








const boxes = new ResizeObserver(() => fitLabels());







export function fitLabels() {
  if (pending) return;
  pending = requestAnimationFrame(() => {
    pending = 0;
    for (const label of document.querySelectorAll(LABELS)) {
      
      
      boxes.observe(label);
      measure(label);
    }
  });
}







/* Watching the whole body for character data meant the clock's own seconds — rewritten once a second, forever, screen off included — re-fitted every label on the page: a `getComputedStyle` and a `scrollWidth` read each, which is a forced style recalc and a forced layout of the document every second for a write that can never change a label. A record now has to actually touch a label before anything is measured. */
new MutationObserver(records => {
  for (const record of records) {
    const node = record.target.nodeType === Node.TEXT_NODE
      ? record.target.parentElement
      : record.target;
    if (!node || node.nodeType !== Node.ELEMENT_NODE) continue;
    if (node.closest(LABELS) || node.querySelector(LABELS)) {
      fitLabels();
      return;
    }
  }
}).observe(document.body, {
  subtree: true,
  childList: true,
  characterData: true,
});
fitLabels();

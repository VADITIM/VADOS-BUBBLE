




























const LABELS = [
  '#app-name', '#title', '#call-name',
  '#player-title', '#player-artist',
  '#lock-title', '#lock-artist',
  '.lock-note-who', '.lock-note-said',
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







new MutationObserver(fitLabels).observe(document.body, {
  subtree: true,
  childList: true,
  characterData: true,
});
fitLabels();

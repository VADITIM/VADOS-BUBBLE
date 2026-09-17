




























const LABELS = [
  '#app-name', '#title', '#call-name',
  '#lock-title', '#lock-artist',
  '#timer-remaining', '#timer-label',
].join(', ');


const RUNS_OUT_SLACK = 2;


const SCROLL_SPEED = 22;


// The blank the copy holds ahead of the real label, in pixels. Mirrors --label-gap in pill.css.
const LABEL_GAP = 40;

function measure(label) {
  // The copy the loop is drawn from is part of the label's own width once the class is on, so every re-fit would have found the label wider than the last one did and stretched the loop further each time. The class comes off before anything is read.
  label.classList.remove('runs-out');
  const width = label.scrollWidth;
  if (width - label.clientWidth <= RUNS_OUT_SLACK) {
    label.style.removeProperty('--label-shift');
    label.style.removeProperty('--label-scroll-ms');
    label.removeAttribute('data-loop');
    return;
  }
  
  
  const shift = width + LABEL_GAP;
  label.dataset.loop = label.textContent;
  label.style.setProperty('--label-shift', shift + 'px');
  label.style.setProperty('--label-scroll-ms', Math.round((shift / SCROLL_SPEED) * 1000) + 'ms');
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

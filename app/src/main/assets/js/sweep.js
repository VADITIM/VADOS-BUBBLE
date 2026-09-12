import { shared } from './state.js';

// Mirrors the .sweeping and .swept transition durations in pill.css.
export const SWEEP_IN = 300;
const SWEEP_OUT = 340;

// The bar covers the glyphs and a hair past them, never the label's whole line box — a reading sitting in a wide flex cell had a bar four times the width of the number it was revealing.
const SWEEP_PAD = 5;

const sweeping = new WeakMap();
let ruler = null;

function labelWidth(element, text) {
  if (!ruler) {
    ruler = document.createElement('span');
    ruler.id = 'label-ruler';
    document.body.appendChild(ruler);
  }
  const style = getComputedStyle(element);
  ruler.style.font = style.font;
  ruler.style.letterSpacing = style.letterSpacing;
  ruler.style.textTransform = style.textTransform;
  ruler.textContent = text;
  return Math.ceil(ruler.getBoundingClientRect().width);
}

// The bar is drawn by the label's parent rather than by the label itself: the label has to be invisible for the whole growth and appear on the frame the bar is full, and a bar that is the label's own pseudo-element goes invisible with it.
function placeBar(element, host, wanted) {
  const range = document.createRange();
  range.selectNodeContents(element);
  const text = range.getBoundingClientRect();
  const box = element.getBoundingClientRect();
  const hostBox = host.getBoundingClientRect();
  const width = Math.max(text.width, labelWidth(element, wanted));
  const left = (text.width ? text.left : box.left) - hostBox.left - SWEEP_PAD;
  const height = (text.height || box.height);
  const top = (text.height ? text.top : box.top) - hostBox.top;

  host.style.setProperty('--sweep-width', Math.ceil(width + SWEEP_PAD * 2) + 'px');
  host.style.setProperty('--sweep-left', Math.round(left) + 'px');
  host.style.setProperty('--sweep-top', Math.round(top - height * 0.06) + 'px');
  host.style.setProperty('--sweep-height', Math.ceil(height * 1.12) + 'px');
}

function clearBar(element, host) {
  host.classList.remove('sweep-host', 'sweeping', 'swept');
  element.classList.remove('sweep-hidden');
  for (const name of ['--sweep-width', '--sweep-left', '--sweep-top', '--sweep-height']) {
    host.style.removeProperty(name);
  }
}

// The bar has to cover the label it is uncovering, and at the moment it starts growing the box is still the width of the label it is replacing — a longer new reading was handed over under a bar that only reached the old one's end.
export function sweepInto(element, next, force) {
  const wanted = next || '';
  const host = element.parentElement;
  const running = sweeping.get(element);
  if (running && running.wanted === wanted) return;
  if (!running && !force && element.textContent === wanted) return;
  if (running) {
    clearTimeout(running.cover);
    clearTimeout(running.clear);
  }

  host.classList.add('sweep-host');
  placeBar(element, host, wanted);
  element.classList.add('sweep-hidden');
  host.classList.remove('swept');
  host.classList.add('sweeping');
  const state = { wanted, host, cover: 0, clear: 0 };
  sweeping.set(element, state);

  state.cover = setTimeout(() => {
    element.textContent = wanted;
    element.classList.remove('sweep-hidden');
    host.classList.add('swept');
  }, SWEEP_IN);
  state.clear = setTimeout(() => {
    sweeping.delete(element);
    clearBar(element, host);
  }, SWEEP_IN + SWEEP_OUT);
}

export function sweepLabel(element, next) {
  if (shared.labelSweep) sweepInto(element, next);
  else element.textContent = next || '';
}

// A dashboard reading is already standing at its final text when its bubble lands, so the reveal there is asked for rather than caused by a change — the bar is told to play over text it is not replacing.
export function revealLabel(element) {
  if (shared.labelSweep) sweepInto(element, element.textContent, true);
}

// A reading that changes while its reveal is still pending must not sweep on its own: the settle reveal is the one animation, and a weather answer landing first played a second one before it.
export function holdLabel(element, next) {
  element.textContent = next || '';
  if (shared.labelSweep) element.classList.add('sweep-hidden');
}

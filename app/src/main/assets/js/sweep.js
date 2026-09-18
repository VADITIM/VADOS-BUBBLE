import { shared } from './state.js';

// Mirrors the .sweeping and .swept transition durations in pill.css.
export const SWEEP_IN = 300;
const SWEEP_OUT = 340;

// The bar covers the glyphs and a hair past them, never the label's whole line box — a reading sitting in a wide flex cell had a bar four times the width of the number it was revealing.
const SWEEP_PAD = 5;

const sweeping = new WeakMap();
let ruler = null;

export function hideLabel(element) {
  element.classList.add('sweep-hidden');
  const glyph = labelGlyph(element);
  if (glyph) glyph.classList.add('sweep-hidden');
}

export function showLabel(element) {
  element.classList.remove('sweep-hidden');
  const glyph = labelGlyph(element);
  if (glyph) glyph.classList.remove('sweep-hidden');
}

function labelWidth(element, text) {
  if (!ruler) {
    ruler = document.createElement('span');
    ruler.id = 'label-ruler';
    document.body.appendChild(ruler);
  }
  const style = getComputedStyle(element);
  // The `font` shorthand computes to an empty string whenever a longhand it cannot express is set — `font-variant-numeric: tabular-nums` on the transfer head was enough — so the ruler measured in the body's default face and the data-used reading got a bar several times its own width. The longhands are copied one by one instead.
  for (const name of ['fontFamily', 'fontSize', 'fontWeight', 'fontStyle', 'fontStretch', 'fontVariantNumeric', 'letterSpacing', 'wordSpacing', 'textTransform']) {
    ruler.style[name] = style[name];
  }
  ruler.textContent = text;
  return Math.ceil(ruler.getBoundingClientRect().width);
}

// A glyph standing beside a reading is part of that label, so the bar starts at the glyph's left edge and the two are uncovered on the same frame.
function labelGlyph(element) {
  for (const sibling of [element.previousElementSibling, element.nextElementSibling]) {
    if (!sibling) continue;
    const name = sibling.id + ' ' + (typeof sibling.className === 'string' ? sibling.className : '');
    if (/glyph|icon/.test(name)) return sibling;
  }
  return null;
}

// The bar is drawn by the label's parent rather than by the label itself: the label has to be invisible for the whole growth and appear on the frame the bar is full, and a bar that is the label's own pseudo-element goes invisible with it.
function placeBar(element, host, wanted) {
  const range = document.createRange();
  range.selectNodeContents(element);
  const text = range.getBoundingClientRect();
  const box = element.getBoundingClientRect();
  const hostBox = host.getBoundingClientRect();
  const width = Math.max(text.width, labelWidth(element, wanted));
  let left = (text.width ? text.left : box.left) - hostBox.left - SWEEP_PAD;
  let right = left + width + SWEEP_PAD * 2;
  let top = (text.height ? text.top : box.top) - hostBox.top;
  let height = (text.height || box.height);

  const glyph = labelGlyph(element);
  if (glyph) {
    const glyphBox = glyph.getBoundingClientRect();
    const bottom = Math.max(top + height, glyphBox.bottom - hostBox.top);
    left = Math.min(left, glyphBox.left - hostBox.left - SWEEP_PAD);
    right = Math.max(right, glyphBox.right - hostBox.left + SWEEP_PAD);
    top = Math.min(top, glyphBox.top - hostBox.top);
    height = bottom - top;
  }

  host.style.setProperty('--sweep-width', Math.ceil(right - left) + 'px');
  host.style.setProperty('--sweep-left', Math.round(left) + 'px');
  host.style.setProperty('--sweep-top', Math.round(top - height * 0.06) + 'px');
  host.style.setProperty('--sweep-height', Math.ceil(height * 1.12) + 'px');
}

function clearBar(element, host) {
  host.classList.remove('sweep-host', 'sweeping', 'swept');
  showLabel(element);
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
  hideLabel(element);
  host.classList.remove('swept');
  host.classList.add('sweeping');
  const state = { wanted, host, cover: 0, clear: 0 };
  sweeping.set(element, state);

  state.cover = setTimeout(() => {
    element.textContent = wanted;
    showLabel(element);
    host.classList.add('swept');
  }, SWEEP_IN);
  state.clear = setTimeout(() => {
    sweeping.delete(element);
    clearBar(element, host);
  }, SWEEP_IN + SWEEP_OUT);
}

// A sweep only hands its text back when its second timer fires, so a reading swept away in a section that is being torn down under it would have been handed back into a panel that had already gone — the bar is cleared and the text put on in the same frame instead.
export function endSweep(element, text) {
  const running = sweeping.get(element);
  if (running) {
    clearTimeout(running.cover);
    clearTimeout(running.clear);
    sweeping.delete(element);
    clearBar(element, running.host);
  }
  element.textContent = text;
  showLabel(element);
}

// A panel closing over a sweep left its two timers to fire into a section that had gone: the text was handed back late, and the reveal asked for on the next open was swallowed whole by the stale entry still standing for the same wanted text — which is the reveal that plays twice, or does not play at all. The bar is cleared and the wanted text written instead. Whether the label is *seen* is left exactly as the close found it: a reading still waiting for its reveal was being handed its text and un-hidden on the way out, so it faded in over a dashboard that was already leaving, with nothing having revealed it.
export function settleSweep(element) {
  const running = sweeping.get(element);
  const wasHidden = element.classList.contains('sweep-hidden');
  if (running) {
    clearTimeout(running.cover);
    clearTimeout(running.clear);
    sweeping.delete(element);
    clearBar(element, running.host);
    element.textContent = running.wanted;
  }
  if (wasHidden) hideLabel(element);
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
  if (shared.labelSweep) hideLabel(element);
}

import { catchInto, stirLiquid } from './liquid.js';
import { pill, root } from './state.js';

/**
 * The bubble where the lock icon is: a padlock on the lock screen, and nothing else.
 *
 * It carries no mod, answers no touch and says one thing — the phone is locked — which the
 * system already draws there. That is the whole justification for the pixels: it is standing
 * on something, the way every other bubble here is. It costs no window, because nothing about
 * it is interactive.
 *
 * On unlock it does what a padlock does: it opens, and then it goes. The shackle lifts, and the
 * shape flies up into the bubble at the cutout as a merge the mirror measures — the same
 * journey the Lock Now bubble makes from the bottom of the screen, so the two arrive as two
 * drops of one liquid rather than as two boxes disappearing.
 */
const padlockPill = document.getElementById('padlock');
const padlockShackle = document.getElementById('padlock-shackle');

/**
 * How far above the bottom of the screen it stands. One UI draws its own lock icon there, and
 * *there* is a measurement off the phone: this is the number to move once it has been walked,
 * and it is the only thing about this bubble that cannot be decided here.
 */
const PADLOCK_BOTTOM = 232;

/** The shackle lifting, and then the flight. Opening is the cause; leaving is the consequence. */
const PADLOCK_OPEN = 260;
const PADLOCK_FLY = 520;
const PADLOCK_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';

let flying = false;

export function isPadlockShowing() {
  return padlockPill.classList.contains('showing');
}

export { padlockPill };

/** Standing there because the phone is locked, gone the moment it is not. */
export function paintPadlock(locked) {
  if (locked) {
    flying = false;
    padlockPill.classList.remove('opened');
    padlockPill.classList.add('showing');
    stirLiquid(420);
    return;
  }
  if (!padlockPill.classList.contains('showing') || flying) return;
  openAndLeave();
}

/**
 * Open first, then leave. A padlock that vanished the instant the phone unlocked would be a
 * box being hidden; one that visibly opens and *then* goes is the same object answering what
 * just happened — which is the glyph-as-cause rule, with the shackle as the glyph.
 */
function openAndLeave() {
  flying = true;
  padlockPill.classList.add('opened');
  stirLiquid(PADLOCK_OPEN + PADLOCK_FLY + 400);
  setTimeout(() => {
    if (!flying) return;
    const from = padlockPill.getBoundingClientRect();
    const to = pill.getBoundingClientRect();
    const dx = (to.left + to.width / 2) - (from.left + from.width / 2);
    const dy = (to.top + to.height / 2) - (from.top + from.height / 2);
    const drop = Math.min(to.width / from.width, to.height / from.height) * 0.9;
    const flight = padlockPill.animate(
      [
        { translate: '0px 0px', scale: 1 },
        { translate: dx.toFixed(1) + 'px ' + dy.toFixed(1) + 'px', scale: drop },
      ],
      { duration: PADLOCK_FLY, easing: PADLOCK_EASE, fill: 'forwards' }
    );
    catchInto(padlockPill, pill, {
      swallowed: () => {
        flying = false;
        // Out on the spot rather than faded: by now it is inside the bubble and there is
        // nothing left to see it happen against.
        padlockPill.style.setProperty('--padlock-fade-ms', '0ms');
        padlockPill.classList.remove('showing', 'opened');
        requestAnimationFrame(() => {
          flight.cancel();
          padlockPill.style.removeProperty('--padlock-fade-ms');
        });
      },
    });
  }, PADLOCK_OPEN);
}

root.style.setProperty('--padlock-bottom', PADLOCK_BOTTOM + 'px');
padlockShackle.textContent = '';

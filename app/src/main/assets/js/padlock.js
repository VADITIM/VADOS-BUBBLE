import { catchInto, releaseCatch, stirLiquid } from './liquid.js';
import { bumpPill } from './lock.js';
import { pill, root } from './state.js';

/**
 * The bubble where the lock icon is: a padlock on the lock screen, and nothing else.
 *
 * It carries no mod, answers no touch and says one thing — the phone is locked — which the
 * system already draws there. That is the whole justification for the pixels: it is standing
 * on something, the way every other bubble here is. It costs no window, because nothing about
 * it is interactive.
 *
 * On unlock it does what a padlock does: it springs open and goes, both at once. The shackle lifts, the shape pops, and it is already flying up into the bubble at the cutout as a merge the mirror measures — the same journey the Lock Now bubble makes from the bottom of the screen, and the same arrival into the bubble, so the two read as two drops of one liquid rather than as two boxes disappearing.
 */
const padlockPill = document.getElementById('padlock');
const padlockShackle = document.getElementById('padlock-shackle');

/**
 * How far above the bottom of the screen it stands. One UI draws its own lock icon there, and
 * *there* is a measurement off the phone: this is the number to move once it has been walked,
 * and it is the only thing about this bubble that cannot be decided here.
 */
const PADLOCK_BOTTOM = 232;

/** The journey home, and the curve it lands on: it overshoots and settles back, like everything else here. */
const PADLOCK_FLY = 520;
const PADLOCK_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';

/** The pop the shackle springing open throws off, and how far into the journey it is over. A padlock opens with a snap, so the size has to answer in a snap — held any longer it reads as the shape swelling rather than as something letting go. */
const PADLOCK_POP_SCALE = 1.22;
const PADLOCK_POP_TURN = 0.16;
const PADLOCK_POP_EASE = 'cubic-bezier(0.2, 1.8, 0.3, 1)';

let flying = false;

/** Whether the keyguard is up, kept so the setting below can be answered without being told again. */
let locked = false;

/** The settings panel's own switch. Off, One UI's own lock icon is left to stand alone. */
let allowed = true;

window.setPadlockShown = wanted => {
  const next = Boolean(Number(wanted));
  if (next === allowed) return;
  allowed = next;
  // Straight off rather than through the opening flight: nothing was unlocked, a switch was
  // thrown, and a padlock playing its unlock animation would be saying something untrue.
  if (!allowed) {
    flying = false;
    padlockPill.classList.remove('showing', 'opened');
    stirLiquid(420);
    return;
  }
  paintPadlock(locked);
};

export function isPadlockShowing() {
  return padlockPill.classList.contains('showing');
}

export { padlockPill };

/** Standing there because the phone is locked, gone the moment it is not. */
export function paintPadlock(isLocked) {
  locked = isLocked;
  if (!allowed) return;
  if (isLocked) {
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
 * Open and go, in one move rather than in two. The shackle lifting is still the cause — it is what starts everything below — but the padlock no longer stands there for a quarter of a second afterwards waiting to be allowed to leave: it pops as the shackle springs, and it is already on its way while it pops. Held first, the opening and the journey read as two unrelated things happening in turn, which is the same complaint the Lock Now bubble's flight was rebuilt to answer.
 */
function openAndLeave() {
  flying = true;
  padlockPill.classList.add('opened');
  stirLiquid(PADLOCK_FLY + 400);
  const from = padlockPill.getBoundingClientRect();
  const to = pill.getBoundingClientRect();
  const dx = (to.left + to.width / 2) - (from.left + from.width / 2);
  const dy = (to.top + to.height / 2) - (from.top + from.height / 2);
  // A shape wider than what is taking it in has not arrived — it is something that still has to close — so the journey ends at the receiver's own measured size, a shade under it so it is properly inside rather than exactly filling it.
  const drop = Math.min(to.width / from.width, to.height / from.height) * 0.9;
  // Two animations on two properties rather than one keyframe list: a keyframe's easing owns every property in it, and the pop and the travel want opposite ones — the pop overshoots at the very start and is over almost at once, the travel eases the whole way. Separate properties compose, and both are real boxes to the mirror.
  const flight = [
    padlockPill.animate(
      [
        { scale: 1, offset: 0, easing: PADLOCK_POP_EASE },
        { scale: PADLOCK_POP_SCALE, offset: PADLOCK_POP_TURN, easing: PADLOCK_EASE },
        { scale: drop, offset: 1 },
      ],
      { duration: PADLOCK_FLY, fill: 'forwards' }
    ),
    padlockPill.animate(
      [
        { translate: '0px 0px' },
        { translate: dx.toFixed(1) + 'px ' + dy.toFixed(1) + 'px' },
      ],
      { duration: PADLOCK_FLY, easing: PADLOCK_EASE, fill: 'forwards' }
    ),
  ];
  // The catch is here for the goo — the swell that says something is being taken in — and not for deciding when the journey is over: that is answered by where the two centres are, which is a plainer question than an overlap share and gives the same answer at the same instant whatever size the row happens to be. Same entry as the Lock Now bubble's, because they are two drops of one liquid arriving at one bubble and an arrival that read differently between them would say they were two different kinds of thing.
  catchInto(padlockPill, pill);
  watchPadlockCentre(flight);
}

/**
 * Watched rather than timed. The flight is eased and its distance depends on where the bubble
 * happens to be, so the moment the drop is level with it is not a number that can be written
 * down beforehand — and it is the moment the whole merge is judged on.
 */
function watchPadlockCentre(flight) {
  if (!flying) return;
  const traveller = padlockPill.getBoundingClientRect();
  const receiver = pill.getBoundingClientRect();
  if (traveller.top + traveller.height / 2 > receiver.top + receiver.height / 2) {
    requestAnimationFrame(() => watchPadlockCentre(flight));
    return;
  }
  flying = false;
  releaseCatch(padlockPill);
  bumpPill();
  // Out on the spot rather than faded: by now it is inside the bubble and there is
  // nothing left to see it happen against.
  padlockPill.style.setProperty('--padlock-fade-ms', '0ms');
  padlockPill.classList.remove('showing', 'opened');
  // And only once that has been drawn is the journey let go of, or the two happen in one frame
  // and the snap back to where it started is what the frame shows.
  requestAnimationFrame(() => {
    flight.forEach(move => move.cancel());
    padlockPill.style.removeProperty('--padlock-fade-ms');
  });
}

root.style.setProperty('--padlock-bottom', PADLOCK_BOTTOM + 'px');
padlockShackle.textContent = '';

/** The settings panel's own fine adjustment, on top of PADLOCK_BOTTOM. Positive lifts it higher. */
window.setPadlockOffset = offset => {
  root.style.setProperty('--padlock-y', (Number(offset) || 0) + 'px');
};

import { catchInto, releaseCatch, stirLiquid } from './liquid.js';
import { bumpPill } from './lock.js';
import { pill, root } from './state.js';











const padlockPill = document.getElementById('padlock');
const padlockShackle = document.getElementById('padlock-shackle');






const PADLOCK_BOTTOM = 232;


const PADLOCK_FLY = 520;
const PADLOCK_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';


const PADLOCK_POP_SCALE = 1.22;
const PADLOCK_POP_TURN = 0.16;
const PADLOCK_POP_EASE = 'cubic-bezier(0.2, 1.8, 0.3, 1)';

let flying = false;


let locked = false;


let allowed = true;

window.setPadlockShown = wanted => {
  const next = Boolean(Number(wanted));
  if (next === allowed) return;
  allowed = next;
  
  
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




function openAndLeave() {
  flying = true;
  padlockPill.classList.add('opened');
  stirLiquid(PADLOCK_FLY + 400);
  const from = padlockPill.getBoundingClientRect();
  const to = pill.getBoundingClientRect();
  const dx = (to.left + to.width / 2) - (from.left + from.width / 2);
  const dy = (to.top + to.height / 2) - (from.top + from.height / 2);
  
  const drop = Math.min(to.width / from.width, to.height / from.height) * 0.9;
  
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
  
  catchInto(padlockPill, pill);
  watchPadlockCentre(flight);
}






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
  
  
  padlockPill.style.setProperty('--padlock-fade-ms', '0ms');
  padlockPill.classList.remove('showing', 'opened');
  
  
  requestAnimationFrame(() => {
    flight.forEach(move => move.cancel());
    padlockPill.style.removeProperty('--padlock-fade-ms');
  });
}

root.style.setProperty('--padlock-bottom', PADLOCK_BOTTOM + 'px');
padlockShackle.textContent = '';


window.setPadlockOffset = offset => {
  root.style.setProperty('--padlock-y', (Number(offset) || 0) + 'px');
};

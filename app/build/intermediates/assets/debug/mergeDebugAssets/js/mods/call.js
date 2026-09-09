import { toClosed } from '../row.js';
import { mods, pill, shared } from '../state.js';

const callAvatar = document.getElementById('call-avatar');
const callName = document.getElementById('call-name');
const callTime = document.getElementById('call-time');
let callTicker = null;

/** The handset, for a call on the line: what a phone call has looked like forever. */
const PHONE_MARK =
  '<svg viewBox="0 0 24 24"><path d="M6.62 10.79a15.15 15.15 0 0 0 6.59 6.59l2.2-2.2a1 1 0 0 1 1.02-.24 11.4 11.4 0 0 0 3.57.57 1 1 0 0 1 1 1V20a1 1 0 0 1-1 1A17 17 0 0 1 3 4a1 1 0 0 1 1-1h3.5a1 1 0 0 1 1 1c0 1.25.2 2.45.57 3.57a1 1 0 0 1-.25 1.02z"/></svg>';

/** Discord's own mark, for the circle it never sends a picture for. */
const DISCORD_MARK =
  '<svg viewBox="0 0 24 24"><path d="M20.317 4.3698a19.7913 19.7913 0 0 0-4.8851-1.5152.0741.0741 0 0 0-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 0 0-.0785-.037 19.7363 19.7363 0 0 0-4.8852 1.515.0699.0699 0 0 0-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 0 0 .0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 0 0 .0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 0 0-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 0 1-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 0 1 .0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 0 1 .0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 0 1-.0066.1276 12.2986 12.2986 0 0 1-1.873.8914.0766.0766 0 0 0-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 0 0 .0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 0 0 .0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 0 0-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189z"/></svg>';

/**
 * The circle for whoever is on the other end: their picture when the notification
 * carries one, otherwise Discord's mark. The voice notification never carries one,
 * so the mark is the normal case rather than the fallback — and it says which app
 * the call is in, which is the one thing a letter could not.
 */
export function paintAvatar(element) {
  const art = (shared.call && shared.call.avatarBase64) || '';
  element.style.backgroundImage = art ? 'url(' + art + ')' : '';
  element.innerHTML = art ? '' : (shared.call && shared.call.phone ? PHONE_MARK : DISCORD_MARK);
}

/**
 * How long the line has been open, counted here rather than re-read: the dialer
 * posts the start once and leaves the shade to run its own chronometer, so the
 * notification says nothing new for the whole call.
 */
function callElapsed() {
  const seconds = Math.max(0, Math.floor((Date.now() - (shared.call.since || Date.now())) / 1000));
  const parts = [Math.floor(seconds / 60) % 60, seconds % 60];
  if (seconds >= 3600) parts.unshift(Math.floor(seconds / 3600));
  return parts
    .map((part, index) => (index ? String(part).padStart(2, '0') : String(part)))
    .join(':');
}

function runCallClock() {
  clearInterval(callTicker);
  if (!shared.call || !shared.call.phone) {
    callTime.textContent = '';
    return;
  }
  callTime.textContent = callElapsed();
  callTicker = setInterval(() => {
    if (shared.call && shared.call.phone) callTime.textContent = callElapsed();
  }, 1000);
}

export function paintCall() {
  if (!shared.call) return;
  document.documentElement.style.setProperty(
    '--app-accent', shared.call.accent || 'var(--section-color)'
  );
  pill.classList.toggle('phone-call', Boolean(shared.call.phone));
  paintAvatar(callAvatar);
  callName.textContent = shared.call.name || '';
}

window.onCallUpdate = payload => {
  shared.call = payload;
  if (!shared.call) {
    mods.delete('call');
    clearInterval(callTicker);
    pill.classList.remove('phone-call');
    if (shared.state === 'idle') toClosed();
    return;
  }
  mods.add('call');
  paintCall();
  runCallClock();
  if (shared.state === 'idle') toClosed();
};

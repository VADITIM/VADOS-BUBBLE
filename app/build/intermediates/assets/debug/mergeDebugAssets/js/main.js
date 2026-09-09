/**
 * The page's one entry point: it pulls every module in and then tells the host it is ready.
 *
 * The imports are for their side effects — each module attaches its own listeners and its own
 * window.on… entry points as it evaluates — so the order here is the order the one script used
 * to run in. It is not load-bearing beyond that: state.js imports nothing, which is what makes
 * it evaluate first however the cycles between the others resolve, and no module touches
 * another one's binding until something calls a function.
 *
 * ready() is last and lives here rather than in bridge.js, because the host replays everything
 * it has queued the moment it is called and the page has to be whole before that.
 */
import { bridge } from './state.js';
import './motion.js';
import './liquid.js';
import './row.js';
import './mods/notification.js';
import './mods/media.js';
import './padlock.js';
import './notes.js';
import './lock.js';
import './mods/timer.js';
import './mods/call.js';
import './tabs.js';
import './now.js';
import './status.js';
import './clock.js';
import './double.js';
// Last of the modules that draw, because it measures what they wrote: its observers have to be
// standing before the host starts replaying, and everything it measures has to exist by then.
import './labels.js';
import './bridge.js';

bridge.ready();

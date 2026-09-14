import { openStatusPanel, statusOpen } from './status.js';
import { openNotifications } from './tabs.js';
import { bridge, root, shared } from './state.js';


const EDGE_DROP = 52;
const EDGE_ACROSS = 52;


let edgeLive = false;
let edgeStart = null;
let edgeFired = false;


export function refreshEdgeProxy() {
  const wants = root.classList.contains('locked') && !statusOpen && shared.state !== 'extended';
  if (wants === edgeLive) return;
  edgeLive = wants;
  bridge.setEdgeProxy(wants);
}


export function edgeTouch(action, x, y, source) {
  if (action === 'down') {
    edgeStart = { x, y };
    edgeFired = false;
    return;
  }
  if (action !== 'move' || !edgeStart || edgeFired) return;
  const down = y - edgeStart.y;
  const inward = source === 'edge-right' ? edgeStart.x - x : x - edgeStart.x;
  if (down < EDGE_DROP || inward < EDGE_ACROSS) return;
  edgeFired = true;
  edgeStart = null;
  bridge.triggerHaptic('expand');
  if (source === 'edge-right') openStatusPanel();
  else openNotifications();
  refreshEdgeProxy();
}

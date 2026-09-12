import { PROXY_TAP_SLOP } from './bridge.js';
import { clockPill } from './clock.js';
import { stirLiquid } from './liquid.js';
import { rubberBandPast, toy, untoy } from './motion.js';
import { closeNowPanel, nowOpen } from './now.js';
import { closePanelNow, openPanelNow } from './lock.js';
import { applyClosedWindow, liveMods, showFace, toClosed } from './row.js';
import { GROWN_PAD, HOLD_MILLIS, bridge, dragGate, pill, root, shared } from './state.js';
import { holdLabel, revealLabel, sweepLabel } from './sweep.js';
















export const statusPill = document.getElementById('status');
const statusClosed = document.getElementById('status-closed');
const statusFaces = {
  closed: statusClosed,
  charge: document.getElementById('status-charge'),
  actions: document.getElementById('status-actions'),
};


const STATUS_RIGHT = 14;



const CLOSED_FACE = { media: 'media', timer: 'timer', call: 'call' };
function closedFace() {
  const owner = liveMods()[0];
  return owner ? CLOSED_FACE[owner] : 'idle';
}

function setReading(element, text) {
  if (readingsHeld) holdLabel(element, text);
  else sweepLabel(element, text);
}


const TRANSFER_COLOUR = { download: '#f09b3a', upload: '#3a8cff' };

export function refreshDock() {
  if (statusOpen) paintTransferStrip();
}

function paintTransferStrip() {
  const isLive = Boolean(transfer);
  transferStrip.classList.toggle('idle', !isLive);
  if (!isLive) {
    transferGlyph.innerHTML = GLYPHS.dataToday;
    setReading(transferReading, bridge.readDataToday() + ' today');
    return;
  }
  transferGlyph.innerHTML = transfer.isDone ? GLYPHS.transferDone : GLYPHS[transfer.mod];
  const share = transfer.isDone || !transfer.total ? 100 : Math.round((transfer.done / transfer.total) * 100);
  transferReading.textContent = transfer.isDone ? 'Done' : share + '%';
  transferStrip.style.setProperty('--transfer-share', share + '%');
  transferStrip.style.setProperty('--transfer-color', TRANSFER_COLOUR[transfer.mod] || 'var(--section-color)');
}

// Main leaves the punch hole for the top edge while the panel is open: its own mod has gone down to the Now bubble, so what is left is a bare bubble curving into the screen's own top edge.
function raiseMain() {
  pill.classList.add('panel-top');
  // An Alert standing when the panel opens is grown, and handing it the closed face left the notification's box wearing the row's contents until its dwell ran out. The rise is a closed bubble's shape, so it only repaints a bubble that is in one.
  if (!root.classList.contains('grown')) showFace(closedFace());
  stirLiquid(320);
}


const STATUS_TRAVEL = 420;
const STATUS_LAND = 0.42;












const STATUS_MARGIN = 10;
const STATUS_MARGIN_FOOT = STATUS_MARGIN;
const STATUS_PANEL = { width: 0, height: 0, ms: 340 };













const screenWidth = () => document.documentElement.clientWidth;
const screenHeight = () => document.documentElement.clientHeight;














const QUICK_CLEARANCE = 22;
// Mirrors --corner-inset and --corner-tall in pill.css. The panel used to start a clearance below the Status bubble's rect, which is measured while that bubble is still the small one on the bar — so the panel began where the closed bubble was and the grown one came down on top of the weather line.
const CORNER_INSET = 10;
const CORNER_TALL = 40;

function fitStatusPanel() {
  const top = CORNER_INSET + CORNER_TALL + QUICK_CLEARANCE;
  STATUS_PANEL.width = Math.round(screenWidth() - STATUS_MARGIN * 2);
  STATUS_PANEL.height = Math.round(screenHeight() - top - STATUS_MARGIN_FOOT);
  
  
  
  root.style.setProperty('--quick-left', Math.round(statusPanelLeft()) + 'px');
  root.style.setProperty('--quick-top', Math.round(top) + 'px');
  root.style.setProperty('--quick-width', STATUS_PANEL.width + 'px');
  root.style.setProperty('--quick-height', STATUS_PANEL.height + 'px');
}
fitStatusPanel();


const STATUS_PULL = 34;











const STATUS_PULL_STOLEN = 12;





const SLOT_MOVE = 260;
const SLOT_BOUNCE = 220;







const SLOTS = { usb: 0, transfer: 1, modus: 7, link: 8, battery: 9 };







const GLYPHS = {
  wifi: '<svg viewBox="0 0 24 24"><path d="M1.33309 8.07433C0.92156 8.44266 0.886539 9.07485 1.25487 9.48638C1.62319 9.89791 2.25539 9.93293 2.66691 9.5646L1.33309 8.07433ZM21.3331 9.5646C21.7446 9.93293 22.3768 9.89791 22.7451 9.48638C23.1135 9.07485 23.0784 8.44266 22.6669 8.07433L21.3331 9.5646ZM12 19C11.4477 19 11 19.4477 11 20C11 20.5523 11.4477 21 12 21V19ZM12.01 21C12.5623 21 13.01 20.5523 13.01 20C13.01 19.4477 12.5623 19 12.01 19V21ZM14.6905 17.04C15.099 17.4116 15.7315 17.3817 16.1031 16.9732C16.4748 16.5646 16.4448 15.9322 16.0363 15.5605L14.6905 17.04ZM18.0539 13.3403C18.4624 13.7119 19.0949 13.682 19.4665 13.2734C19.8381 12.8649 19.8082 12.2324 19.3997 11.8608L18.0539 13.3403ZM7.96372 15.5605C7.55517 15.9322 7.52524 16.5646 7.89687 16.9732C8.2685 17.3817 8.90095 17.4116 9.3095 17.04L7.96372 15.5605ZM4.60034 11.8608C4.19179 12.2324 4.16185 12.8649 4.53348 13.2734C4.90511 13.682 5.53756 13.7119 5.94611 13.3403L4.60034 11.8608ZM2.66691 9.5646C5.14444 7.34716 8.41371 6 12 6V4C7.90275 4 4.16312 5.54138 1.33309 8.07433L2.66691 9.5646ZM12 6C15.5863 6 18.8556 7.34716 21.3331 9.5646L22.6669 8.07433C19.8369 5.54138 16.0972 4 12 4V6ZM12 21H12.01V19H12V21ZM12 16C13.0367 16 13.9793 16.3931 14.6905 17.04L16.0363 15.5605C14.9713 14.5918 13.5536 14 12 14V16ZM12 11C14.3319 11 16.4546 11.8855 18.0539 13.3403L19.3997 11.8608C17.4466 10.0842 14.8487 9 12 9V11ZM9.3095 17.04C10.0207 16.3931 10.9633 16 12 16V14C10.4464 14 9.02872 14.5918 7.96372 15.5605L9.3095 17.04ZM5.94611 13.3403C7.54544 11.8855 9.66815 11 12 11V9C9.15127 9 6.55344 10.0842 4.60034 11.8608L5.94611 13.3403Z" fill="currentColor"/></svg>',
  ethernet: '<svg viewBox="0 0 24 24"><path d="M7 3h10a2 2 0 0 1 2 2v5h-3v3h-2v-3h-4v3H8v-3H5V5a2 2 0 0 1 2-2zm-2 12h14v4a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2z"/></svg>',
  bluetooth: '<svg viewBox="0 0 24 24"><path fill="none" d="M7 17L17 7L12 2V22L17 17L7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  usb: '<svg viewBox="0 0 24 24"><path d="M11 2h2l1.5 2.5h-5zM11 5h2v10.2l3-2.4V10h-1.5V7.5h4V10H17v3.4l-4.8 3.8V19a2 2 0 1 1-2 0v-3.6l-3.4-2.6V10.9a2 2 0 1 1 2 0v.9l1.4 1.1z"/></svg>',
  
  
  
  download: '<svg viewBox="0 0 24 24"><path d="M11 3h2v9.2l3.3-3.3 1.4 1.4L12 16l-5.7-5.7 1.4-1.4L11 12.2zM5 18h14v2H5z"/></svg>',
  upload: '<svg viewBox="0 0 24 24"><path d="M12 3l5.7 5.7-1.4 1.4L13 6.8V16h-2V6.8L7.7 10.1 6.3 8.7zM5 18h14v2H5z"/></svg>',
  transferDone: '<svg viewBox="0 0 24 24"><path d="M9.8 16.2 5.6 12l-1.4 1.4 5.6 5.6L20.4 7.9 19 6.5z"/></svg>',
  dataToday: '<svg viewBox="0 0 24 24"><path d="M4 14h3v6H4zm6.5-5h3v11h-3zM17 4h3v16h-3z"/></svg>',
  hotspot: '<svg viewBox="0 0 24 24"><path d="M12 9.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5zM7.8 5.8 6.4 4.4a10 10 0 0 0 0 15.2l1.4-1.4a8 8 0 0 1 0-12.4zm9.8-1.4-1.4 1.4a8 8 0 0 1 0 12.4l1.4 1.4a10 10 0 0 0 0-15.2z"/></svg>',
  
  
  gps: '<svg viewBox="0 0 24 24"><path d="M12 2a7 7 0 0 0-7 7c0 5.2 7 13 7 13s7-7.8 7-13a7 7 0 0 0-7-7zm0 4.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5z"/></svg>',
  
  
  plane: '<svg viewBox="0 0 24 24"><path d="M21 16v-2l-8-5V3.5a1.5 1.5 0 0 0-3 0V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5z"/></svg>',
  
  
  zen: '<svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 2a8 8 0 0 1 8 8 8 8 0 0 1-8 8 8 8 0 0 1-8-8 8 8 0 0 1 8-8zM7.5 11h9v2h-9z"/></svg>',
  
  
  mobile: '<svg viewBox="0 0 24 24"><path d="M4 16h3v4H4zm5-3h3v7H9zm5-4h3v11h-3zm5-5h3v16h-3z"/></svg>',
  
  
  
  
  zenSleep: '<svg viewBox="0 0 24 24"><path d="M20.7 14.3A8.5 8.5 0 0 1 9.7 3.3 9 9 0 1 0 20.7 14.3z"/></svg>',
  zenHeart: '<svg viewBox="0 0 24 24"><path d="M12 21S3.5 15.4 3.5 9.4A4.9 4.9 0 0 1 12 6.2a4.9 4.9 0 0 1 8.5 3.2C20.5 15.4 12 21 12 21z"/></svg>',
  zenWork: '<svg viewBox="0 0 24 24"><path d="M9 4h6a2 2 0 0 1 2 2v1h3a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h3V6a2 2 0 0 1 2-2zm0 3h6V6H9z"/></svg>',
  zenDrive: '<svg viewBox="0 0 24 24"><path d="M6.5 5h11l2 6H4.5zM4 12h16a1 1 0 0 1 1 1v5h-3v-2H6v2H3v-5a1 1 0 0 1 1-1zm2.5 2a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zm11 0a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z"/></svg>',
  zenExercise: '<svg viewBox="0 0 24 24"><path d="M4 9h2v6H4zm14 0h2v6h-2zM7 7h2v10H7zm8 0h2v10h-2zM9.5 11h5v2h-5z"/></svg>',
  zenGame: '<svg viewBox="0 0 24 24"><path d="M7 7h10a5 5 0 0 1 0 10 4 4 0 0 1-2.8-1.2L13 14.6h-2l-1.2 1.2A4 4 0 0 1 7 17a5 5 0 0 1 0-10zm-1.5 3v1.5H4v2h1.5V15h2v-1.5H9v-2H7.5V10zm10 0a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0-2.4z"/></svg>',
  
  
  
  
  modus: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/><path d="M7.5 11h9v2h-9z"/></svg>',
  dim: '<svg viewBox="-7.5 0 32 32"><path fill="currentColor" d="M9.75 8.25v0.219c0 0.844-0.375 1.25-1.156 1.25s-1.125-0.406-1.125-1.25v-0.219c0-0.813 0.344-1.219 1.125-1.219s1.156 0.406 1.156 1.219zM12.063 9.25l0.156-0.188c0.469-0.688 1.031-0.781 1.625-0.344 0.625 0.438 0.719 1.031 0.25 1.719l-0.188 0.156c-0.469 0.688-1.031 0.781-1.625 0.313-0.625-0.438-0.688-0.969-0.219-1.656zM5 9.063l0.125 0.188c0.469 0.688 0.406 1.219-0.188 1.656-0.625 0.469-1.219 0.375-1.688-0.313l-0.125-0.156c-0.469-0.688-0.406-1.281 0.188-1.719 0.625-0.438 1.219-0.281 1.688 0.344zM8.594 11.125c2.656 0 4.844 2.188 4.844 4.875 0 2.656-2.188 4.813-4.844 4.813-2.688 0-4.844-2.156-4.844-4.813 0-2.688 2.156-4.875 4.844-4.875zM1.594 12.5l0.219 0.063c0.813 0.25 1.063 0.719 0.844 1.469-0.25 0.75-0.75 0.969-1.531 0.719l-0.219-0.063c-0.781-0.25-1.063-0.719-0.844-1.469 0.25-0.75 0.75-0.969 1.531-0.719zM15.375 12.563l0.219-0.063c0.813-0.25 1.313-0.031 1.531 0.719s-0.031 1.219-0.844 1.469l-0.188 0.063c-0.813 0.25-1.313 0.031-1.531-0.719-0.25-0.75 0.031-1.219 0.813-1.469zM8.594 18.688c1.469 0 2.688-1.219 2.688-2.688 0-1.5-1.219-2.719-2.688-2.719-1.5 0-2.719 1.219-2.719 2.719 0 1.469 1.219 2.688 2.719 2.688zM0.906 17.281l0.219-0.063c0.781-0.25 1.281-0.063 1.531 0.688 0.219 0.75-0.031 1.219-0.844 1.469l-0.219 0.063c-0.781 0.25-1.281 0.063-1.531-0.688-0.219-0.75 0.063-1.219 0.844-1.469zM16.094 17.219l0.188 0.063c0.813 0.25 1.063 0.719 0.844 1.469s-0.719 0.938-1.531 0.688l-0.219-0.063c-0.781-0.25-1.063-0.719-0.813-1.469 0.219-0.75 0.719-0.938 1.531-0.688zM3.125 21.563l0.125-0.188c0.469-0.688 1.063-0.75 1.688-0.313 0.594 0.438 0.656 0.969 0.188 1.656l-0.125 0.188c-0.469 0.688-1.063 0.75-1.688 0.313-0.594-0.438-0.656-0.969-0.188-1.656zM13.906 21.375l0.188 0.188c0.469 0.688 0.375 1.219-0.25 1.656-0.594 0.438-1.156 0.375-1.625-0.313l-0.156-0.188c-0.469-0.688-0.406-1.219 0.219-1.656 0.594-0.438 1.156-0.375 1.625 0.313zM9.75 23.469v0.25c0 0.844-0.375 1.25-1.156 1.25s-1.125-0.406-1.125-1.25v-0.25c0-0.844 0.344-1.25 1.125-1.25s1.156 0.406 1.156 1.25z"></path></svg>',
  rotate: '<svg viewBox="0 0 24 24"><path fill="none" d="M20.4898 14.9907C19.8414 16.831 18.6124 18.4108 16.9879 19.492C15.3635 20.5732 13.4316 21.0972 11.4835 20.9851C9.5353 20.873 7.67634 20.1308 6.18668 18.8704C4.69703 17.61 3.65738 15.8996 3.22438 13.997C2.79138 12.0944 2.98849 10.1026 3.78602 8.32177C4.58354 6.54091 5.93827 5.06746 7.64608 4.12343C9.35389 3.17941 11.3223 2.81593 13.2546 3.08779C16.5171 3.54676 18.6725 5.91142 21 8M21 8V2M21 8H15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  saver: '<svg viewBox="0 0 32 32" fill="currentColor"><path d="M18.605 2.022v0zM18.605 2.022l-2.256 11.856 8.174 0.027-11.127 16.072 2.257-13.043-8.174-0.029zM18.606 0.023c-0.054 0-0.108 0.002-0.161 0.006-0.353 0.028-0.587 0.147-0.864 0.333-0.154 0.102-0.295 0.228-0.419 0.373-0.037 0.043-0.071 0.088-0.103 0.134l-11.207 14.832c-0.442 0.607-0.508 1.407-0.168 2.076s1.026 1.093 1.779 1.099l5.773 0.042-1.815 10.694c-0.172 0.919 0.318 1.835 1.18 2.204 0.257 0.11 0.527 0.163 0.793 0.163 0.629 0 1.145-0.294 1.533-0.825l11.22-16.072c0.442-0.607 0.507-1.408 0.168-2.076-0.34-0.669-1.026-1.093-1.779-1.098l-5.773-0.010 1.796-9.402c0.038-0.151 0.057-0.308 0.057-0.47 0-1.082-0.861-1.964-1.939-1.999-0.024-0.001-0.047-0.001-0.071-0.001v0z"/></svg>',
  augenkomfort: '<svg viewBox="0 0 24 24"><path fill="none" d="M15.0007 12C15.0007 13.6569 13.6576 15 12.0007 15C10.3439 15 9.00073 13.6569 9.00073 12C9.00073 10.3431 10.3439 9 12.0007 9C13.6576 9 15.0007 10.3431 15.0007 12Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path fill="none" d="M12.0012 5C7.52354 5 3.73326 7.94288 2.45898 12C3.73324 16.0571 7.52354 19 12.0012 19C16.4788 19 20.2691 16.0571 21.5434 12C20.2691 7.94291 16.4788 5 12.0012 5Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  camera: '<svg viewBox="0 0 24 24"><circle fill="none" cx="12" cy="12" r="4" stroke="currentColor" stroke-width="2"/><path fill="none" d="M22 12C22 16.714 22 19.0711 20.5355 20.5355C19.0711 22 16.714 22 12 22C7.28595 22 4.92893 22 3.46447 20.5355C2 19.0711 2 16.714 2 12C2 7.28595 2 4.92893 3.46447 3.46447C4.92893 2 7.28595 2 12 2C16.714 2 19.0711 2 20.5355 3.46447C21.5093 4.43821 21.8356 5.80655 21.9449 8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
  comdirect: '<svg viewBox="0 0 192 192" fill="none" stroke="currentColor" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"><path fill="none" d="M164.181 144.948a74.37 73.795 0 0 1-81.836 20.19 74.37 73.795 0 0 1-48.233-68.637 74.37 73.795 0 0 1 47.384-69.217 74.37 73.795 0 0 1 82.079 19.196"/><path fill="none" d="M139.233 123.543a40.627 40.627 0 0 1-44.706 11.116A40.627 40.627 0 0 1 68.178 96.87a40.627 40.627 0 0 1 25.885-38.106 40.627 40.627 0 0 1 44.838 10.568"/><path fill="none" d="m163.604 46.52-24.732 22.788"/><path fill="none" d="m139.167 123.575 24.973 21.273"/></svg>',
  
  recording: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/><circle class="record-dot" cx="12" cy="12" r="4.5" fill="currentColor"/></svg>',
  mic: '<svg viewBox="0 0 24 24"><path fill="none" d="M12 17V21M12 21H9M12 21H15" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><rect fill="none" x="10" y="3" width="4" height="10" rx="2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path fill="none" d="M17.7378 12.7542C17.3674 13.9659 16.6228 15.0293 15.6109 15.7918C14.599 16.5544 13.3716 16.977 12.1047 16.9991C10.8378 17.0212 9.59647 16.6417 8.55854 15.9149C7.52061 15.1881 6.73941 14.1515 6.32689 12.9534" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
};








const GLYPHS_OFF = {
  mic: '<svg viewBox="0 0 24 24" fill="none"><path fill-rule="evenodd" clip-rule="evenodd" d="M9.00004 7.91421V11C9.00004 12.6569 10.3432 14 12 14C12.8503 14 13.6179 13.6463 14.1638 13.078L12.7487 11.6629C12.5655 11.8697 12.298 12 12 12C11.4478 12 11 11.5523 11 11V9.91422L9.00004 7.91421ZM13 9.08579V5C13 4.44772 12.5523 4 12 4C11.4478 4 11 4.44772 11 5V7.08579L9.00004 5.08579V5C9.00004 3.34315 10.3432 2 12 2C13.6569 2 15 3.34315 15 5V11C15 11.0283 14.9997 11.0565 14.9989 11.0846L13 9.08579ZM15.5782 14.4924C15.4023 14.6727 15.2121 14.8402 15.0091 14.9932C14.1658 15.6286 13.143 15.9808 12.0873 15.9992C12.0594 15.9997 12.0315 16 12.0036 16C10.977 16.0007 9.97424 15.6854 9.13216 15.0958C8.26722 14.4901 7.61622 13.6262 7.27245 12.6278C7.09264 12.1056 6.52356 11.8281 6.00136 12.0079C5.47917 12.1877 5.20161 12.7568 5.38141 13.279C5.86269 14.6767 6.77409 15.8862 7.98501 16.7341C8.88694 17.3656 9.92054 17.7724 11 17.9282V20H9.00004C8.44776 20 8.00004 20.4477 8.00004 21C8.00004 21.5523 8.44776 22 9.00004 22H12H15C15.5523 22 16 21.5523 16 21C16 20.4477 15.5523 20 15 20H13V17.9282C14.1618 17.7605 15.2678 17.3025 16.2127 16.5904C16.4905 16.3812 16.7509 16.1525 16.9925 15.9067L15.5782 14.4924ZM18.1876 14.2733L16.6785 12.7642C16.716 12.6648 16.7504 12.5639 16.7816 12.4619C16.943 11.9337 17.5021 11.6365 18.0302 11.7979C18.5584 11.9594 18.8556 12.5184 18.6942 13.0466C18.5639 13.4729 18.3938 13.8834 18.1876 14.2733Z" fill="currentColor"/><path d="M5 5L19 19" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
};


const glyphFor = (name, isOn) => (isOn ? GLYPHS[name] : GLYPHS_OFF[name] || GLYPHS[name]) || '';






const BATTERY_GLYPHS = {
  charging: '<svg viewBox="0 0 24 24"><path fill="none" d="M12.5 6L8.5 12H14.5L10.5 18M21 13V11M7.7 6H6.2C5.0799 6 4.51984 6 4.09202 6.21799C3.71569 6.40973 3.40973 6.71569 3.21799 7.09202C3 7.51984 3 8.0799 3 9.2V14.8C3 15.9201 3 16.4802 3.21799 16.908C3.40973 17.2843 3.71569 17.5903 4.09202 17.782C4.51984 18 5.0799 18 6.2 18H6.5M16.5 6H16.8C17.9201 6 18.4802 6 18.908 6.21799C19.2843 6.40973 19.5903 6.71569 19.782 7.09202C20 7.51984 20 8.0799 20 9.2V14.8C20 15.9201 20 16.4802 19.782 16.908C19.5903 17.2843 19.2843 17.5903 18.908 17.782C18.4802 18 17.9201 18 16.8 18H15.31" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
};









const ANNOUNCE_CELL = 'M21 13V11M7.7 6H6.2C5.0799 6 4.51984 6 4.09202 6.21799C3.71569 6.40973 3.40973 6.71569 3.21799 7.09202C3 7.51984 3 8.0799 3 9.2V14.8C3 15.9201 3 16.4802 3.21799 16.908C3.40973 17.2843 3.71569 17.5903 4.09202 17.782C4.51984 18 5.0799 18 6.2 18H6.5M16.5 6H16.8C17.9201 6 18.4802 6 18.908 6.21799C19.2843 6.40973 19.5903 6.71569 19.782 7.09202C20 7.51984 20 8.0799 20 9.2V14.8C20 15.9201 20 16.4802 19.782 16.908C19.5903 17.2843 19.2843 17.5903 18.908 17.782C18.4802 18 17.9201 18 16.8 18H15.31';
const announceGlyph = mark =>
  '<svg viewBox="0 0 24 24"><path fill="none" d="' + mark + ANNOUNCE_CELL +
  '" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';

const ANNOUNCE_GLYPHS = {
  charging: BATTERY_GLYPHS.charging,
  low: announceGlyph('M12 9.2V14.8'),
  critical: announceGlyph('M12 8.4V13M12 16.3V16.4'),
};


const ANNOUNCE_COLOURS = {
  charging: '#5bfd5b',
  low: '#ffb300',
  critical: '#ff3b30',
};

// Mirrors BatteryWatch.LOW and BatteryWatch.CRITICAL in BatteryWatch.kt, which fire the low/critical alerts at the same two marks this bubble changes colour at.
const BATTERY_LOW = 40;
const BATTERY_CRITICAL = 15;

function batteryBand(level) {
  if (level >= BATTERY_LOW) return 'ok';
  if (level >= BATTERY_CRITICAL) return 'low';
  return 'critical';
}

const BATTERY_COLOURS = { ok: ANNOUNCE_COLOURS.charging, low: ANNOUNCE_COLOURS.low, critical: ANNOUNCE_COLOURS.critical };









const MODUS_ORDER = ['hotspot', 'bluetooth', 'zen'];








const ZEN_MODES = [
  { glyph: 'zenSleep', words: ['schlaf', 'nacht', 'sleep', 'night', 'bett', 'bed'] },
  { glyph: 'zenHeart', words: ['<3', '❤', '♥', 'herz', 'liebe', 'love', 'heart'] },
  { glyph: 'zenWork', words: ['arbeit', 'work', 'büro', 'buro', 'office', 'fokus', 'focus', 'lern', 'study'] },
  { glyph: 'zenDrive', words: ['fahr', 'auto', 'driv', 'car', 'pendel', 'commut'] },
  { glyph: 'zenExercise', words: ['sport', 'training', 'workout', 'fitness', 'lauf', 'run', 'gym'] },
  { glyph: 'zenGame', words: ['spiel', 'gam', 'zock'] },
];


function zenGlyph() {
  const name = (attached && attached.zenName ? attached.zenName : '').toLowerCase();
  const found = name && ZEN_MODES.find(mode => mode.words.some(word => name.includes(word)));
  return GLYPHS[found ? found.glyph : 'zen'];
}
const MODUS_COLOUR = {
  bluetooth: '#3d8bff',
  hotspot: '#ff9b3d',
  zen: '#a07dff',
};

let attached = null;






let transfer = null;
let charge = -1;
let isPlugged = false;
let remainingMinutes = -1;
let isBorn = false;


function subject() {
  if (!attached) return 'none';
  return MODUS_ORDER.find(kind => hasModus(kind)) || attached.link;
}

function hasModus(kind) {
  if (!attached) return false;
  if (kind === 'bluetooth') return Boolean(attached.bluetooth);
  if (kind === 'hotspot') return Boolean(attached.hotspot);
  if (kind === 'zen') return Boolean(attached.zen);
  return false;
}









function wanted() {
  const shown = [];
  if (attached && attached.usb) shown.push({ name: 'usb', html: GLYPHS.usb });
  if (transfer) {
    shown.push({
      name: 'transfer',
      html: transfer.isDone ? GLYPHS.transferDone : GLYPHS[transfer.mod],
    });
  }
  const modus = MODUS_ORDER.find(kind => hasModus(kind));
  if (modus) shown.push({ name: 'modus', html: modus === 'zen' ? zenGlyph() : GLYPHS[modus] });
  if (attached && attached.link === 'wifi') {
    shown.push({ name: 'link', html: GLYPHS.wifi, level: attached.level });
  } else if (attached && attached.link === 'ethernet') {
    shown.push({ name: 'link', html: GLYPHS.ethernet });
  } else if (attached && attached.link === 'mobile') {
    
    
    
    if (attached.generation) shown.push({ name: 'link', text: attached.generation });
    else shown.push({ name: 'link', html: GLYPHS.mobile });
  }
  
  
  
  const reading = charge >= 0
    ? '<span class="own-charge" data-charge="' + chargeBand() + '">' + charge + '%</span>'
    : '';
  if (reading) shown.push({ name: 'battery', html: reading, isText: true });
  return shown.sort((one, two) => SLOTS[one.name] - SLOTS[two.name]);
}















function paintStatus() {
  if (!attached) return;
  const modus = MODUS_ORDER.find(kind => hasModus(kind));
  statusPill.classList.toggle('modal', Boolean(modus));
  root.style.setProperty('--status-accent', modus ? MODUS_COLOUR[modus] : 'transparent');
  statusPill.classList.toggle('plugged', isPlugged);
  paintBattery();
  paintModeLabels();
  if (statusOpen) paintTransferStrip();

  const shown = wanted();
  const names = shown.map(slot => slot.name);
  let hasLeft = false;
  for (const slot of [...statusClosed.children]) {
    if (names.includes(slot.dataset.slot) || slot.classList.contains('leaving')) continue;
    hasLeft = true;
    slot.classList.add('leaving');
    setTimeout(() => slot.remove(), SLOT_MOVE);
  }

  shown.forEach(want => {
    let slot = statusClosed.querySelector(`[data-slot="${want.name}"]:not(.leaving)`);
    if (!slot) {
      slot = document.createElement('div');
      slot.className = 'status-slot arriving';
      slot.dataset.slot = want.name;
      slot.appendChild(document.createElement('div')).className = 'status-slot-inner';
      
      
      const after = [...statusClosed.children].find(
        other => SLOTS[other.dataset.slot] > SLOTS[want.name]
      );
      statusClosed.insertBefore(slot, after || null);
      
      void slot.offsetWidth;
      slot.classList.remove('arriving');
    }
    const inner = slot.firstElementChild;
    const drawn = want.text ? want.text : want.html;
    if (inner.dataset.drawn !== drawn) {
      inner.dataset.drawn = drawn;
      if (want.text) inner.textContent = want.text;
      else inner.innerHTML = want.html;
    }
    inner.classList.toggle('is-text', Boolean(want.text) || Boolean(want.isText));
    if (want.level >= 0) inner.dataset.level = want.level;
    else delete inner.dataset.level;
  });

  if (hasLeft && isBorn) {
    statusPill.classList.add('settling');
    setTimeout(() => statusPill.classList.remove('settling'), SLOT_BOUNCE);
  }
  fitStatusProxy();
  stirLiquid(SLOT_MOVE + 200);
}



















const CHARGE_POP_STAGGER = 34;
const CHARGE_POP_SHAKE = 150;
const CHARGE_POP_COLLAPSE = 190;


const CHARGE_POP_IN_STAGGER = 42;
const CHARGE_POP_IN = 460;
const CHARGE_POP_IN_TILT = 14;


const CHARGE_DWELL = 2600;


const CHARGE_WIDTH = 86;
root.style.setProperty('--status-charge-width', CHARGE_WIDTH + 'px');

let chargeDwell = null;
let isAnnouncing = false;

let announcingState = null;

export function announceCharge(state = 'charging') {
  
  
  if (!isBorn || statusOpen) return;
  clearTimeout(chargeDwell);
  
  
  root.style.setProperty('--status-announce', ANNOUNCE_COLOURS[state] || ANNOUNCE_COLOURS.charging);
  
  
  if (!isAnnouncing) {
    isAnnouncing = true;
    announcingState = state;
    const slots = [...statusClosed.children];
    slots.forEach((slot, index) => {
      slot.style.setProperty('--pop-at', index * CHARGE_POP_STAGGER + 'ms');
      slot.classList.add('popping');
    });
    const emptyAt = slots.length * CHARGE_POP_STAGGER + CHARGE_POP_SHAKE;
    setTimeout(() => {
      if (!isAnnouncing) return;
      
      
      statusFaces.charge.innerHTML =
        ANNOUNCE_GLYPHS[announcingState] || ANNOUNCE_GLYPHS.charging;
      statusPill.classList.add('announcing');
      showStatusFace('charge');
      bridge.triggerHaptic('notification');
      fitStatusProxy();
      stirLiquid(CHARGE_POP_COLLAPSE + 400);
    }, emptyAt);
    stirLiquid(emptyAt + CHARGE_POP_COLLAPSE + 400);
    chargeDwell = setTimeout(retireCharge, emptyAt + CHARGE_DWELL);
    return;
  }
  
  
  
  
  
  
  if (state !== announcingState) {
    announcingState = state;
    statusFaces.charge.innerHTML = ANNOUNCE_GLYPHS[state] || ANNOUNCE_GLYPHS.charging;
    bridge.triggerHaptic('notification');
  }
  chargeDwell = setTimeout(retireCharge, CHARGE_DWELL);
}







function retireCharge() {
  if (!isAnnouncing) return;
  isAnnouncing = false;
  announcingState = null;
  statusPill.classList.remove('announcing');
  showStatusFace('closed');
  statusClosed.replaceChildren();
  paintStatus();
  
  
  [...statusClosed.children].forEach((slot, index) => {
    const tilt = (Math.random() * 0.6 + 0.4) * CHARGE_POP_IN_TILT * (Math.random() < 0.5 ? -1 : 1);
    slot.style.setProperty('--pop-at', index * CHARGE_POP_IN_STAGGER + 'ms');
    slot.style.setProperty('--pop-tilt', tilt + 'deg');
    slot.classList.add('popping-in');
    setTimeout(() => slot.classList.remove('popping-in'), index * CHARGE_POP_IN_STAGGER + CHARGE_POP_IN);
  });
  stirLiquid(statusClosed.children.length * CHARGE_POP_IN_STAGGER + CHARGE_POP_IN + 200);
}


export function fitStatusProxy() {
  if (!isBorn) {
    bridge.setStatusProxy(0, 0, 0);
    return;
  }
  
  
  
  
  
  if (statusOpen) {
    bridge.setStatusProxy(
      
      STATUS_PANEL.width, STATUS_PANEL.height + GROWN_PAD, Math.round(statusPanelLeft())
    );
    return;
  }
  
  
  
  
  
  
  
  
  
  if (statusPill.classList.contains('panel-closing')) {
    bridge.setStatusProxy(0, 0, 0);
    return;
  }
  const box = statusPill.getBoundingClientRect();
  if (!box.width) {
    bridge.setStatusProxy(0, 0, 0);
    return;
  }
  bridge.setStatusProxy(Math.round(box.width), Math.round(box.height), Math.round(box.left));
}







function bornStatus() {
  if (isBorn) return;
  isBorn = true;
  const box = statusPill.getBoundingClientRect();
  const home = screenWidth() / 2 - (box.left + box.width / 2);
  root.style.setProperty('--status-fly', Math.round(home) + 'px');
  root.style.setProperty('--status-pop', 0.5);
  requestAnimationFrame(() => {
    statusPill.classList.add('lit');
    root.style.setProperty('--status-fly', '0px');
    root.style.setProperty('--status-pop', 1);
  });
  setTimeout(() => {
    bridge.triggerHaptic('tap');
    fitStatusProxy();
  }, STATUS_TRAVEL * STATUS_LAND);
  stirLiquid(STATUS_TRAVEL + 300);
}


export function statusHolds(x, y) {
  if (!isBorn) return false;
  
  
  
  
  
  if (statusPill.classList.contains('panel-closing')) return false;
  const box = statusPill.getBoundingClientRect();
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
}



export let statusOpen = false;

/* The actions face is absolutely positioned and so contributes no width of its own: the open size is a number rather than something the contents can be asked for. */
const STATUS_CORNER_WIDE = 96;
const CLOCK_CORNER_LEAVE = 260;
const clockDate = document.getElementById('clock-date');

let cornerIdleWide = 0;
let clockIdleWide = 0;
let clockCornerWide = 0;

function ownsClockWidth() {
  return !clockPill.classList.contains('now-live');
}

/* The two corner bubbles grew by transitioning `width: auto` under interpolate-size, which eases the box but not what is being laid out inside it: the pair arrived at their new type size in the first frame and the box caught up around them. They are measured and driven in pixels now, the way the Now bubble's own growth already was. */
function pinCornerWidths() {
  cornerIdleWide = Math.round(statusPill.getBoundingClientRect().width);
  root.style.setProperty('--status-width-ms', '0ms');
  root.style.setProperty('--status-width', cornerIdleWide + 'px');
  if (!ownsClockWidth()) return;
  clockIdleWide = Math.round(clockPill.getBoundingClientRect().width);
  /* The corner width was the idle one plus a fixed allowance, which the date at its corner size ran straight out of. It is asked of the grown box itself instead, with the class on and the width back at auto for one uncommitted layout. */
  clockPill.classList.add('corner-grow');
  root.style.setProperty('--clock-width', 'auto');
  /* Floored at the Status bubble's own corner width: the two stand at opposite ends of the same line and one of them being narrower reads as the pair not matching rather than as one holding less. */
  clockCornerWide = Math.max(STATUS_CORNER_WIDE, Math.ceil(clockPill.getBoundingClientRect().width));
  clockPill.classList.remove('corner-grow');
  root.style.setProperty('--clock-width-ms', '0ms');
  root.style.setProperty('--clock-width', clockIdleWide + 'px');
}

function growCornerWidths() {
  root.style.setProperty('--status-width-ms', STATUS_PANEL.ms + 'ms');
  root.style.setProperty('--status-width', STATUS_CORNER_WIDE + 'px');
  if (!ownsClockWidth()) return;
  root.style.setProperty('--clock-width-ms', STATUS_PANEL.ms + 'ms');
  root.style.setProperty('--clock-width', clockCornerWide + 'px');
}

function shrinkCornerWidths() {
  root.style.setProperty('--status-width-ms', STATUS_PANEL.ms + 'ms');
  root.style.setProperty('--status-width', cornerIdleWide + 'px');
  if (!ownsClockWidth()) return;
  root.style.setProperty('--clock-width-ms', STATUS_PANEL.ms + 'ms');
  root.style.setProperty('--clock-width', clockIdleWide + 'px');
}

function releaseCornerWidths() {
  root.style.removeProperty('--status-width');
  root.style.removeProperty('--status-width-ms');
  if (!ownsClockWidth()) return;
  root.style.removeProperty('--clock-width');
  root.style.removeProperty('--clock-width-ms');
}

/* Left in flow the date collapsed its own max-width and its margin as the box retracted around it, so it travelled twice on the way out. It is held at the offset it already stood at inside the Clock and only fades; the Clock wears `overflow: hidden` for the same stretch, so whatever the shrinking box stops covering is cut rather than pushed. */
function pinCornerDate() {
  const box = clockDate.getBoundingClientRect();
  if (!box.width) return;
  const around = clockPill.getBoundingClientRect();
  clockDate.style.left = Math.round(box.left - around.left) + 'px';
  clockDate.style.top = Math.round(box.top - around.top) + 'px';
  clockDate.classList.add('handed-over');
  clockPill.classList.add('corner-leaving');
  setTimeout(() => {
    clockPill.classList.remove('corner-leaving');
    clockDate.classList.remove('handed-over');
    clockDate.style.removeProperty('left');
    clockDate.style.removeProperty('top');
  }, CLOCK_CORNER_LEAVE + 60);
}

function showStatusFace(name) {
  Object.entries(statusFaces).forEach(([key, face]) => {
    face.classList.toggle('showing', key === name);
  });
}


function statusPanelLeft() {
  return (screenWidth() - STATUS_PANEL.width) / 2;
}







const quickPanel = document.getElementById('quick-panel');

// The row runs on forever, and what makes it run is three copies of the nine standing end to end: the shift wraps by one copy's width, so a flick never reaches an end and never has to be told it has. The copies are made here, before quickBubbles is captured below, so every copy is ranked and staggered like the original rather than reading --pop-rank and --pop-from as unset.
const KNOB_COPIES = 3;

const knobRow = document.getElementById('quick-row-knobs');
const knobOriginals = [...knobRow.children];
for (let copy = 1; copy < KNOB_COPIES; copy += 1) {
  knobOriginals.forEach(knob => knobRow.appendChild(knob.cloneNode(true)));
}

const quickBubbles = [...quickPanel.querySelectorAll('.quick-bubble')];















const QUICK_RANK_STEPS = 6;

// Share of a module's own growth that has run before the next band sets off: the centre starts when the edge is 70% of the way home, so the stagger is a fraction of the pop rather than a number standing beside it.
const QUICK_OVERLAP = 0.7;

const QUICK_PULL = 240;

// The centre of the panel has no distance from the centre to be thrown across, so without a floor it has no journey at all and arrives as a bare scale. Every module travels at least this far along its own side.
const QUICK_PULL_FLOOR = 56;

const QUICK_JITTER = 50;


const QUICK_DROP_STAGGER = 20;


const QUICK_POP = 360;

// Written onto the panel as --pop-step rather than mirrored into pill.css: the gap between two bands is derived from the pop, and a second copy of it could only ever disagree.
const QUICK_STAGGER = Math.round(QUICK_POP * QUICK_OVERLAP / QUICK_RANK_STEPS);






const SCRIM_FROST_MS = 450;








function rankQuickBubbles() {
  quickPanel.style.setProperty('--pop-step', QUICK_STAGGER + 'ms');
  const panelBox = quickPanel.getBoundingClientRect();
  const panelCenter = panelBox.top + panelBox.height / 2;
  const reach = panelBox.height / 2 || 1;
  for (const bubble of quickBubbles) {
    const box = bubble.getBoundingClientRect();
    const fromCenter = Math.max(-1, Math.min(1, (box.top + box.height / 2 - panelCenter) / reach));
    const edgeness = Math.abs(fromCenter);
    const rank = Math.round((1 - edgeness) * QUICK_RANK_STEPS);
    bubble.style.setProperty('--pop-rank', rank);
    bubble.style.setProperty('--drop-rank', QUICK_RANK_STEPS - rank);
    const side = fromCenter < 0 ? -1 : 1;
    bubble.style.setProperty('--pop-from', Math.round(fromCenter * QUICK_PULL + side * QUICK_PULL_FLOOR) + 'px');
  }
  for (const connector of quickPanel.querySelectorAll('.quick-mode')) {
    connector.style.setProperty('--pop-jitter', Math.round(Math.random() * QUICK_JITTER) + 'ms');
  }
}


let panelSettle = null;

let quickSettle = null;

let quickEntering = null;
let readingReveal = null;
let readingsHeld = false;

export function openStatusPanel() {
  if (statusOpen) return;
  
  
  
  
  clearTimeout(panelSettle);
  clearTimeout(quickSettle);
  statusPill.classList.remove('panel-closing');
  
  
  clearTimeout(chargeDwell);
  retireCharge();
  
  
  
  if (shared.state === 'extended') toClosed();
  if (nowOpen) closeNowPanel();
  
  
  fitStatusPanel();


  statusOpen = true;
  // The steal has to be on before Main is repainted: raiseMain asks closedFace(), which asks liveMods(), and with the Now bubble not yet holding the mod that answer was the mod's own face — so Main rose still wearing the thing that was being taken off it.
  enterPanelNow();
  raiseMain();
  paintTransferStrip();
  paintVitals();
  bridge.requestVitals();
  bridge.requestWeather();


  bridge.requestToggles();



  root.style.setProperty('--liquid-tall', (STATUS_PANEL.height + GROWN_PAD + 24) + 'px');







  if (!statusDownAt) fitStatusProxy();



  pinCornerWidths();
  statusPill.classList.add('open');
  showStatusFace('actions');
  clockPill.classList.add('corner-grow');
  requestAnimationFrame(growCornerWidths);

  quickPanel.classList.remove('leaving');
  
  
  rankQuickBubbles();
  
  
  quickPanel.classList.add('showing');
  quickPanel.classList.add('entering');
  clearTimeout(quickEntering);
  quickEntering = setTimeout(() => quickPanel.classList.remove('entering'), QUICK_RANK_STEPS * QUICK_STAGGER + QUICK_JITTER + QUICK_POP);
  
  // A reading that swept while its bubble was still dropping was a bar moving under a box that was itself moving — the reveal is asked for once the quick panel has landed instead, over the text it is already standing on. Until then the reading is held out of sight and any answer arriving early only writes its text, so a weather reply landing mid-drop no longer plays a reveal of its own before the real one.
  clearTimeout(readingReveal);
  readingsHeld = true;
  weatherReading.classList.add('sweep-hidden');
  transferReading.classList.add('sweep-hidden');
  readingReveal = setTimeout(() => {
    readingsHeld = false;
    revealLabel(weatherReading);
    revealLabel(transferReading);
  }, QUICK_RANK_STEPS * QUICK_STAGGER + QUICK_JITTER + QUICK_POP);
  
  
  stirLiquid(STATUS_PANEL.ms + QUICK_RANK_STEPS * QUICK_STAGGER + QUICK_JITTER + QUICK_POP);
}

export function closeStatusPanel() {
  if (!statusOpen) return;
  statusOpen = false;
  clearTimeout(readingReveal);
  readingsHeld = false;
  weatherReading.classList.remove('sweep-hidden');
  transferReading.classList.remove('sweep-hidden');
  
  
  
  
  statusPill.classList.add('panel-closing');
  showStatusFace('closed');
  
  
  quickPanel.classList.remove('showing');
  clearTimeout(quickEntering);
  quickPanel.classList.remove('entering');
  quickPanel.classList.add('leaving');
  
  
  pinCornerDate();
  statusPill.classList.remove('open');
  clockPill.classList.remove('corner-grow');
  shrinkCornerWidths();
  pill.classList.remove('panel-top');
  quickPanel.classList.remove('level-solo');
  leavePanelNow();
  showFace(closedFace());
  applyClosedWindow();

  fitStatusProxy();
  
  const dropped = QUICK_RANK_STEPS * QUICK_DROP_STAGGER + 180;
  
  
  
  
  stirLiquid(Math.max(STATUS_PANEL.ms, dropped, SCRIM_FROST_MS) + 160);
  panelSettle = setTimeout(() => {
    
    
    
    statusPill.classList.remove('panel-closing');
    releaseCornerWidths();
    fitStatusProxy();
    
    
    root.style.removeProperty('--liquid-tall');
  }, Math.max(STATUS_PANEL.ms, dropped) + 40);
  
  
  
  
  
  quickSettle = setTimeout(() => quickPanel.classList.remove('leaving'), dropped + 40);
}







statusPill.addEventListener('click', () => {
  if (!statusOpen) return;
  bridge.triggerHaptic('tap');
  closeStatusPanel();
});











// RING_RADIUS mirrors half of --quick-grid-icon in pill.css: the ring is the button's border now, so the characters and the dots have to sit exactly on the icon's edge, and there is no build step joining the two files.
const RING_RADIUS = 30;
// RING_CENTER mirrors half the ring svg viewBox in pill.html: the viewBox used to be centred on zero, and Blink resolves `transform-box: view-box` from the viewport corner rather than from the viewBox origin, so the slow turn swung the whole reading around a point 39 units off the button. The box starts at zero now and every group is placed from this centre by hand.
const RING_CENTER = 39;
const RING_ADVANCE = 5.6;
const RING_GAP = 3;
const RING_DOTS = 20;
const RING_BURST_MS = 420;
// RING_SWAP_OUT_MS and RING_SWAP_IN_MS mirror the ring-char-out and ring-char-in durations plus their per-character delays in pill.css: the new reading is only drawn once the old one has finished collapsing, and there is no build step joining the two files.
const RING_SWAP_OUT_MS = 340;
const RING_SWAP_IN_MS = 260;

function drawRing(ring, reading) {
  const characters = [...reading];
  const text = ring.querySelector('.ring-text');
  if (!characters.length) {
    text.innerHTML = '';
    return;
  }
  const slots = Math.round((2 * Math.PI * RING_RADIUS) / RING_ADVANCE);
  const run = characters.length + RING_GAP;
  const copies = Math.max(1, Math.floor(slots / run));
  const step = 360 / (copies * run);
  const marks = [];
  for (let copy = 0; copy < copies; copy += 1) {
    characters.forEach((character, index) => {
      const turn = ((copy * run + index) * step).toFixed(2);
      const glyph = character === ' ' ? '&#160;' : character;
      marks.push(`<g transform="translate(${RING_CENTER} ${RING_CENTER}) rotate(${turn}) translate(0 ${-RING_RADIUS})"><text class="ring-char" style="--index:${index}">${glyph}</text></g>`);
    });
  }
  text.innerHTML = marks.join('');
}

function drawDots(ring) {
  const dots = [];
  for (let index = 0; index < RING_DOTS; index += 1) {
    const turn = ((index * 360) / RING_DOTS).toFixed(2);
    dots.push(`<g transform="translate(${RING_CENTER} ${RING_CENTER}) rotate(${turn}) translate(0 ${-RING_RADIUS})"><circle class="ring-dot" r="1.6" style="--index:${index}"/></g>`);
  }
  ring.querySelector('.ring-dots').innerHTML = dots.join('');
}

const quickModes = [...document.querySelectorAll('.quick-mode')];
const quickKnobs = [...document.querySelectorAll('.quick-knob')];


const faceOf = control => control.querySelector('.quick-icon') || control;

[...quickModes, ...quickKnobs].forEach(control => {
  faceOf(control).innerHTML = glyphFor(control.dataset.toggle, false);
  const ring = control.querySelector('.quick-ring');
  if (ring) drawDots(ring);
});






function paintToggles(state) {
  toggles = state;
  quickModes.forEach(button => {
    const name = button.dataset.toggle;
    setControl(button, Boolean(state[name]), false);
    
    
    
    if (name !== 'usb' && name !== 'hotspot') button.classList.toggle('unavailable', !(name in state));
  });
  paintModeLabels();
  paintLevels(state);
  quickKnobs.forEach(knob => {
    if (knob.dataset.open) return;
    const name = knob.dataset.toggle;
    const value = name === 'recording' ? isRecordingLive() : state[name];
    setControl(knob, Boolean(value), false);
    
    
    knob.classList.toggle('unavailable', !(name in state) && name !== 'recording');
  });
  
  batteryBox.classList.toggle('saving', Boolean(state.saver));
}







let toggles = {};












const PAIRED_SWAP_MS = 5000;
let pairedTurn = 0;
let pairedSwap = null;

function holdPairedSwap(count) {
  if (count > 1) {
    if (!pairedSwap) {
      pairedSwap = setInterval(() => {
        pairedTurn += 1;
        paintModeLabels();
      }, PAIRED_SWAP_MS);
    }
    return;
  }
  clearInterval(pairedSwap);
  pairedSwap = null;
  pairedTurn = 0;
}

const pairedChargeBadge = document.querySelector('.quick-mode[data-toggle="bluetooth"] .quick-charge');

function pairedChargeBand(level) {
  if (level >= 31) return 'ok';
  if (level >= 11) return 'low';
  return 'critical';
}

function paintPairedCharge(level) {
  pairedChargeBadge.textContent = level >= 0 ? level : '';
  pairedChargeBadge.dataset.charge = level >= 0 ? pairedChargeBand(level) : '';
}

function paintModeLabels() {
  const paired = attached && attached.bluetooth;
  const pairedList = (paired && paired.names) || (paired && paired.name ? [paired.name] : []);
  const pairedShown = pairedList.length ? pairedList[pairedTurn % pairedList.length] : '';
  holdPairedSwap(pairedList.length);
  paintPairedCharge(paired && pairedList.length === 1 && paired.charge >= 0 ? paired.charge : -1);
  const text = {
    
    
    usb: !(attached && attached.usb) ? 'no cable' : toggles.usb ? 'file transfer' : 'charging only',
    bluetooth: paired ? (pairedShown || 'connected') : toggles.bluetooth ? 'nothing paired' : '',
    wifi: attached && attached.link === 'wifi' && attached.ssid
      ? attached.ssid
      : toggles.wifi ? 'not connected' : '',
    
    
    mobile: !toggles.mobile ? '' : (attached && attached.generation) || 'standby',
    
    
    hotspot: attached && attached.hotspot ? 'hotspot' : toggles.hotspot ? 'nobody joined' : '',
    
    
    plane: toggles.plane ? 'offline' : '',
    
    
    gps: toggles.gps ? 'gps location' : '',
  };
  const waiting = {
    bluetooth: 'nothing paired',
    wifi: 'not connected',
    hotspot: 'nobody joined',
    mobile: 'standby',
  };
  quickModes.forEach(button => {
    const name = button.dataset.toggle;


    const ring = button.querySelector('.quick-ring');
    if (ring) {
      const reading = text[name] || '';
      const isWaiting = Boolean(reading) && reading === waiting[name];
      const wasWaiting = button.classList.contains('searching');
      button.classList.toggle('searching', isWaiting);
      const written = isWaiting ? '' : reading;
      if (button.dataset.ring !== written) {
        const wasReading = button.dataset.ring;
        button.dataset.ring = written;
        if (wasReading && written) {
          button.classList.add('swapping');
          window.setTimeout(() => {
            button.classList.remove('swapping');
            button.classList.add('swapped');
            drawRing(ring, written);
            window.setTimeout(() => button.classList.remove('swapped'), RING_SWAP_IN_MS);
          }, RING_SWAP_OUT_MS);
        } else {
          drawRing(ring, written);
        }
      }
      if (wasWaiting && !isWaiting) {
        button.classList.add('found');
        window.setTimeout(() => button.classList.remove('found'), RING_BURST_MS);
      }
    }
    
    
    
    if (name === 'usb') {
      button.classList.toggle('attached', Boolean(attached && attached.usb));
    }
  });
  
  
  
  
  
  const hotspot = quickModes.find(button => button.dataset.toggle === 'hotspot');
  if (hotspot && attached) setControl(hotspot, Boolean(attached.hotspot), false);
}


function isRecordingLive() {
  
  
  return document.getElementById('clock').classList.contains('recording');
}



const batteryBox = document.getElementById('quick-battery');
const batteryGlyphBox = document.getElementById('battery-glyph');
const batteryReading = document.getElementById('battery-reading');
const batteryRemaining = document.getElementById('battery-remaining');









function chargeColour() {
  if (isPlugged) return ANNOUNCE_COLOURS.charging;
  return BATTERY_COLOURS[batteryBand(charge)];
}







const chargeBand = () => batteryBand(charge);

function formatRemaining(minutes) {
  if (minutes < 0) return '';
  if (minutes < 1) return '<1m';
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return hours > 0 ? hours + 'h ' + mins + 'm' : mins + 'm';
}

function paintBattery() {


  batteryGlyphBox.innerHTML = isPlugged ? BATTERY_GLYPHS.charging : '';
  batteryReading.textContent = charge >= 0 ? charge + '%' : '--';
  batteryRemaining.textContent = isPlugged ? formatRemaining(remainingMinutes) : '';
  batteryBox.style.setProperty('--charge-width', Math.max(0, charge) + '%');
  batteryBox.style.setProperty('--charge-color', chargeColour());
  
  batteryBox.classList.toggle('low', !isPlugged && charge >= 0 && batteryBand(charge) === 'critical');
  batteryBox.classList.toggle('charging', isPlugged);
}


const saverSwitch = () => document.querySelector('.quick-knob[data-toggle=saver]');

let batteryHeld = false;
let batteryHoldTimer = 0;

batteryBox.addEventListener('touchstart', () => {
  batteryHeld = false;
  batteryBox.classList.add('pressing');
  
  batteryHoldTimer = setTimeout(() => {
    batteryHeld = true;
    batteryBox.classList.remove('pressing');
    bridge.triggerHaptic('expand');
    bridge.openConnectionSettings('battery');
    closeStatusPanel();
  }, HOLD_MILLIS);
}, { passive: true });

['touchend', 'touchcancel'].forEach(type => {
  batteryBox.addEventListener(type, () => {
    clearTimeout(batteryHoldTimer);
    batteryBox.classList.remove('pressing');
  }, { passive: true });
});

batteryBox.addEventListener('click', event => {
  event.stopPropagation();
  
  if (batteryHeld) return;
  pressToggle('saver', saverSwitch());
  batteryBox.classList.toggle('saving', saverSwitch().classList.contains('on'));
});

window.onTogglesChanged = paintToggles;











const LEVEL_GLYPHS = {
  brightness: '<svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="4" stroke="currentColor" stroke-width="2"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M19.1 4.9l-1.4 1.4M6.3 17.7l-1.4 1.4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
  volume: '<svg viewBox="0 0 24 24"><path d="M11 4.5 6.5 8.5H3.5v7h3l4.5 4zM15.4 8.6a4.8 4.8 0 0 1 0 6.8l1.4 1.4a6.8 6.8 0 0 0 0-9.6zM18.2 5.8a8.8 8.8 0 0 1 0 12.4l1.4 1.4a10.8 10.8 0 0 0 0-15.2z"/></svg>',
};

const levels = [...document.querySelectorAll('.level')];
levels.forEach(level => {
  level.querySelector('.level-icon').innerHTML = LEVEL_GLYPHS[level.dataset.level];
});


const LEVEL_SLOP = 8;


const LEVEL_STEP = 2;





function paintLevel(level, share) {
  level.style.setProperty('--level', share + '%');
  level.classList.toggle('lit', share > 0);
}

levels.forEach(level => {
  const name = level.dataset.level;
  let from = null;
  let share = 0;
  let sent = -1;
  let levelDrag = dragGate();

  level.addEventListener('touchstart', event => {
    event.stopPropagation();
    from = event.touches[0].clientY;
    levelDrag = dragGate();
    level.classList.add('holding');
    share = parseFloat(level.style.getPropertyValue('--level')) || 0;
    sent = share;
    clearTimeout(soloTimer);
    soloTimer = setTimeout(() => enterSolo(level), SOLO_HOLD);
  }, { passive: true });

  level.addEventListener('touchmove', event => {
    if (from === null) return;
    event.stopPropagation();
    const travelled = from - event.touches[0].clientY;
    const isDragging = levelDrag(event.touches[0].clientX, event.touches[0].clientY);
    if (!level.classList.contains('dragging')) {
      if (Math.abs(travelled) < LEVEL_SLOP || !isDragging) return;
      level.classList.add('dragging');
      // The drag used to cancel the hold that merges the bars, which put the merge behind a finger held perfectly still for 420ms — a slider is grabbed and moved, so that gesture is not one anybody makes and the merge was unreachable. Taking the bar is what merges it, whether the finger then travels or waits.
      clearTimeout(soloTimer);
      enterSolo(level);
    }
    
    
    const next = Math.round(share + (travelled / level.getBoundingClientRect().height) * 100);
    const clamped = Math.max(0, Math.min(100, next));
    paintLevel(level, clamped);
    if (Math.abs(clamped - sent) < LEVEL_STEP && clamped !== 0 && clamped !== 100) return;
    sent = clamped;
    bridge.setLevel(name, clamped);
  }, { passive: true });

  ['touchend', 'touchcancel'].forEach(type => {
    level.addEventListener(type, () => {
      from = null;
      // The 2% gate above lets the last few percent of a drag go unsent, which left the panel showing one number and the phone standing at another, so the value under the finger is always sent again on release.
      const settled = Math.round(parseFloat(level.style.getPropertyValue('--level')) || 0);
      if (level.classList.contains('dragging') && settled !== sent) {
        sent = settled;
        bridge.setLevel(name, settled);
      }
      level.classList.remove('dragging', 'holding');
      clearTimeout(soloTimer);
      leaveSolo();
    }, { passive: true });
  });

  
  
  
  level.addEventListener('click', event => event.stopPropagation());
});


function paintLevels(state) {
  levels.forEach(level => {
    const value = state[level.dataset.level];
    
    
    if (typeof value === 'number' && !level.classList.contains('dragging')) paintLevel(level, value);
  });
}






const KNOB_TURN = 460;






const turnTimers = new WeakMap();

function setControl(control, isOn, isTurning) {
  const wasOn = control.classList.contains('on');
  control.classList.toggle('on', isOn);
  // Each turn armed its own timer to swap the glyph half a rotation later, so tapping faster than a rotation left several of them queued and the last to fire wrote whichever face it had been told about, not the one the control had settled on.
  clearTimeout(turnTimers.get(control));
  if (!isTurning) {
    if (wasOn !== isOn) faceOf(control).innerHTML = glyphFor(control.dataset.toggle, isOn);
    return;
  }
  
  
  
  control.classList.remove('turning');
  void control.offsetWidth;
  control.classList.add('turning');
  // The ring carries the same turn as the face rather than sharing the face's animation: `.ring-text` and the dots are already animating, and a second rule on one element does not add to the first, it replaces it. Two elements, two rules, and what is seen is the sum.
  const ring = control.querySelector('.quick-ring');
  if (ring) {
    ring.animate(
      [{ rotate: '0deg', scale: 0.55 }, { rotate: '360deg', scale: 1 }],
      { duration: KNOB_TURN, easing: 'cubic-bezier(0.34, 1.56, 0.64, 1)' }
    );
  }
  turnTimers.set(control, setTimeout(() => {
    faceOf(control).innerHTML = glyphFor(control.dataset.toggle, control.classList.contains('on'));
  }, KNOB_TURN / 2));
}

function pressToggle(name, element) {
  const next = !element.classList.contains('on');
  setControl(element, next, true);
  
  
  
  
  bridge.triggerHaptic('toggle');
  bridge.setToggle(name, next);
}

const HOLD_OPENS = {
  gps: 'com.life360.android.safetymapd',
};

let modeHeld = false;
let modeHoldTimer = 0;
let modeDownAt = null;

function releaseModeHold() {
  modeDownAt = null;
  clearTimeout(modeHoldTimer);
}

quickModes.forEach(control => {
  control.addEventListener('touchstart', event => {
    modeHeld = false;
    // Every forwarded move is dispatched whether or not the finger travelled, so cancelling the hold on the first of them let the timer expire only under a hand that never moved a pixel — which is no hand.
    modeDownAt = { x: event.changedTouches[0].clientX, y: event.changedTouches[0].clientY };
    clearTimeout(modeHoldTimer);
    modeHoldTimer = setTimeout(() => {
      modeHeld = true;
      const name = control.dataset.toggle;
      bridge.triggerHaptic('expand');
      if (HOLD_OPENS[name]) bridge.openApp(HOLD_OPENS[name]);
      else bridge.openConnectionSettings(name);
      closeStatusPanel();
    }, HOLD_MILLIS);
  }, { passive: true });
  control.addEventListener('touchmove', event => {
    if (!modeDownAt) return;
    const touch = event.changedTouches[0];
    if (Math.hypot(touch.clientX - modeDownAt.x, touch.clientY - modeDownAt.y) > KNOB_DRAG_SLOP) releaseModeHold();
  }, { passive: true });
  control.addEventListener('touchend', releaseModeHold, { passive: true });
  control.addEventListener('touchcancel', releaseModeHold, { passive: true });
});

[...quickModes, ...quickKnobs].forEach(control => {
  control.addEventListener('click', event => {
    event.stopPropagation();
    if (control.classList.contains('unavailable') || knobSwiped || modeHeld) return;
    if (control.dataset.open) {
      bridge.triggerHaptic('tap');
      bridge.openApp(control.dataset.open);
      closeStatusPanel();
      return;
    }
    pressToggle(control.dataset.toggle, control);
  });
});

// The nine toggles are one row inside a window five wide, and the four beyond either edge are reached by dragging it. Nothing here can scroll: the panel is driven by touches the host synthesises against a proxy, and a synthetic touch moves no scroll box, so the row is carried by hand. The drag also has to disarm whichever toggle the finger came down on, because the bridge only withholds its click for a travel shorter than a tap's own slop and a short drag would otherwise flip a switch as well as move the row.
const KNOB_DRAG_SLOP = 8;

// Pixels per millisecond a flick is allowed to leave behind, and how much of that survives each frame of the glide.
const KNOB_FLING_CAP = 4;
const KNOB_FRICTION = 0.94;
const KNOB_STILL = 0.02;

const knobWindow = document.getElementById('quick-knob-window');

let knobFrom = null;
let knobStart = 0;
let knobShift = 0;
let knobSwiped = false;
let knobLast = null;
let knobSpeed = 0;
let knobGlide = 0;
let knobGlideAt = 0;
let knobDrag = dragGate();

function setKnobShift(value) {
  const cycle = knobRow.scrollWidth / KNOB_COPIES;
  let shift = cycle > 0 ? value % cycle : value;
  if (shift > 0) shift -= cycle;
  knobShift = shift;
  knobRow.style.setProperty('--knob-shift', shift.toFixed(1) + 'px');
}

// The row's width changes with the toggles when the Now bubble opens, and the wrap is measured off that width. Watching the row re-wraps it on every frame of that transition rather than on a duration this file would have to keep in step with the CSS.
new ResizeObserver(() => setKnobShift(knobShift)).observe(knobRow);

function stopKnobGlide() {
  cancelAnimationFrame(knobGlide);
  knobGlide = 0;
  knobSpeed = 0;
}

function glideKnobs(at) {
  const step = Math.min(32, at - knobGlideAt);
  knobGlideAt = at;
  setKnobShift(knobShift + knobSpeed * step);
  knobSpeed *= Math.pow(KNOB_FRICTION, step / 16);
  if (Math.abs(knobSpeed) < KNOB_STILL) {
    stopKnobGlide();
    return;
  }
  knobGlide = requestAnimationFrame(glideKnobs);
}

knobRow.addEventListener('touchstart', event => {
  stopKnobGlide();
  knobFrom = event.touches[0].clientX;
  knobStart = knobShift;
  knobSwiped = false;
  knobDrag = dragGate();
  knobLast = { x: knobFrom, at: performance.now() };
}, { passive: true });

knobRow.addEventListener('touchmove', event => {
  if (knobFrom === null) return;
  const x = event.touches[0].clientX;
  if (!knobSwiped && (Math.abs(x - knobFrom) < KNOB_DRAG_SLOP || !knobDrag(x, event.touches[0].clientY))) return;
  // The slop is spent before the row starts moving, so it is taken off the travel rather than jumped over — otherwise the row leaps the whole slop the moment a drag is recognised.
  if (!knobSwiped) {
    knobSwiped = true;
    knobStart = knobShift - (x - knobFrom);
  }
  const at = performance.now();
  const span = at - knobLast.at;
  if (span > 0) knobSpeed = (x - knobLast.x) / span;
  knobLast = { x, at };
  setKnobShift(knobStart + (x - knobFrom));
}, { passive: true });

function releaseKnobs() {
  if (knobFrom === null) return;
  knobFrom = null;
  if (!knobSwiped) return;
  knobSpeed = Math.max(-KNOB_FLING_CAP, Math.min(KNOB_FLING_CAP, knobSpeed));
  if (Math.abs(knobSpeed) < KNOB_STILL) return;
  knobGlideAt = performance.now();
  knobGlide = requestAnimationFrame(glideKnobs);
}

knobRow.addEventListener('touchend', releaseKnobs, { passive: true });
knobRow.addEventListener('touchcancel', releaseKnobs, { passive: true });



let statusHeld = false;
let statusDownAt = null;
let statusHoldTimer = 0;
let statusPulled = false;

let statusDrop = 0;
























// Every tap inside the open panel used to close it: the proxy resolves a touch against the Status bubble's box, and nothing in the panel is a descendant of that bubble, so each control's press fell back to the bubble itself, which reads a press as "close". The panel resolves its own targets now, and answers with nothing where a tap really is meant to close.
const QUICK_GRACE = 26;

const QUICK_CONTROLS = '.quick-mode, .quick-knob, .level, #quick-battery, #quick-vitals';

export function statusPanelTarget(x, y) {
  if (!statusOpen) return null;
  const hit = document.elementFromPoint(x, y);
  const under = hit && quickPanel.contains(hit) ? hit.closest(QUICK_CONTROLS) : null;
  if (under) return under;
  let nearest = null;
  let closest = QUICK_GRACE;
  const knobs = knobWindow.getBoundingClientRect();
  for (const control of quickPanel.querySelectorAll(QUICK_CONTROLS)) {
    const box = control.getBoundingClientRect();
    // A toggle scrolled past the window's edge is still in the document and still a candidate for the grace radius, so a tap near the edge would answer with a switch nobody can see.
    if (control.classList.contains('quick-knob') && (box.left < knobs.left - 1 || box.right > knobs.right + 1)) continue;
    const across = Math.max(box.left - x, 0, x - box.right);
    const down = Math.max(box.top - y, 0, y - box.bottom);
    const gap = Math.hypot(across, down);
    if (gap >= closest) continue;
    closest = gap;
    nearest = control;
  }
  return nearest;
}


let panelPushFrom = null;
let panelPushed = false;


// The Now bubble is thrown home along the flick that dismissed the panel, so how far across that flick had gone when it crossed has to survive the two calls between here and the throw. It is reported in pixels rather than as a ratio: what those pixels are worth as an angle is the throw's business and not this one's. Every other way the panel closes has no hand behind it and leaves this at nothing.
let panelPushLean = 0;

export function statusPanelPush(action, x, y) {
  if (action === 'down') {
    
    
    
    
    
    
    
    
    
    
    panelPushFrom = statusPanelTarget(x, y) ? null : { x, y };
    panelPushed = false;
    return;
  }
  if (action !== 'move' || !panelPushFrom || panelPushed) return;
  if (y - panelPushFrom.y < -24 && Math.abs(x - panelPushFrom.x) < 40) {
    panelPushed = true;
    panelPushLean = x - panelPushFrom.x;
    panelPushFrom = null;
    bridge.triggerHaptic('dismiss');
    closeStatusPanel();
  }
}

export function statusTouch(action, x, y) {
  if (action === 'down') {
    statusHeld = false;
    statusPulled = false;
    statusDrop = 0;
    statusDownAt = { x, y };
    statusPill.classList.add('pressing');
    clearTimeout(statusHoldTimer);
    statusHoldTimer = setTimeout(() => {
      statusHeld = true;
      statusPill.classList.remove('pressing');
      bridge.triggerHaptic('expand');
      bridge.openConnectionSettings(subject());
    }, HOLD_MILLIS);
    return;
  }
  if (action === 'move') {
    if (!statusDownAt) return;
    if (statusPulled) return;
    const across = x - statusDownAt.x;
    const down = y - statusDownAt.y;
    statusDrop = Math.max(statusDrop, down);
    
    
    
    toy(statusPill, '--status-drag', across, down);
    if (rubberBandPast(across, down)) {
      clearTimeout(statusHoldTimer);
      statusPill.classList.remove('pressing');
    }
    if (down > STATUS_PULL && down > Math.abs(across)) {
      
      
      statusPulled = true;
      clearTimeout(statusHoldTimer);
      statusPill.classList.remove('pressing');
      untoy(statusPill, '--status-drag');
      bridge.triggerHaptic('expand');
      openStatusPanel();
    }
    return;
  }
  clearTimeout(statusHoldTimer);
  statusPill.classList.remove('pressing');
  untoy(statusPill, '--status-drag');
  const travelled = statusDownAt ? Math.hypot(x - statusDownAt.x, y - statusDownAt.y) : Infinity;
  statusDownAt = null;
  
  
  if (statusOpen) fitStatusProxy();
  
  
  if (action === 'cancel') {
    if (!statusHeld && !statusPulled && !statusOpen && statusDrop > STATUS_PULL_STOLEN) {
      bridge.triggerHaptic('expand');
      openStatusPanel();
    }
    return;
  }
  if (action !== 'up' || statusHeld || statusPulled || travelled >= PROXY_TAP_SLOP) return;
  bridge.triggerHaptic('tap');
  if (statusOpen) closeStatusPanel();
  else openStatusPanel();
}


const TRANSFER_DONE_DWELL = 1100;

let transferTick = null;










export function setTransfer(payload) {
  if (payload) {
    const isDone = payload.total > 0 && payload.done >= payload.total;
    transfer = Object.assign({ isDone }, payload);
    if (isDone) holdTheTick();
    paintStatus();
    return;
  }
  if (transfer && transfer.isDone) return;
  if (transfer && transfer.total && transfer.done / transfer.total >= 0.95) {
    transfer = Object.assign({}, transfer, { isDone: true });
    holdTheTick();
    paintStatus();
    return;
  }
  transfer = null;
  paintStatus();
}

const transferStrip = document.getElementById('quick-transfer');
const transferGlyph = document.getElementById('transfer-glyph');
const transferReading = document.getElementById('transfer-reading');

function holdTheTick() {
  clearTimeout(transferTick);
  transferTick = setTimeout(() => {
    transfer = null;
    paintStatus();
  }, TRANSFER_DONE_DWELL);
}

window.onConnectivity = state => {
  attached = state;
  bornStatus();
  paintStatus();
};


window.onCharge = (level, plugged, remaining) => {
  charge = level;
  isPlugged = plugged;
  remainingMinutes = remaining;
  paintStatus();
};



window.addEventListener('resize', () => {
  if (!isBorn) return;
  fitStatusPanel();
  fitStatusProxy();
});

root.style.setProperty('--status-right', STATUS_RIGHT + 'px');





const ACTION_GLYPHS = {
  settings: '<svg viewBox="0 0 24 24"><path fill-rule="evenodd" clip-rule="evenodd" d="M12 8.25C9.92894 8.25 8.25 9.92893 8.25 12C8.25 14.0711 9.92894 15.75 12 15.75C14.0711 15.75 15.75 14.0711 15.75 12C15.75 9.92893 14.0711 8.25 12 8.25ZM9.75 12C9.75 10.7574 10.7574 9.75 12 9.75C13.2426 9.75 14.25 10.7574 14.25 12C14.25 13.2426 13.2426 14.25 12 14.25C10.7574 14.25 9.75 13.2426 9.75 12Z"/><path fill-rule="evenodd" clip-rule="evenodd" d="M11.9747 1.25C11.5303 1.24999 11.1592 1.24999 10.8546 1.27077C10.5375 1.29241 10.238 1.33905 9.94761 1.45933C9.27379 1.73844 8.73843 2.27379 8.45932 2.94762C8.31402 3.29842 8.27467 3.66812 8.25964 4.06996C8.24756 4.39299 8.08454 4.66251 7.84395 4.80141C7.60337 4.94031 7.28845 4.94673 7.00266 4.79568C6.64714 4.60777 6.30729 4.45699 5.93083 4.40743C5.20773 4.31223 4.47642 4.50819 3.89779 4.95219C3.64843 5.14353 3.45827 5.3796 3.28099 5.6434C3.11068 5.89681 2.92517 6.21815 2.70294 6.60307L2.67769 6.64681C2.45545 7.03172 2.26993 7.35304 2.13562 7.62723C1.99581 7.91267 1.88644 8.19539 1.84541 8.50701C1.75021 9.23012 1.94617 9.96142 2.39016 10.5401C2.62128 10.8412 2.92173 11.0602 3.26217 11.2741C3.53595 11.4461 3.68788 11.7221 3.68786 12C3.68785 12.2778 3.53592 12.5538 3.26217 12.7258C2.92169 12.9397 2.62121 13.1587 2.39007 13.4599C1.94607 14.0385 1.75012 14.7698 1.84531 15.4929C1.88634 15.8045 1.99571 16.0873 2.13552 16.3727C2.26983 16.6469 2.45535 16.9682 2.67758 17.3531L2.70284 17.3969C2.92507 17.7818 3.11058 18.1031 3.28089 18.3565C3.45817 18.6203 3.64833 18.8564 3.89769 19.0477C4.47632 19.4917 5.20763 19.6877 5.93073 19.5925C6.30717 19.5429 6.647 19.3922 7.0025 19.2043C7.28833 19.0532 7.60329 19.0596 7.8439 19.1986C8.08452 19.3375 8.24756 19.607 8.25964 19.9301C8.27467 20.3319 8.31403 20.7016 8.45932 21.0524C8.73843 21.7262 9.27379 22.2616 9.94761 22.5407C10.238 22.661 10.5375 22.7076 10.8546 22.7292C11.1592 22.75 11.5303 22.75 11.9747 22.75H12.0252C12.4697 22.75 12.8407 22.75 13.1454 22.7292C13.4625 22.7076 13.762 22.661 14.0524 22.5407C14.7262 22.2616 15.2616 21.7262 15.5407 21.0524C15.686 20.7016 15.7253 20.3319 15.7403 19.93C15.7524 19.607 15.9154 19.3375 16.156 19.1985C16.3966 19.0596 16.7116 19.0532 16.9974 19.2042C17.3529 19.3921 17.6927 19.5429 18.0692 19.5924C18.7923 19.6876 19.5236 19.4917 20.1022 19.0477C20.3516 18.8563 20.5417 18.6203 20.719 18.3565C20.8893 18.1031 21.0748 17.7818 21.297 17.3969L21.3223 17.3531C21.5445 16.9682 21.7301 16.6468 21.8644 16.3726C22.0042 16.0872 22.1135 15.8045 22.1546 15.4929C22.2498 14.7697 22.0538 14.0384 21.6098 13.4598C21.3787 13.1586 21.0782 12.9397 20.7378 12.7258C20.464 12.5538 20.3121 12.2778 20.3121 11.9999C20.3121 11.7221 20.464 11.4462 20.7377 11.2742C21.0783 11.0603 21.3788 10.8414 21.6099 10.5401C22.0539 9.96149 22.2499 9.23019 22.1547 8.50708C22.1136 8.19546 22.0043 7.91274 21.8645 7.6273C21.7302 7.35313 21.5447 7.03183 21.3224 6.64695L21.2972 6.60318C21.0749 6.21825 20.8894 5.89688 20.7191 5.64347C20.5418 5.37967 20.3517 5.1436 20.1023 4.95225C19.5237 4.50826 18.7924 4.3123 18.0692 4.4075C17.6928 4.45706 17.353 4.60782 16.9975 4.79572C16.7117 4.94679 16.3967 4.94036 16.1561 4.80144C15.9155 4.66253 15.7524 4.39297 15.7403 4.06991C15.7253 3.66808 15.686 3.2984 15.5407 2.94762C15.2616 2.27379 14.7262 1.73844 14.0524 1.45933C13.762 1.33905 13.4625 1.29241 13.1454 1.27077C12.8407 1.24999 12.4697 1.24999 12.0252 1.25H11.9747ZM10.5216 2.84515C10.5988 2.81319 10.716 2.78372 10.9567 2.76729C11.2042 2.75041 11.5238 2.75 12 2.75C12.4762 2.75 12.7958 2.75041 13.0432 2.76729C13.284 2.78372 13.4012 2.81319 13.4783 2.84515C13.7846 2.97202 14.028 3.21536 14.1548 3.52165C14.1949 3.61826 14.228 3.76887 14.2414 4.12597C14.271 4.91835 14.68 5.68129 15.4061 6.10048C16.1321 6.51968 16.9974 6.4924 17.6984 6.12188C18.0143 5.9549 18.1614 5.90832 18.265 5.89467C18.5937 5.8514 18.9261 5.94047 19.1891 6.14228C19.2554 6.19312 19.3395 6.27989 19.4741 6.48016C19.6125 6.68603 19.7726 6.9626 20.0107 7.375C20.2488 7.78741 20.4083 8.06438 20.5174 8.28713C20.6235 8.50382 20.6566 8.62007 20.6675 8.70287C20.7108 9.03155 20.6217 9.36397 20.4199 9.62698C20.3562 9.70995 20.2424 9.81399 19.9397 10.0041C19.2684 10.426 18.8122 11.1616 18.8121 11.9999C18.8121 12.8383 19.2683 13.574 19.9397 13.9959C20.2423 14.186 20.3561 14.29 20.4198 14.373C20.6216 14.636 20.7107 14.9684 20.6674 15.2971C20.6565 15.3799 20.6234 15.4961 20.5173 15.7128C20.4082 15.9355 20.2487 16.2125 20.0106 16.6249C19.7725 17.0373 19.6124 17.3139 19.474 17.5198C19.3394 17.72 19.2553 17.8068 19.189 17.8576C18.926 18.0595 18.5936 18.1485 18.2649 18.1053C18.1613 18.0916 18.0142 18.045 17.6983 17.8781C16.9973 17.5075 16.132 17.4803 15.4059 17.8995C14.68 18.3187 14.271 19.0816 14.2414 19.874C14.228 20.2311 14.1949 20.3817 14.1548 20.4784C14.028 20.7846 13.7846 21.028 13.4783 21.1549C13.4012 21.1868 13.284 21.2163 13.0432 21.2327C12.7958 21.2496 12.4762 21.25 12 21.25C11.5238 21.25 11.2042 21.2496 10.9567 21.2327C10.716 21.2163 10.5988 21.1868 10.5216 21.1549C10.2154 21.028 9.97201 20.7846 9.84514 20.4784C9.80512 20.3817 9.77195 20.2311 9.75859 19.874C9.72896 19.0817 9.31997 18.3187 8.5939 17.8995C7.86784 17.4803 7.00262 17.5076 6.30158 17.8781C5.98565 18.0451 5.83863 18.0917 5.73495 18.1053C5.40626 18.1486 5.07385 18.0595 4.81084 17.8577C4.74458 17.8069 4.66045 17.7201 4.52586 17.5198C4.38751 17.314 4.22736 17.0374 3.98926 16.625C3.75115 16.2126 3.59171 15.9356 3.4826 15.7129C3.37646 15.4962 3.34338 15.3799 3.33248 15.2971C3.28921 14.9684 3.37828 14.636 3.5801 14.373C3.64376 14.2901 3.75761 14.186 4.0602 13.9959C4.73158 13.5741 5.18782 12.8384 5.18786 12.0001C5.18791 11.1616 4.73165 10.4259 4.06021 10.004C3.75769 9.81389 3.64385 9.70987 3.58019 9.62691C3.37838 9.3639 3.28931 9.03149 3.33258 8.7028C3.34348 8.62001 3.37656 8.50375 3.4827 8.28707C3.59181 8.06431 3.75125 7.78734 3.98935 7.37493C4.22746 6.96253 4.3876 6.68596 4.52596 6.48009C4.66055 6.27983 4.74468 6.19305 4.81093 6.14222C5.07395 5.9404 5.40636 5.85133 5.73504 5.8946C5.83873 5.90825 5.98576 5.95483 6.30173 6.12184C7.00273 6.49235 7.86791 6.51962 8.59394 6.10045C9.31998 5.68128 9.72896 4.91837 9.75859 4.12602C9.77195 3.76889 9.80512 3.61827 9.84514 3.52165C9.97201 3.21536 10.2154 2.97202 10.5216 2.84515Z"/></svg>',
  power: '<svg viewBox="0 0 24 24"><path fill-rule="evenodd" clip-rule="evenodd" d="M13 3C13 2.44772 12.5523 2 12 2C11.4477 2 11 2.44772 11 3V12C11 12.5523 11.4477 13 12 13C12.5523 13 13 12.5523 13 12V3ZM8.6092 5.8744C9.09211 5.60643 9.26636 4.99771 8.99839 4.5148C8.73042 4.03188 8.12171 3.85763 7.63879 4.1256C4.87453 5.65948 3 8.61014 3 12C3 16.9706 7.02944 21 12 21C16.9706 21 21 16.9706 21 12C21 8.66747 19.1882 5.75928 16.5007 4.20465C16.0227 3.92811 15.4109 4.09147 15.1344 4.56953C14.8579 5.04759 15.0212 5.65932 15.4993 5.93586C17.5942 7.14771 19 9.41027 19 12C19 15.866 15.866 19 12 19C8.13401 19 5 15.866 5 12C5 9.3658 6.45462 7.06997 8.6092 5.8744Z"/></svg>',
};

const WEATHER_GLYPHS = {
  clear: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4.4"/><path d="M12 1.5v3M12 19.5v3M1.5 12h3M19.5 12h3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M19.8 4.2l-2.1 2.1M6.3 17.7l-2.1 2.1" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
  cloud: '<svg viewBox="0 0 24 24"><path d="M7 19a4.5 4.5 0 0 1-.5-8.97A6 6 0 0 1 18 10.5a4.25 4.25 0 0 1-.5 8.5z"/></svg>',
  rain: '<svg viewBox="0 0 24 24"><path d="M7 15a4.5 4.5 0 0 1-.5-8.97A6 6 0 0 1 18 6.5a4.25 4.25 0 0 1-.5 8.5z"/><path d="M8.5 17.5 7 21M13 17.5 11.5 21M17.5 17.5 16 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
};

const statusSettings = document.getElementById('status-settings');
const statusPower = document.getElementById('status-power');
statusSettings.innerHTML = ACTION_GLYPHS.settings;
statusPower.innerHTML = ACTION_GLYPHS.power;

statusSettings.addEventListener('click', event => {
  event.stopPropagation();
  bridge.triggerHaptic('tap');
  bridge.openControlPanel();
  closeStatusPanel();
});

statusPower.addEventListener('click', event => {
  event.stopPropagation();
  bridge.triggerHaptic('expand');
  bridge.openPowerMenu();
  closeStatusPanel();
});




const weatherReading = document.getElementById('weather-reading');
const weatherGlyph = document.getElementById('weather-glyph');

// WeatherWatch answers requestWeather() off the phone's last known location; a miss (no fix, no network) arrives as null and the line stands at its own shape rather than at a number that would be a lie.
window.onWeather = payload => {
  const known = Boolean(payload);
  setReading(weatherReading, known ? Math.round(payload.celsius) + '°C' : '--°C');
  weatherGlyph.innerHTML = WEATHER_GLYPHS[(payload && payload.sky) || 'cloud'];
};
window.onWeather(null);




const vitalRows = {
  storage: document.querySelector('.vital[data-vital="storage"]'),
  ram: document.querySelector('.vital[data-vital="ram"]'),
};
const vitalsBox = document.getElementById('quick-vitals');

let vitals = null;

// VitalsWatch answers requestVitals() with storage and RAM, read fresh each time.
window.onVitals = payload => {
  vitals = payload;
  if (statusOpen) paintVitals();
};

function paintVitals() {
  paintVital(vitalRows.storage, vitals && vitals.storage);
  paintVital(vitalRows.ram, vitals && vitals.ram);
}

function paintVital(row, reading) {
  row.querySelector('.vital-reading').textContent = reading
    ? reading.usedGigabytes.toFixed(1) + ' / ' + reading.totalGigabytes.toFixed(0) + ' GB'
    : '—';
  row.querySelector('.vital-gauge-fill').style.width = (reading ? reading.percent : 0) + '%';
}

let vitalsHeld = false;
let vitalsHoldTimer = 0;

vitalsBox.addEventListener('touchstart', () => {
  vitalsHeld = false;
  vitalsBox.classList.add('pressing');
  vitalsHoldTimer = setTimeout(() => {
    vitalsHeld = true;
    vitalsBox.classList.remove('pressing');
    bridge.triggerHaptic('expand');
    bridge.openConnectionSettings('vitals');
    closeStatusPanel();
  }, HOLD_MILLIS);
}, { passive: true });

['touchend', 'touchcancel'].forEach(type => {
  vitalsBox.addEventListener(type, () => {
    clearTimeout(vitalsHoldTimer);
    vitalsBox.classList.remove('pressing');
  }, { passive: true });
});

// Mirrors --vitals-purge in pill.css — the sweep has to be over before Device Care takes the screen.
const VITALS_PURGE = 620;

vitalsBox.addEventListener('click', event => {
  event.stopPropagation();
  if (vitalsHeld) return;
  bridge.triggerHaptic('expand');
  vitalsBox.classList.remove('cleaning');
  void vitalsBox.offsetWidth;
  vitalsBox.classList.add('cleaning');
  setTimeout(() => {
    vitalsBox.classList.remove('cleaning');
    bridge.openConnectionSettings('vitals');
    closeStatusPanel();
  }, VITALS_PURGE);
});




// The brightness bar held clears the panel: the frost goes first and the modules leave behind it on the panel's own close, and the lift runs the same order — frost back first, then the modules grow in. Volume takes the merge and leaves the panel standing.
const levelsRow = document.getElementById('quick-row-levels');
export const SOLO_HOLD = 420;
// Mirrors --solo-ms and --solo-out in pill.css; SOLO_OUT is the panel's own collapse allowance, the same 110ms and slack closeStatusPanel gives it.
const SOLO_TRAVEL = 420;
const SOLO_OUT = 180;
const SOLO_FROST = 260;
const SOLO_IN = QUICK_POP + QUICK_RANK_STEPS * QUICK_STAGGER;

let soloTimer = 0;
let soloBackTimer = 0;
let soloLevel = null;

function enterSolo(level) {
  if (soloLevel) return;
  soloLevel = level;
  clearTimeout(soloBackTimer);
  quickPanel.classList.remove('solo-in');
  levelsRow.dataset.held = level.dataset.level;
  levelsRow.classList.add('merged');
  levels.forEach(other => other.classList.toggle('solo-hidden', other !== level));
  if (level.dataset.level === 'brightness') {
    quickPanel.style.setProperty('--pop-step', QUICK_STAGGER + 'ms');
  const panelBox = quickPanel.getBoundingClientRect();
    const rowBox = levelsRow.getBoundingClientRect();
    const lift = Math.round((panelBox.top + panelBox.height / 2) - (rowBox.top + rowBox.height / 2));
    root.style.setProperty('--solo-lift', lift + 'px');
    quickPanel.classList.add('solo-out', 'level-solo');
  }
  bridge.triggerHaptic('expand');
  stirLiquid(SOLO_OUT + SOLO_FROST + SOLO_TRAVEL);
}

function leaveSolo() {
  if (!soloLevel) return;
  soloLevel = null;
  levelsRow.classList.remove('merged');
  delete levelsRow.dataset.held;
  levels.forEach(other => other.classList.remove('solo-hidden'));
  if (!quickPanel.classList.contains('solo-out')) {
    stirLiquid(SOLO_TRAVEL);
    return;
  }
  quickPanel.classList.remove('level-solo');
  soloBackTimer = setTimeout(() => {
    quickPanel.classList.remove('solo-out');
    quickPanel.classList.add('solo-in');
    soloBackTimer = setTimeout(() => quickPanel.classList.remove('solo-in'), SOLO_IN);
  }, SOLO_FROST);
  stirLiquid(SOLO_FROST + Math.max(SOLO_IN, SOLO_TRAVEL));
}




let panelNowOpen = false;

export function setPanelNowOpen(open) {
  panelNowOpen = open;
  quickPanel.classList.toggle('now-open', open);
}

function enterPanelNow() {
  openPanelNow(panelNowOpen);
}

function leavePanelNow() {
  const lean = panelPushLean;
  panelPushLean = 0;
  closePanelNow(lean);
}

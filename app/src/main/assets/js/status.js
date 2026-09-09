import { PROXY_TAP_SLOP } from './bridge.js';
import { clockPill } from './clock.js';
import { stirLiquid } from './liquid.js';
import { rubberBandPast, toy, untoy } from './motion.js';
import { closeNowPanel, nowOpen } from './now.js';
import { applyClosedWindow, liveMods, showFace, toClosed } from './row.js';
import { GROWN_PAD, HOLD_MILLIS, bridge, pill, root, shared } from './state.js';
















export const statusPill = document.getElementById('status');
const statusClosed = document.getElementById('status-closed');
const statusFaces = {
  closed: statusClosed,
  charge: document.getElementById('status-charge'),
};


const STATUS_RIGHT = 14;


const EDGE_OVER = 20;


const PILL_DOCK_TALL = 130;
const PILL_DOCK_TRANSFER_TALL = 46;




function dockShows() {
  return Boolean(shared.media) || Boolean(transfer);
}

const CLOSED_FACE = { media: 'media', timer: 'timer', call: 'call' };
function closedFace() {
  const owner = liveMods()[0];
  return owner ? CLOSED_FACE[owner] : 'idle';
}

function fitPillDock() {
  const hasMedia = Boolean(shared.media);
  const hasTransfer = Boolean(transfer);
  const tall = (hasMedia ? PILL_DOCK_TALL : 0) + (hasTransfer ? PILL_DOCK_TRANSFER_TALL : 0);
  const width = STATUS_PANEL.width + EDGE_OVER * 2;
  const bottom = screenHeight() - STATUS_MARGIN_FOOT;
  const top = bottom - tall;
  root.style.setProperty('--pill-dock-width', Math.round(width) + 'px');
  root.style.setProperty('--pill-dock-top', Math.round(top) + 'px');
  root.style.setProperty('--pill-dock-height', Math.round(tall + EDGE_OVER) + 'px');
  bridge.setWindowBounds(Math.round(width), Math.round(bottom), 0, 0);
}

export function refreshDock() {
  if (statusOpen) paintDock();
}

function paintDock() {
  const showing = dockShows();
  pill.classList.toggle('panel-dock', showing);
  pill.classList.toggle('dock-transfer', showing && Boolean(transfer));
  dockTransfer.classList.toggle('showing', showing && Boolean(transfer));
  if (!showing) {
    showFace(closedFace());
    applyClosedWindow();
    return;
  }
  fitPillDock();
  showFace(shared.media ? 'player' : closedFace());
  if (transfer) {
    dockTransferGlyph.innerHTML = transfer.isDone ? GLYPHS.transferDone : GLYPHS[transfer.mod];
    const share = transfer.isDone || !transfer.total ? 100 : Math.round((transfer.done / transfer.total) * 100);
    dockTransferReading.textContent = transfer.isDone ? 'Done' : share + '%';
  }
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

function fitStatusPanel() {
  const top = statusPill.getBoundingClientRect().top + GROWN_PAD + QUICK_CLEARANCE;
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
  saver: '<svg viewBox="0 0 24 24"><path d="M9 2h6v2h1.5A1.5 1.5 0 0 1 18 5.5v15A1.5 1.5 0 0 1 16.5 22h-9A1.5 1.5 0 0 1 6 20.5v-15A1.5 1.5 0 0 1 7.5 4H9zm3.6 5-4.1 7h2.6l-.7 5 4.1-7h-2.6z"/></svg>',
  
  recording: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/><circle class="record-dot" cx="12" cy="12" r="4.5" fill="currentColor"/></svg>',
  mic: '<svg viewBox="0 0 24 24"><path fill="none" d="M12 17V21M12 21H9M12 21H15" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><rect fill="none" x="10" y="3" width="4" height="10" rx="2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path fill="none" d="M17.7378 12.7542C17.3674 13.9659 16.6228 15.0293 15.6109 15.7918C14.599 16.5544 13.3716 16.977 12.1047 16.9991C10.8378 17.0212 9.59647 16.6417 8.55854 15.9149C7.52061 15.1881 6.73941 14.1515 6.32689 12.9534" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
};








const GLYPHS_OFF = {
  wifi: '<svg viewBox="0 0 24 24" fill="none"><path d="M1.33309 8.07433C0.92156 8.44266 0.886539 9.07485 1.25487 9.48638C1.62319 9.89791 2.25539 9.93293 2.66691 9.5646L1.33309 8.07433ZM21.3331 9.5646C21.7446 9.93293 22.3768 9.89791 22.7451 9.48638C23.1135 9.07485 23.0784 8.44266 22.6669 8.07433L21.3331 9.5646ZM12 19C11.4477 19 11 19.4477 11 20C11 20.5523 11.4477 21 12 21V19ZM12.01 21C12.5623 21 13.01 20.5523 13.01 20C13.01 19.4477 12.5623 19 12.01 19V21ZM14.6905 17.04C15.099 17.4116 15.7315 17.3817 16.1031 16.9732C16.4748 16.5646 16.4448 15.9322 16.0363 15.5605L14.6905 17.04ZM18.0539 13.3403C18.4624 13.7119 19.0949 13.682 19.4665 13.2734C19.8381 12.8649 19.8082 12.2324 19.3997 11.8608L18.0539 13.3403ZM7.96372 15.5605C7.55517 15.9322 7.52524 16.5646 7.89687 16.9732C8.2685 17.3817 8.90095 17.4116 9.3095 17.04L7.96372 15.5605ZM4.60034 11.8608C4.19179 12.2324 4.16185 12.8649 4.53348 13.2734C4.90511 13.682 5.53756 13.7119 5.94611 13.3403L4.60034 11.8608ZM10.5705 4.06305C10.0204 4.1118 9.61391 4.59729 9.66266 5.14741C9.71141 5.69754 10.1969 6.10399 10.747 6.05525L10.5705 4.06305ZM17.3393 10.3798C16.8567 10.1114 16.2478 10.285 15.9794 10.7677C15.711 11.2504 15.8847 11.8593 16.3673 12.1277L17.3393 10.3798ZM3.70711 2.29289C3.31658 1.90237 2.68342 1.90237 2.29289 2.29289C1.90237 2.68342 1.90237 3.31658 2.29289 3.70711L3.70711 2.29289ZM20.2929 21.7071C20.6834 22.0976 21.3166 22.0976 21.7071 21.7071C22.0976 21.3166 22.0976 20.6834 21.7071 20.2929L20.2929 21.7071ZM12 6C15.5863 6 18.8556 7.34716 21.3331 9.5646L22.6669 8.07433C19.8369 5.54138 16.0972 4 12 4V6ZM12 21H12.01V19H12V21ZM12 16C13.0367 16 13.9793 16.3931 14.6905 17.04L16.0363 15.5605C14.9713 14.5918 13.5536 14 12 14V16ZM9.3095 17.04C10.0207 16.3931 10.9633 16 12 16V14C10.4464 14 9.02872 14.5918 7.96372 15.5605L9.3095 17.04ZM10.747 6.05525C11.1596 6.01869 11.5775 6 12 6V4C11.5185 4 11.0417 4.0213 10.5705 4.06305L10.747 6.05525ZM16.3673 12.1277C16.9757 12.466 17.5412 12.874 18.0539 13.3403L19.3997 11.8608C18.7751 11.2927 18.0844 10.7941 17.3393 10.3798L16.3673 12.1277ZM2.29289 3.70711L5.46648 6.8807L6.8807 5.46648L3.70711 2.29289L2.29289 3.70711ZM2.66691 9.5646C3.81213 8.53961 5.12648 7.70074 6.56232 7.09494L5.78486 5.25224C4.14251 5.94517 2.64069 6.904 1.33309 8.07433L2.66691 9.5646ZM5.46648 6.8807L9.46042 10.8746L10.8746 9.46042L6.8807 5.46648L5.46648 6.8807ZM9.46042 10.8746L20.2929 21.7071L21.7071 20.2929L10.8746 9.46042L9.46042 10.8746ZM5.94611 13.3403C7.15939 12.2367 8.67355 11.4612 10.3496 11.1508L9.98543 9.18424C7.93271 9.5644 6.08108 10.5139 4.60034 11.8608L5.94611 13.3403Z" fill="currentColor"/></svg>',
  
  
  
  bluetooth: '<svg viewBox="0 0 24 24" fill="none"><path d="M7 17L17 7L12 2V22L17 17L7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path d="M3.5 3.5L20.5 20.5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
  mobile: '<svg viewBox="0 0 24 24"><path d="M4 16h3v4H4zm5-3h3v7H9zm5-4h3v11h-3zm5-5h3v16h-3z" opacity="0.55"/><path fill="none" d="M3.5 3.5L20.5 20.5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
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
  
  
  
  const paired = attached && attached.bluetooth && attached.bluetooth.charge >= 0
    ? attached.bluetooth.charge
    : -1;
  
  const own = charge >= 0
    ? '<span class="own-charge" data-charge="' + chargeBand() + '">' + charge + '%</span>'
    : '';
  const reading = (paired >= 0 ? paired + '% · ' : '') + own;
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
  if (statusOpen) paintDock();

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

function showStatusFace(name) {
  Object.entries(statusFaces).forEach(([key, face]) => {
    face.classList.toggle('showing', key === name);
  });
}


function statusPanelLeft() {
  return (screenWidth() - STATUS_PANEL.width) / 2;
}







const quickPanel = document.getElementById('quick-panel');
const quickScrim = document.getElementById('quick-scrim');

const quickBubbles = [...quickPanel.querySelectorAll('.quick-bubble')];















const QUICK_STAGGER = 5;


const QUICK_DROP_STAGGER = 4;


const QUICK_POP = 460;






const SCRIM_FROST_MS = 450;








function shuffleQuickRanks() {
  const ranks = quickBubbles.map((bubble, index) => index);
  for (let at = ranks.length - 1; at > 0; at -= 1) {
    const swap = Math.floor(Math.random() * (at + 1));
    [ranks[at], ranks[swap]] = [ranks[swap], ranks[at]];
  }
  quickBubbles.forEach((bubble, index) => bubble.style.setProperty('--pop-rank', ranks[index]));
}


let panelSettle = null;

let quickSettle = null;

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
  paintDock();


  bridge.requestToggles();



  root.style.setProperty('--liquid-tall', (STATUS_PANEL.height + GROWN_PAD + 24 + PILL_DOCK_TALL + PILL_DOCK_TRANSFER_TALL) + 'px');







  if (!statusDownAt) fitStatusProxy();



  statusPill.classList.add('open');
  clockPill.classList.add('corner-grow');

  quickPanel.classList.remove('leaving');
  
  
  shuffleQuickRanks();
  
  
  quickPanel.classList.add('showing');
  
  
  
  
  
  stirLiquid(STATUS_PANEL.ms + quickBubbles.length * QUICK_STAGGER + QUICK_POP);
}

export function closeStatusPanel() {
  if (!statusOpen) return;
  statusOpen = false;
  
  
  
  
  statusPill.classList.add('panel-closing');
  showStatusFace('closed');
  
  
  quickPanel.classList.remove('showing');
  quickPanel.classList.add('leaving');
  
  
  statusPill.classList.remove('open');
  clockPill.classList.remove('corner-grow');
  pill.classList.remove('panel-dock', 'dock-transfer');
  dockTransfer.classList.remove('showing');
  showFace(closedFace());
  applyClosedWindow();

  fitStatusProxy();
  
  const dropped = quickBubbles.length * QUICK_DROP_STAGGER + 180;
  
  
  
  
  stirLiquid(Math.max(STATUS_PANEL.ms, dropped, SCRIM_FROST_MS) + 160);
  panelSettle = setTimeout(() => {
    
    
    
    statusPill.classList.remove('panel-closing');
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











const quickModes = [...document.querySelectorAll('.quick-mode')];
const quickKnobs = [...document.querySelectorAll('.quick-knob')];


const faceOf = control => control.querySelector('.quick-icon') || control;

[...quickModes, ...quickKnobs].forEach(control => {
  faceOf(control).innerHTML = glyphFor(control.dataset.toggle, false);
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
    const name = knob.dataset.toggle;
    const value = name === 'recording' ? isRecordingLive() : state[name];
    setControl(knob, Boolean(value), false);
    
    
    knob.classList.toggle('unavailable', !(name in state) && name !== 'recording');
  });
  
  batteryBox.classList.toggle('saving', Boolean(state.saver));
}







let toggles = {};













function paintModeLabels() {
  const paired = attached && attached.bluetooth;
  const text = {
    
    
    usb: !(attached && attached.usb) ? 'no cable' : toggles.usb ? 'file transfer' : 'charging only',
    bluetooth: paired ? (paired.name || 'connected') : toggles.bluetooth ? 'nothing paired' : '',
    wifi: attached && attached.link === 'wifi' && attached.ssid
      ? attached.ssid
      : toggles.wifi ? 'not connected' : '',
    
    
    mobile: !toggles.mobile ? ''
      : attached && attached.link === 'mobile' ? (attached.generation || 'connected')
      : 'standby',
    
    
    hotspot: attached && attached.hotspot ? 'sharing' : toggles.hotspot ? 'nobody joined' : '',
    
    
    plane: toggles.plane ? 'radios off' : '',
    
    
    gps: '',
  };
  const waiting = {
    bluetooth: 'nothing paired',
    wifi: 'not connected',
    hotspot: 'nobody joined',
    mobile: 'standby',
  };
  quickModes.forEach(button => {
    const name = button.dataset.toggle;


    const label = button.querySelector('.quick-label');
    if (label) {
      const reading = text[name] || '';
      const isWaiting = reading && reading === waiting[name];
      const wasWaiting = label.classList.contains('waiting');
      label.classList.toggle('waiting', isWaiting);
      if (isWaiting) {
        if (!wasWaiting) {
          label.textContent = '';
          for (let dot = 0; dot < 3; dot += 1) {
            const span = document.createElement('span');
            span.className = 'quick-dot';
            label.appendChild(span);
          }
        }
      } else {
        label.textContent = reading;
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









function chargeColour() {
  
  
  
  if (isPlugged) return ANNOUNCE_COLOURS.charging;
  if (charge >= 86) return '#5bfd5b';
  if (charge >= 61) return '#2fbf2f';
  if (charge >= 31) return '#ffd429';
  if (charge >= 16) return '#ff9020';
  return '#e5342b';
}







function chargeBand() {
  if (charge >= 31) return 'ok';
  if (charge >= 16) return 'low';
  return 'critical';
}


function paintBattery() {
  
  
  batteryGlyphBox.innerHTML = isPlugged ? BATTERY_GLYPHS.charging : '';
  batteryReading.textContent = charge >= 0 ? charge + '%' : '--';
  batteryBox.style.setProperty('--charge-height', Math.max(0, charge) + '%');
  batteryBox.style.setProperty('--charge-color', chargeColour());
  
  batteryBox.classList.toggle('low', !isPlugged && charge >= 0 && charge < 15);
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

  level.addEventListener('touchstart', event => {
    event.stopPropagation();
    from = event.touches[0].clientY;
    share = parseFloat(level.style.getPropertyValue('--level')) || 0;
    sent = share;
  }, { passive: true });

  level.addEventListener('touchmove', event => {
    if (from === null) return;
    event.stopPropagation();
    const travelled = from - event.touches[0].clientY;
    if (!level.classList.contains('dragging')) {
      if (Math.abs(travelled) < LEVEL_SLOP) return;
      level.classList.add('dragging');
      bridge.triggerHaptic('tap');
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
      level.classList.remove('dragging');
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






function setControl(control, isOn, isTurning) {
  const wasOn = control.classList.contains('on');
  control.classList.toggle('on', isOn);
  if (!isTurning) {
    if (wasOn !== isOn) faceOf(control).innerHTML = glyphFor(control.dataset.toggle, isOn);
    return;
  }
  
  
  
  control.classList.remove('turning');
  void control.offsetWidth;
  control.classList.add('turning');
  setTimeout(() => {
    faceOf(control).innerHTML = glyphFor(control.dataset.toggle, control.classList.contains('on'));
  }, KNOB_TURN / 2);
}

function pressToggle(name, element) {
  const next = !element.classList.contains('on');
  setControl(element, next, true);
  
  
  
  
  bridge.triggerHaptic('toggle');
  bridge.setToggle(name, next);
}

[...quickModes, ...quickKnobs].forEach(control => {
  control.addEventListener('click', event => {
    event.stopPropagation();
    if (control.classList.contains('unavailable')) return;
    pressToggle(control.dataset.toggle, control);
  });
});



let statusHeld = false;
let statusDownAt = null;
let statusHoldTimer = 0;
let statusPulled = false;

let statusDrop = 0;
























let panelPushFrom = null;
let panelPushed = false;

export function statusPanelPush(action, x, y) {
  if (action === 'down') {
    
    
    
    
    
    
    
    
    
    
    const on = document.elementFromPoint(x, y);
    panelPushFrom = on === quickScrim ? { x, y } : null;
    panelPushed = false;
    return;
  }
  if (action !== 'move' || !panelPushFrom || panelPushed) return;
  if (y - panelPushFrom.y < -24 && Math.abs(x - panelPushFrom.x) < 40) {
    panelPushed = true;
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

const dockTransfer = document.getElementById('dock-transfer');
const dockTransferGlyph = document.getElementById('dock-transfer-glyph');
const dockTransferReading = document.getElementById('dock-transfer-reading');

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


window.onCharge = (level, plugged) => {
  charge = level;
  isPlugged = plugged;
  paintStatus();
};



window.addEventListener('resize', () => {
  if (!isBorn) return;
  fitStatusPanel();
  fitStatusProxy();
});

root.style.setProperty('--status-right', STATUS_RIGHT + 'px');

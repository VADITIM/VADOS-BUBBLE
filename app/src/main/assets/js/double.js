const doublePill = document.getElementById('double');

export function isDoubleOut() {
  return doublePill.classList.contains('showing');
}

export { doublePill };

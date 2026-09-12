// Paste build/submit/Player.java (served by tools/serve_submit.py) into the CodinGame IDE editor.
// Run in the IDE page context (DevTools console or browser automation). Returns a status string.
(async () => {
  const resp = await fetch('http://localhost:7788/Player.java?t=' + Date.now());
  if (!resp.ok) return 'fetch failed: ' + resp.status;
  const code = await resp.text();
  const ta = document.querySelector('.monaco-editor textarea.ime-text-area, .monaco-editor textarea');
  if (!ta) return 'no monaco textarea';
  ta.focus();
  // select all, then paste replaces the selection
  ta.dispatchEvent(new KeyboardEvent('keydown', {key: 'a', code: 'KeyA', ctrlKey: true, bubbles: true}));
  document.execCommand && document.execCommand('selectAll');
  const dt = new DataTransfer();
  dt.setData('text/plain', code);
  const ev = new ClipboardEvent('paste', {clipboardData: dt, bubbles: true, cancelable: true});
  ta.dispatchEvent(ev);
  await new Promise(r => setTimeout(r, 500));
  const lines = document.querySelectorAll('.monaco-editor .view-lines .view-line').length;
  return 'pasted ' + code.length + ' chars; visible lines ' + lines;
})();

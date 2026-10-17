import { useState } from 'react';

export default function CodeBlock({ text, label }) {
  const [copied, setCopied] = useState(false);
  if (!text) return <p className="muted">Nothing defined.</p>;
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch { /* clipboard blocked on plain http, ignore */ }
  };
  return (
    <div className="code">
      <div className="code-bar">
        <span>{label}</span>
        <button type="button" className="link-btn" onClick={copy}>{copied ? 'Copied' : 'Copy'}</button>
      </div>
      <pre>{text}</pre>
    </div>
  );
}

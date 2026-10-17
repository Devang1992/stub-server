import { useMemo, useState } from 'react';
import { pretty } from '../api.js';
import CodeBlock from './CodeBlock.jsx';

function buildRequest(ep, catalog, form) {
  let url = ep.url;
  const query = new URLSearchParams();
  const headers = {};
  const body = {};
  for (const p of ep.params) {
    const v = form.params[p.name];
    if (v === undefined || v === '') continue;
    if (p.in === 'path') url = url.replace(`{${p.name}}`, encodeURIComponent(v));
    else if (p.in === 'header') headers[p.name] = v;
    else if (p.in === 'body') body[p.name] = v;
    else query.append(p.name, v);
  }
  for (const [k, v] of Object.entries(form.headers)) if (v) headers[k] = v;
  if (ep.auth.type !== 'none' && form.auth) headers[ep.auth.header] = form.auth;
  if (form.status) headers[catalog.statusHeader] = form.status;
  if (form.delay) headers[catalog.delayHeader] = form.delay;
  const qs = query.toString();
  const hasBody = Object.keys(body).length > 0;
  if (hasBody) headers['Content-Type'] = 'application/json';
  return { url: qs ? `${url}?${qs}` : url, headers, body: hasBody ? JSON.stringify(body) : undefined };
}

function toCurl(method, req) {
  const parts = [`curl -i -X ${method} '${window.location.origin}${req.url}'`];
  for (const [k, v] of Object.entries(req.headers)) parts.push(`  -H '${k}: ${v}'`);
  if (req.body) parts.push(`  -d '${req.body}'`);
  return parts.join(' \\\n');
}

export default function TryIt({ endpoint: ep, catalog, form, setForm }) {
  const [result, setResult] = useState(null);
  const [sending, setSending] = useState(false);
  const req = useMemo(() => buildRequest(ep, catalog, form), [ep, catalog, form]);

  const set = (group, key) => (e) => {
    const value = e.target.value;
    setForm((f) => (group ? { ...f, [group]: { ...f[group], [key]: value } } : { ...f, [key]: value }));
  };

  const send = async () => {
    setSending(true);
    const started = performance.now();
    try {
      const res = await fetch(req.url, { method: ep.method, headers: req.headers, body: req.body });
      const text = await res.text();
      setResult({ status: res.status, ms: Math.round(performance.now() - started), body: pretty(text) });
    } catch (e) {
      setResult({ status: 'Failed', ms: Math.round(performance.now() - started), body: e.message });
    } finally {
      setSending(false);
    }
  };

  const statusClass = typeof result?.status === 'number' ? `c${String(result.status)[0]}` : 'c5';

  return (
    <div className="panel tryit" id="try-it">
      <h2>Try it</h2>
      <div className="form-grid">
        {ep.params.map((p) => (
          <label key={p.name}>
            <span>{p.name}{p.required && ' *'}</span>
            <input value={form.params[p.name] || ''} onChange={set('params', p.name)} placeholder={p.example || ''} />
          </label>
        ))}
        {ep.auth.type !== 'none' && (
          <label>
            <span>{ep.auth.header}</span>
            <input value={form.auth} onChange={set(null, 'auth')} />
          </label>
        )}
        {ep.headers.map((h) => (
          <label key={h.name}>
            <span>{h.name}{h.required && ' *'}</span>
            {h.allowed.length ? (
              <select value={form.headers[h.name] || ''} onChange={set('headers', h.name)}>
                <option value="">Not sent</option>
                {h.allowed.map((a) => <option key={a} value={a}>{a}</option>)}
              </select>
            ) : (
              <input value={form.headers[h.name] || ''} onChange={set('headers', h.name)} />
            )}
          </label>
        ))}
        <label>
          <span>{catalog.statusHeader}</span>
          <select value={form.status} onChange={set(null, 'status')}>
            <option value="">Not sent (normal behaviour)</option>
            {ep.supportedCodes.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
        <label>
          <span>{catalog.delayHeader}</span>
          <input value={form.delay} onChange={set(null, 'delay')} placeholder="e.g. 3000" inputMode="numeric" />
        </label>
      </div>

      <div className="actions">
        <button type="button" className="btn" onClick={send} disabled={sending}>
          {sending ? 'Sending…' : 'Send request'}
        </button>
      </div>

      <CodeBlock text={toCurl(ep.method, req)} label="curl" />

      {result && (
        <div className="result">
          <div className="result-head">
            <span className={`code-chip ${statusClass} static`}>{result.status}</span>
            <span className="muted">{result.ms} ms</span>
          </div>
          <CodeBlock text={result.body || '(empty body)'} label="response" />
        </div>
      )}
    </div>
  );
}

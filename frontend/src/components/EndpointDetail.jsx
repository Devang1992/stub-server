import { useState } from 'react';
import { pretty } from '../api.js';
import CodeBlock from './CodeBlock.jsx';
import TryIt from './TryIt.jsx';

function defaultAuth(auth) {
  switch (auth.type) {
    case 'bearer': return `Bearer ${auth.tokens[0] || 'any-token'}`;
    case 'basic': return `Basic ${btoa(auth.users[0] || 'user:password')}`;
    case 'api-key': return auth.tokens[0] || 'any-key';
    default: return '';
  }
}

function behaviour(record) {
  if (record._status) return { text: `Always returns ${record._status}`, cls: 'tag tag-red' };
  if (record._delayMs) return { text: `Waits ${record._delayMs / 1000}s`, cls: 'tag tag-amber' };
  return { text: 'Normal', cls: 'tag tag-green' };
}

export default function EndpointDetail({ endpoint, catalog }) {
  const ep = endpoint;
  const [form, setForm] = useState(() => ({
    params: Object.fromEntries(ep.params.map((p) => [p.name, p.example || ''])),
    headers: Object.fromEntries(ep.headers.map((h) => [h.name, h.example || ''])),
    auth: defaultAuth(ep.auth),
    status: '',
    delay: '',
  }));

  const pickRecord = (record) => {
    setForm((f) => ({
      ...f,
      params: Object.fromEntries(ep.params.map((p) => [p.name, record[p.field] != null ? String(record[p.field]) : ''])),
    }));
    document.getElementById('try-it')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const forceCode = (code) => {
    setForm((f) => ({ ...f, status: String(code) }));
    document.getElementById('try-it')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const fields = ep.params.map((p) => p.field);

  return (
    <section className="detail">
      <div className="detail-head">
        <h1>{ep.name}</h1>
        <div className="endpoint-line">
          <span className={`method m-${ep.method.toLowerCase()}`}>{ep.method}</span>
          <code>{ep.url}</code>
        </div>
        {ep.description && <p className="lead">{ep.description}</p>}
      </div>

      <div className="panel">
        <h2>What to send</h2>
        <h3>Authentication</h3>
        {ep.auth.type === 'none' ? (
          <p>No authentication.</p>
        ) : (
          <p>
            <strong>{ep.auth.type}</strong> in the <code>{ep.auth.header}</code> header.{' '}
            {ep.auth.tokens.length > 0 && <>Accepted tokens: {ep.auth.tokens.map((t) => <code key={t}>{t}</code>)}. </>}
            {ep.auth.users.length > 0 && <>Accepted users: {ep.auth.users.map((u) => <code key={u}>{u}</code>)}. </>}
            {ep.auth.tokens.length === 0 && ep.auth.users.length === 0 && 'Any value is accepted. '}
            Missing gives 401, wrong value gives 403.
          </p>
        )}

        <h3>Parameters</h3>
        <table className="table">
          <thead><tr><th>Name</th><th>In</th><th>Required</th><th>Description</th><th>Example</th></tr></thead>
          <tbody>
            {ep.params.map((p) => (
              <tr key={p.name}>
                <td><code>{p.name}</code></td>
                <td>{p.in}</td>
                <td>{p.required ? <span className="tag tag-dark">Required</span> : 'Optional'}</td>
                <td>{p.description}</td>
                <td>{p.example && <code>{p.example}</code>}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <h3>Headers</h3>
        {ep.headers.length === 0 ? <p className="muted">No extra headers.</p> : (
          <table className="table">
            <thead><tr><th>Name</th><th>Required</th><th>Description</th><th>Allowed values</th></tr></thead>
            <tbody>
              {ep.headers.map((h) => (
                <tr key={h.name}>
                  <td><code>{h.name}</code></td>
                  <td>{h.required ? <span className="tag tag-dark">Required</span> : 'Optional'}</td>
                  <td>{h.description}</td>
                  <td>{h.allowed.length ? h.allowed.join(', ') : 'Any'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="panel">
        <h2>Test data</h2>
        <p>
          These records come from <code>{ep.dataSource || 'no data file'}</code>. A call matches a record when every
          parameter you send equals its field. Pick a record to load it into the request below.
        </p>
        {ep.data.length === 0 ? <p className="muted">No data file. The sample response is returned for every valid call.</p> : (
          <div className="table-wrap">
            <table className="table data">
              <thead>
                <tr>{fields.map((f) => <th key={f}>{f}</th>)}<th>Behaviour</th><th>Note</th><th></th></tr>
              </thead>
              <tbody>
                {ep.data.map((r, i) => {
                  const b = behaviour(r);
                  return (
                    <tr key={i}>
                      {fields.map((f) => <td key={f}><code>{r[f] != null ? String(r[f]) : ''}</code></td>)}
                      <td><span className={b.cls}>{b.text}</span></td>
                      <td className="muted">{r._note}</td>
                      <td><button type="button" className="btn btn-small" onClick={() => pickRecord(r)}>Use</button></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="panel">
        <h2>Status codes</h2>
        <p>
          Send <code>{catalog.statusHeader}: 503</code> to get a 503 back regardless of the data. 2xx values return the
          normal body with that status. Pick a code to try it.
        </p>
        <div className="codes">
          {ep.supportedCodes.map((c) => (
            <button key={c} type="button" className={`code-chip c${String(c)[0]}`} onClick={() => forceCode(c)}>
              {c}
              {ep.customErrorBodies.includes(c) && <span className="dot" title="Custom error body defined" />}
            </button>
          ))}
        </div>
        <p className="muted small">A dot means a custom error body file exists for that code; others use the default error shape.</p>
      </div>

      <div className="panel two-col">
        <div>
          <h2>Sample request</h2>
          <CodeBlock text={pretty(ep.sampleRequest)} label="request" />
        </div>
        <div>
          <h2>Sample response</h2>
          <CodeBlock text={pretty(ep.sampleResponse)} label="200 response" />
        </div>
      </div>

      <TryIt endpoint={ep} catalog={catalog} form={form} setForm={setForm} />
    </section>
  );
}

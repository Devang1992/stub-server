import { useCallback, useEffect, useState } from 'react';
import { getJson, pretty } from '../api.js';
import CodeBlock from '../components/CodeBlock.jsx';

function timeAgo(iso) {
  if (!iso) return 'never';
  const s = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return `${s}s ago`;
  if (s < 3600) return `${Math.round(s / 60)} min ago`;
  return new Date(iso).toLocaleString();
}

function AppTile({ app }) {
  const state = app.state.toLowerCase();
  const label = { up: 'Running', down: 'Offline', unknown: 'Not checked yet' }[state];
  return (
    <article className={`tile tile-${state}`}>
      <div className="tile-head">
        <span className={`light light-${state}`} aria-hidden="true" />
        <div>
          <h3>{app.name}</h3>
          <p className="tile-state">{label}</p>
        </div>
      </div>
      {app.description && <p className="muted small">{app.description}</p>}
      <dl className="facts">
        <dt>Health URL</dt><dd><code>{app.url}</code></dd>
        <dt>Last check</dt><dd>{timeAgo(app.lastChecked)}{app.latencyMs != null && `, ${app.latencyMs} ms`}</dd>
        <dt>Detail</dt><dd>{app.detail}</dd>
        {state === 'down' && <><dt>Last up</dt><dd>{timeAgo(app.lastUp)}</dd></>}
      </dl>
      <div className="history" title="Last checks, oldest on the left">
        {app.history.map((ok, i) => <span key={i} className={ok ? 'h-up' : 'h-down'} />)}
      </div>
    </article>
  );
}

function SampleItem({ sample }) {
  const [content, setContent] = useState(null);
  const load = (e) => {
    if (e.target.open && content === null) {
      getJson(`/api/samples/${sample.id}`)
        .then((d) => setContent(d.error ? `Could not read file: ${d.error}` : pretty(d.content)))
        .catch((err) => setContent(err.message));
    }
  };
  return (
    <details className="sample" onToggle={load}>
      <summary>
        <span className="sample-name">{sample.name}</span>
        {sample.description && <span className="muted small">{sample.description}</span>}
      </summary>
      <div className="sample-body">
        {content === null ? <p className="muted">Loading…</p> : <CodeBlock text={content} label={sample.path} />}
      </div>
    </details>
  );
}

export default function StatusPage() {
  const [status, setStatus] = useState(null);
  const [samples, setSamples] = useState([]);
  const [error, setError] = useState(null);
  const [checking, setChecking] = useState(false);

  const load = useCallback(() => {
    getJson('/api/status').then((s) => { setStatus(s); setError(null); }).catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    load();
    getJson('/api/samples').then(setSamples).catch(() => {});
    const t = setInterval(load, 10000);
    return () => clearInterval(t);
  }, [load]);

  const checkNow = async () => {
    setChecking(true);
    try {
      setStatus(await getJson('/api/status/refresh', { method: 'POST' }));
    } catch (e) {
      setError(e.message);
    } finally {
      setChecking(false);
    }
  };

  const groups = samples.reduce((acc, s) => {
    const key = s.app || 'Other';
    (acc[key] ||= []).push(s);
    return acc;
  }, {});

  const apps = status?.apps || [];
  const down = apps.filter((a) => a.state === 'DOWN').length;

  return (
    <div className="status-page">
      <div className="page-head">
        <div>
          <h1>App status</h1>
          <p className="lead">
            {status
              ? down === 0
                ? `All ${apps.length} apps are responding. Checked every ${status.intervalMs / 1000} seconds.`
                : `${down} of ${apps.length} apps are offline. Checked every ${status.intervalMs / 1000} seconds.`
              : 'Loading…'}
          </p>
        </div>
        <button type="button" className="btn" onClick={checkNow} disabled={checking}>
          {checking ? 'Checking…' : 'Check now'}
        </button>
      </div>

      {error && <div className="notice error">Could not load status: {error}</div>}
      {status && apps.length === 0 && <div className="notice">No apps configured. Add them under qa-hub.monitor.apps in monitor.yml.</div>}

      <div className="tiles">
        {apps.map((a) => <AppTile key={a.name} app={a} />)}
      </div>

      <section className="samples">
        <h2>Samples</h2>
        <p className="muted">Reference payloads for each app. Open one to see it.</p>
        {samples.length === 0 && <p className="muted">No samples configured. Add them in samples.yml.</p>}
        {Object.entries(groups).map(([app, list]) => (
          <div key={app} className="sample-group">
            <h3>{app}</h3>
            {list.map((s) => <SampleItem key={s.id} sample={s} />)}
          </div>
        ))}
      </section>
    </div>
  );
}

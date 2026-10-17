import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getJson } from '../api.js';
import EndpointDetail from '../components/EndpointDetail.jsx';

export default function StubsPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [catalog, setCatalog] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    getJson('/api/catalog').then(setCatalog).catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    if (catalog && !id && catalog.endpoints.length) navigate(`/stubs/${catalog.endpoints[0].id}`, { replace: true });
  }, [catalog, id, navigate]);

  if (error) return <div className="notice error">Could not load stubs: {error}. Is the backend running?</div>;
  if (!catalog) return <p className="muted">Loading stubs…</p>;
  if (!catalog.endpoints.length) {
    return <div className="notice">No stub endpoints yet. Add one under qa-hub.stub.endpoints in stubs.yml and restart.</div>;
  }

  const selected = catalog.endpoints.find((e) => e.id === id) || catalog.endpoints[0];

  return (
    <div className="stubs">
      <aside className="sidebar">
        <h2 className="sidebar-title">Endpoints</h2>
        <ul>
          {catalog.endpoints.map((e) => (
            <li key={e.id}>
              <Link to={`/stubs/${e.id}`} className={e.id === selected.id ? 'ep active' : 'ep'}>
                <span className={`method m-${e.method.toLowerCase()}`}>{e.method}</span>
                <span className="ep-text">
                  <span className="ep-name">{e.name}</span>
                  <span className="ep-path">{e.url}</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
        <div className="sidebar-help">
          <p>Force any status with the <code>{catalog.statusHeader}</code> header.</p>
          <p>Slow any call with <code>{catalog.delayHeader}</code> in milliseconds.</p>
        </div>
      </aside>
      <EndpointDetail key={selected.id} endpoint={selected} catalog={catalog} />
    </div>
  );
}

import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout.jsx';
import StubsPage from './pages/StubsPage.jsx';
import StatusPage from './pages/StatusPage.jsx';

export default function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Navigate to="/stubs" replace />} />
        <Route path="/stubs" element={<StubsPage />} />
        <Route path="/stubs/:id" element={<StubsPage />} />
        <Route path="/status" element={<StatusPage />} />
        <Route path="*" element={<Navigate to="/stubs" replace />} />
      </Routes>
    </Layout>
  );
}

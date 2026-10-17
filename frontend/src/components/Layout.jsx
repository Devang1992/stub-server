import { NavLink } from 'react-router-dom';

export default function Layout({ children }) {
  return (
    <div className="shell">
      <div className="topbar">Internal QA environment</div>
      <header className="header">
        <div className="header-inner">
          <NavLink to="/stubs" className="brand">
            <img src="/logo.svg" alt="Logo" className="brand-logo" />
            <span className="brand-name">QA Hub</span>
          </NavLink>
          <nav className="nav">
            <NavLink to="/stubs" className={({ isActive }) => (isActive ? 'active' : '')}>Stubs</NavLink>
            <NavLink to="/status" className={({ isActive }) => (isActive ? 'active' : '')}>App status</NavLink>
          </nav>
        </div>
      </header>
      <main className="main">{children}</main>
    </div>
  );
}

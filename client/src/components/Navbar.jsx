import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

const Navbar = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const homeLink = isAuthenticated
    ? user?.role === 'TEACHER'
      ? '/teacher'
      : '/student'
    : '/login';

  return (
    <header className="navbar">
      <NavLink to={homeLink} className="brand">
        <span className="brand-icon">⚡</span>
        <span>CodePulse</span>
      </NavLink>

      <nav>
        <ul className="nav-links">
          {!isAuthenticated ? (
            <>
              <li>
                <NavLink
                  to="/login"
                  className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                >
                  Sign In
                </NavLink>
              </li>
              <li>
                <NavLink
                  to="/register"
                  className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                >
                  Register
                </NavLink>
              </li>
            </>
          ) : (
            <>
              {user?.role === 'TEACHER' ? (
                <li>
                  <NavLink
                    to="/teacher"
                    className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                  >
                    Teacher Dashboard
                  </NavLink>
                </li>
              ) : (
                <li>
                  <NavLink
                    to="/student"
                    className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                  >
                    Student Dashboard
                  </NavLink>
                </li>
              )}
              <li className="user-info">
                <span className="user-tag">
                  <span>{user?.name || 'User'}</span>
                  <span className="role-badge">{user?.role}</span>
                </span>
                <button
                  type="button"
                  className="btn-logout"
                  onClick={handleLogout}
                  title="Sign out of your account"
                >
                  Logout
                </button>
              </li>
            </>
          )}
        </ul>
      </nav>
    </header>
  );
};

export default Navbar;

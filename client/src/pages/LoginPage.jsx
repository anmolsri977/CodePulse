import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../hooks/useAuth';

const LoginPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const successMessage = location.state?.successMessage || '';

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage('');
    setLoading(true);

    try {
      const response = await api.post('/auth/login', {
        email: email.trim(),
        password,
      });

      const { token, id, name, email: userEmail, role } = response.data;

      // Update centralized auth state and localStorage
      login(token, { id, name, email: userEmail, role });

      // Navigate to role-specific dashboard
      if (role === 'TEACHER') {
        navigate('/teacher', { replace: true });
      } else {
        navigate('/student', { replace: true });
      }
    } catch (err) {
      let msg = 'Invalid email or password. Please try again.';
      if (err.response && err.response.data) {
        const data = err.response.data;
        if (typeof data === 'string') {
          msg = data;
        } else if (data.message) {
          msg = data.message;
        } else if (data.error) {
          msg = data.error;
        } else if (Array.isArray(data.errors) && data.errors.length > 0) {
          msg = data.errors.map((e) => e.defaultMessage || e.message).join(', ');
        }
      } else if (err.message) {
        msg = err.message;
      }
      setErrorMessage(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="placeholder-page">
      <div className="card">
        <span className="badge">Welcome Back</span>
        <h1 className="page-title">Sign In</h1>
        <p className="page-desc">
          Enter your credentials to access your live coding classroom.
        </p>

        {successMessage && (
          <div className="alert alert-success">
            <span>✓</span>
            <span>{successMessage}</span>
          </div>
        )}

        {errorMessage && (
          <div className="alert alert-error">
            <span>⚠</span>
            <span>{errorMessage}</span>
          </div>
        )}

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label" htmlFor="email">Email Address</label>
            <input
              id="email"
              type="email"
              className="form-input"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
              disabled={loading}
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              className="form-input"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
              disabled={loading}
            />
          </div>

          <button
            type="submit"
            className="btn btn-primary btn-block"
            disabled={loading}
          >
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>

        <p className="auth-footer-text">
          Don't have an account?
          <Link to="/register" className="auth-link">
            Create account
          </Link>
        </p>
      </div>
    </div>
  );
};

export default LoginPage;

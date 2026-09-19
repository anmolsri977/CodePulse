import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../services/api';

const RegisterPage = () => {
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('STUDENT');
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage('');
    setLoading(true);

    try {
      await api.post('/auth/register', {
        name: name.trim(),
        email: email.trim(),
        password,
        role,
      });

      // On successful registration, redirect to /login with a success notification
      navigate('/login', {
        state: { successMessage: 'Account created successfully! Please sign in with your credentials.' },
        replace: true,
      });
    } catch (err) {
      let msg = 'Failed to create account. Please check your inputs and try again.';
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
        <span className="badge">Get Started</span>
        <h1 className="page-title">Create Account</h1>
        <p className="page-desc">
          Join CodePulse as a teacher or student for interactive collaborative coding.
        </p>

        {errorMessage && (
          <div className="alert alert-error">
            <span>⚠</span>
            <span>{errorMessage}</span>
          </div>
        )}

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label" htmlFor="name">Full Name</label>
            <input
              id="name"
              type="text"
              className="form-input"
              placeholder="e.g. Alan Turing"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              disabled={loading}
            />
          </div>

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
              minLength={6}
              autoComplete="new-password"
              disabled={loading}
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="role">Account Role</label>
            <select
              id="role"
              className="form-select"
              value={role}
              onChange={(e) => setRole(e.target.value)}
              disabled={loading}
            >
              <option value="STUDENT">Student &mdash; Join rooms and solve challenges</option>
              <option value="TEACHER">Teacher &mdash; Host rooms and broadcast code</option>
            </select>
          </div>

          <button
            type="submit"
            className="btn btn-primary btn-block"
            disabled={loading}
          >
            {loading ? 'Creating account...' : 'Create Account'}
          </button>
        </form>

        <p className="auth-footer-text">
          Already have an account?
          <Link to="/login" className="auth-link">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
};

export default RegisterPage;

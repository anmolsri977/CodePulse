import React from 'react';
import { Link } from 'react-router-dom';

const NotFoundPage = () => {
  return (
    <div className="placeholder-page">
      <div className="card">
        <span className="badge" style={{ background: 'rgba(239, 68, 68, 0.15)', color: '#fca5a5', borderColor: 'rgba(239, 68, 68, 0.3)' }}>
          404 Not Found
        </span>
        <h1 className="page-title">Page Not Found</h1>
        <p className="page-desc">
          The requested page does not exist or has been moved.
        </p>
        <Link to="/login" className="nav-link active" style={{ display: 'inline-block', marginTop: '1rem' }}>
          Back to Login
        </Link>
      </div>
    </div>
  );
};

export default NotFoundPage;

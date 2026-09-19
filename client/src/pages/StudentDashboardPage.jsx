import React from 'react';
import { useAuth } from '../hooks/useAuth';
import JoinRoomForm from '../components/JoinRoomForm';

const StudentDashboardPage = () => {
  const { user } = useAuth();

  return (
    <div className="dashboard-container">
      {/* Header */}
      <div className="dashboard-header">
        <div className="dashboard-greeting">
          <h1 className="dashboard-user-name">Welcome, {user?.name || 'Student'}</h1>
          <p className="dashboard-subtitle">
            Join your instructor's live classroom to sync code and submit challenge solutions.
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span className="role-badge" style={{ fontSize: '0.85rem', padding: '0.35rem 0.8rem' }}>
            STUDENT PORTAL
          </span>
        </div>
      </div>

      {/* Join Classroom Section */}
      <div className="dashboard-section" style={{ marginTop: '1rem' }}>
        <JoinRoomForm />
      </div>
    </div>
  );
};

export default StudentDashboardPage;

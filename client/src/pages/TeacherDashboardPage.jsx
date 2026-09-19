import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import api from '../services/api';
import CreateRoomForm from '../components/CreateRoomForm';
import RoomCard from '../components/RoomCard';

const TeacherDashboardPage = () => {
  const { user } = useAuth();
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [newlyCreatedRoom, setNewlyCreatedRoom] = useState(null);

  const fetchRooms = async () => {
    setError('');
    try {
      const response = await api.get('/rooms/my');
      setRooms(response.data || []);
    } catch (err) {
      let msg = 'Failed to load your classrooms.';
      if (err.response && err.response.data) {
        const data = err.response.data;
        if (typeof data === 'string') msg = data;
        else if (data.message) msg = data.message;
      }
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRooms();
  }, []);

  const handleRoomCreated = (newRoom) => {
    setNewlyCreatedRoom(newRoom);
    setRooms((prev) => [newRoom, ...prev]);
  };

  const handleRoomClosed = (updatedRoom) => {
    setRooms((prev) =>
      prev.map((r) => (r.roomCode === updatedRoom.roomCode ? updatedRoom : r))
    );
    if (newlyCreatedRoom?.roomCode === updatedRoom.roomCode) {
      setNewlyCreatedRoom(updatedRoom);
    }
  };

  return (
    <div className="dashboard-container">
      {/* Header */}
      <div className="dashboard-header">
        <div className="dashboard-greeting">
          <h1 className="dashboard-user-name">Welcome, {user?.name || 'Teacher'}</h1>
          <p className="dashboard-subtitle">
            Host live coding rooms, collaborate with students, and launch interactive challenges.
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span className="role-badge" style={{ fontSize: '0.85rem', padding: '0.35rem 0.8rem' }}>
            TEACHER PORTAL
          </span>
        </div>
      </div>

      {/* Prominent Banner for Newly Created Room */}
      {newlyCreatedRoom && (
        <div className="new-room-banner">
          <div className="new-room-info">
            <span className="new-room-label">Classroom Ready &bull; Share Code with Students</span>
            <span className="new-room-title">{newlyCreatedRoom.title}</span>
          </div>
          <div className="new-room-code-wrapper">
            <span className="new-room-code">{newlyCreatedRoom.roomCode}</span>
            <Link
              to={`/room/${newlyCreatedRoom.roomCode}`}
              className="btn btn-primary"
            >
              Enter Classroom &rarr;
            </Link>
          </div>
        </div>
      )}

      {/* Create Room Form */}
      <div className="dashboard-section">
        <CreateRoomForm onRoomCreated={handleRoomCreated} />
      </div>

      {/* Teacher's Rooms List */}
      <div className="dashboard-section">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 className="section-title">
            <span>Your Classrooms</span>
            <span style={{ fontSize: '0.9rem', color: 'var(--text-muted)', fontWeight: 'normal' }}>
              ({rooms.length})
            </span>
          </h2>
          <button
            type="button"
            className="btn btn-outline btn-sm"
            onClick={fetchRooms}
            disabled={loading}
            title="Refresh room list"
          >
            {loading ? 'Refreshing...' : '↻ Refresh'}
          </button>
        </div>

        {error && (
          <div className="alert alert-error">
            <span>⚠</span>
            <span>{error}</span>
          </div>
        )}

        {loading && rooms.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">⏳</div>
            <div className="empty-state-title">Loading classrooms...</div>
          </div>
        ) : rooms.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">🏫</div>
            <div className="empty-state-title">No classrooms created yet</div>
            <p>Use the form above to launch your first live coding room.</p>
          </div>
        ) : (
          <div className="rooms-grid">
            {rooms.map((room) => (
              <RoomCard
                key={room.id || room.roomCode}
                room={room}
                onRoomClosed={handleRoomClosed}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default TeacherDashboardPage;

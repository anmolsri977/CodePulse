import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';

const JoinRoomForm = () => {
  const navigate = useNavigate();
  const [roomCode, setRoomCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    const cleanCode = roomCode.trim().toUpperCase();
    if (!cleanCode) return;

    setErrorMessage('');
    setLoading(true);

    try {
      const response = await api.post(`/rooms/join/${cleanCode}`);
      const joinedRoom = response.data;

      // Navigate to the joined classroom
      navigate(`/room/${joinedRoom.roomCode}`);
    } catch (err) {
      let msg = 'Failed to join room. Please verify the room code and try again.';
      if (err.response && err.response.data) {
        const data = err.response.data;
        if (typeof data === 'string') {
          msg = data;
        } else if (data.message) {
          msg = data.message;
        } else if (data.error) {
          msg = data.error;
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
    <div className="card" style={{ maxWidth: '480px', margin: '0 auto' }}>
      <span className="badge">Student Portal</span>
      <h2 className="page-title">Join a Classroom</h2>
      <p className="page-desc">
        Enter the 6-character room code provided by your teacher to enter the live session.
      </p>

      {errorMessage && (
        <div className="alert alert-error">
          <span>⚠</span>
          <span>{errorMessage}</span>
        </div>
      )}

      <form className="auth-form" onSubmit={handleSubmit}>
        <div className="form-group">
          <label className="form-label" htmlFor="roomCodeInput">
            Room Code
          </label>
          <input
            id="roomCodeInput"
            type="text"
            className="form-input"
            placeholder="e.g. ABC123"
            value={roomCode}
            onChange={(e) => setRoomCode(e.target.value.toUpperCase().slice(0, 6))}
            maxLength={6}
            required
            autoComplete="off"
            spellCheck="false"
            disabled={loading}
            style={{
              fontFamily: 'var(--font-mono)',
              fontSize: '1.25rem',
              letterSpacing: '0.15em',
              textAlign: 'center',
              textTransform: 'uppercase',
            }}
          />
        </div>

        <button
          type="submit"
          className="btn btn-primary btn-block"
          disabled={loading || roomCode.trim().length === 0}
        >
          {loading ? 'Joining Room...' : 'Enter Classroom \u2192'}
        </button>
      </form>
    </div>
  );
};

export default JoinRoomForm;

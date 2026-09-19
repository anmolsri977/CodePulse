import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';

const RoomCard = ({ room, onRoomClosed }) => {
  const [closing, setClosing] = useState(false);
  const [closeError, setCloseError] = useState('');

  const isClosed = room.status === 'CLOSED';

  const handleClose = async () => {
    if (isClosed || closing) return;

    if (!window.confirm(`Are you sure you want to close "${room.title}" (${room.roomCode})? Students will no longer be able to submit.`)) {
      return;
    }

    setClosing(true);
    setCloseError('');

    try {
      const response = await api.patch(`/rooms/${room.roomCode}/close`);
      if (onRoomClosed) {
        onRoomClosed(response.data);
      }
    } catch (err) {
      let msg = 'Failed to close room.';
      if (err.response && err.response.data) {
        const data = err.response.data;
        if (typeof data === 'string') msg = data;
        else if (data.message) msg = data.message;
      }
      setCloseError(msg);
    } finally {
      setClosing(false);
    }
  };

  const formatDate = (isoString) => {
    if (!isoString) return '';
    try {
      const d = new Date(isoString);
      return d.toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  return (
    <div className={`room-card ${isClosed ? 'closed' : ''}`}>
      <div>
        <div className="room-card-header">
          <h3 className="room-card-title">{room.title}</h3>
          <span className={`status-badge ${isClosed ? 'status-closed' : 'status-active'}`}>
            {room.status}
          </span>
        </div>

        <div className="room-card-details" style={{ marginTop: '1rem' }}>
          <div className="room-code-row">
            <span>Room Code:</span>
            <span className="room-code-tag">{room.roomCode}</span>
          </div>
          {room.createdAt && (
            <div style={{ color: 'var(--text-muted)' }}>
              Created: {formatDate(room.createdAt)}
            </div>
          )}
        </div>

        {closeError && (
          <div className="alert alert-error" style={{ marginTop: '0.75rem', padding: '0.5rem 0.75rem' }}>
            <span>{closeError}</span>
          </div>
        )}
      </div>

      <div className="room-card-actions">
        {!isClosed ? (
          <>
            <Link
              to={`/room/${room.roomCode}`}
              className="btn btn-primary btn-sm"
              style={{ flex: 1 }}
            >
              Enter Room &rarr;
            </Link>
            <button
              type="button"
              className="btn btn-danger btn-sm"
              onClick={handleClose}
              disabled={closing}
              title="Close this classroom session"
            >
              {closing ? 'Closing...' : 'Close'}
            </button>
          </>
        ) : (
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            disabled
            style={{ width: '100%', opacity: 0.5 }}
          >
            Session Closed
          </button>
        )}
      </div>
    </div>
  );
};

export default RoomCard;

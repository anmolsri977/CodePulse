import React, { useState } from 'react';
import api from '../services/api';

const CreateRoomForm = ({ onRoomCreated }) => {
  const [title, setTitle] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!title.trim()) return;

    setErrorMessage('');
    setLoading(true);

    try {
      const response = await api.post('/rooms', {
        title: title.trim(),
      });
      setTitle('');
      if (onRoomCreated) {
        onRoomCreated(response.data);
      }
    } catch (err) {
      let msg = 'Failed to create room. Please try again.';
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
    <div className="card">
      <h2 className="section-title">Create a Classroom</h2>
      <p className="page-desc" style={{ marginBottom: '1.25rem' }}>
        Start a new interactive coding session. A unique 6-character code will be generated for your students.
      </p>

      {errorMessage && (
        <div className="alert alert-error">
          <span>⚠</span>
          <span>{errorMessage}</span>
        </div>
      )}

      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
        <input
          type="text"
          className="form-input"
          placeholder="e.g. Advanced Algorithms - Section A"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
          disabled={loading}
          style={{ flex: '1 1 280px' }}
        />
        <button
          type="submit"
          className="btn btn-primary"
          disabled={loading || !title.trim()}
        >
          {loading ? 'Creating...' : '+ Create Room'}
        </button>
      </form>
    </div>
  );
};

export default CreateRoomForm;

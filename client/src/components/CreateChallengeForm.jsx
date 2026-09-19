import React, { useState } from 'react';
import api from '../services/api';

const CreateChallengeForm = ({ roomCode, onChallengeCreated }) => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [skeleton, setSkeleton] = useState('public class Solution {\n    // Write your code here\n}\n');
  const [timeLimit, setTimeLimit] = useState(30);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!title.trim()) return;

    setErrorMessage('');
    setLoading(true);

    try {
      const response = await api.post(`/rooms/${roomCode}/challenges`, {
        title: title.trim(),
        description: description.trim(),
        skeleton,
        timeLimit: Number(timeLimit) || 30,
      });

      setTitle('');
      setDescription('');
      setSkeleton('public class Solution {\n    // Write your code here\n}\n');
      setTimeLimit(30);

      if (onChallengeCreated) {
        onChallengeCreated(response.data);
      }
    } catch (err) {
      let msg = 'Failed to create challenge.';
      if (err.response && err.response.data) {
        const data = err.response.data;
        if (typeof data === 'string') msg = data;
        else if (data.message) msg = data.message;
        else if (data.error) msg = data.error;
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
      <h3 className="section-title">
        <span>+ Create Coding Challenge</span>
      </h3>
      <p className="page-desc" style={{ marginBottom: '1rem' }}>
        Create a challenge for students in this room. You can launch it to all students when ready.
      </p>

      {errorMessage && (
        <div className="alert alert-error">
          <span>⚠</span>
          <span>{errorMessage}</span>
        </div>
      )}

      <form className="auth-form" onSubmit={handleSubmit}>
        <div className="form-group">
          <label className="form-label" htmlFor="challengeTitle">Title *</label>
          <input
            id="challengeTitle"
            type="text"
            className="form-input"
            placeholder="e.g. Reverse a Linked List"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            disabled={loading}
          />
        </div>

        <div className="form-group">
          <label className="form-label" htmlFor="challengeDesc">Problem Description</label>
          <textarea
            id="challengeDesc"
            className="form-input textarea-input"
            placeholder="Describe the task, inputs, expected outputs, and constraints..."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            disabled={loading}
          />
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
          <div className="form-group">
            <label className="form-label" htmlFor="challengeTime">Time Limit (minutes)</label>
            <input
              id="challengeTime"
              type="number"
              min="1"
              max="180"
              className="form-input"
              value={timeLimit}
              onChange={(e) => setTimeLimit(e.target.value)}
              disabled={loading}
            />
          </div>
        </div>

        <div className="form-group">
          <label className="form-label" htmlFor="challengeSkeleton">Starter Code (Skeleton)</label>
          <textarea
            id="challengeSkeleton"
            className="form-input textarea-input"
            style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}
            value={skeleton}
            onChange={(e) => setSkeleton(e.target.value)}
            disabled={loading}
          />
        </div>

        <button
          type="submit"
          className="btn btn-primary"
          disabled={loading || !title.trim()}
          style={{ alignSelf: 'flex-start' }}
        >
          {loading ? 'Creating...' : 'Save Challenge'}
        </button>
      </form>
    </div>
  );
};

export default CreateChallengeForm;

import React, { useState } from 'react';
import api from '../services/api';

const SubmissionStatusPanel = ({ submissions = [], challenges = [], connectionStatus }) => {
  const [selectedSubmission, setSelectedSubmission] = useState(null);
  const [loadingCodeId, setLoadingCodeId] = useState(null);
  const [fetchError, setFetchError] = useState('');

  const handleViewCode = async (submissionId) => {
    if (!submissionId) return;
    setLoadingCodeId(submissionId);
    setFetchError('');
    try {
      const response = await api.get(`/submissions/${submissionId}`);
      setSelectedSubmission(response.data);
    } catch (err) {
      let msg = 'Failed to load submission code.';
      if (err.response?.data?.message) msg = err.response.data.message;
      setFetchError(msg);
    } finally {
      setLoadingCodeId(null);
    }
  };
  // Constraint 2: Derive challenge title strictly from challenges fetched by RoomPage using challengeId
  const getChallengeTitle = (challengeId) => {
    if (!challengeId) return 'General Challenge';
    const found = challenges.find((c) => c.id === challengeId);
    return found ? found.title : `Challenge #${challengeId}`;
  };

  const formatDate = (isoString) => {
    if (!isoString) return '';
    try {
      return new Date(isoString).toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  const getScoreBadge = (score) => {
    // Constraint 4: Defensive score handling, support null/missing without crashing, never fabricate
    if (score === null || score === undefined) {
      return (
        <span className="score-badge score-badge-fallback" style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem' }}>
          Saved (Fallback)
        </span>
      );
    }

    const num = Number(score);
    let badgeClass = 'score-badge score-badge-low';
    if (num >= 80) badgeClass = 'score-badge score-badge-high';
    else if (num >= 50) badgeClass = 'score-badge score-badge-mid';

    return (
      <span className={badgeClass} style={{ fontSize: '0.8rem', padding: '0.2rem 0.55rem' }}>
        ★ {num}/100
      </span>
    );
  };

  const isRecent = (sub) => {
    if (!sub.receivedAt) return false;
    return Date.now() - sub.receivedAt < 20000; // Received in last 20 seconds
  };

  return (
    <div className="card submission-panel">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 className="section-title">
          <span>📡 Live Submissions</span>
          <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)', fontWeight: 'normal' }}>
            ({submissions.length})
          </span>
        </h3>
        <span style={{ fontSize: '0.75rem', color: connectionStatus === 'connected' ? '#10b981' : 'var(--text-muted)' }}>
          {connectionStatus === 'connected' ? '● Real-time sync' : '○ Offline'}
        </span>
      </div>

      <p className="page-desc" style={{ marginBottom: '0.5rem', fontSize: '0.875rem' }}>
        Real-time feed of challenge submissions from connected students in this classroom.
      </p>

      {submissions.length === 0 ? (
        <div className="empty-state" style={{ padding: '2.5rem 1rem' }}>
          <div className="empty-state-icon">📥</div>
          <div className="empty-state-title">No submissions yet</div>
          <p style={{ fontSize: '0.85rem' }}>
            Submissions and AI evaluation scores will appear here in real time as students submit solutions.
          </p>
        </div>
      ) : (
        <div className="submission-feed">
          {submissions.map((sub, index) => {
            const recent = isRecent(sub);
            const challengeTitle = getChallengeTitle(sub.challengeId);

            return (
              <div
                key={sub.submissionId || `${sub.studentId}-${index}`}
                className={`submission-item ${recent ? 'submission-item-new' : ''}`}
              >
                <div className="submission-item-header">
                  <div className="submission-student-group">
                    <span>{sub.studentName || `Student #${sub.studentId}`}</span>
                    {recent && <span className="badge-new">NEW</span>}
                  </div>
                  <span className="submission-challenge-tag" title={challengeTitle}>
                    {challengeTitle}
                  </span>
                </div>

                <div className="submission-item-body">
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                    {getScoreBadge(sub.score)}
                    <button
                      type="button"
                      className="btn btn-sm btn-secondary"
                      onClick={() => handleViewCode(sub.submissionId)}
                      disabled={loadingCodeId === sub.submissionId}
                      style={{ fontSize: '0.75rem', padding: '0.2rem 0.6rem' }}
                      title="Inspect student's submitted code"
                    >
                      {loadingCodeId === sub.submissionId ? 'Loading...' : '🔍 View Code'}
                    </button>
                  </div>
                  <div className="submission-meta">
                    <span>Sub #{sub.submissionId}</span> &bull; <span>{formatDate(sub.submittedAt)}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {fetchError && (
        <div className="alert alert-error" style={{ marginTop: '0.75rem' }}>
          <span>⚠</span>
          <span>{fetchError}</span>
        </div>
      )}

      {/* Code Inspection Modal */}
      {selectedSubmission && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.75)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '1rem',
          }}
          onClick={() => setSelectedSubmission(null)}
        >
          <div
            className="card"
            style={{
              maxWidth: '750px',
              width: '100%',
              maxHeight: '90vh',
              overflowY: 'auto',
              border: '1px solid var(--border-color)',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.5)',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1rem' }}>
              <div>
                <span className="badge" style={{ marginBottom: '0.25rem' }}>
                  {selectedSubmission.challengeTitle || 'Challenge Submission'}
                </span>
                <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                  {selectedSubmission.studentName} ({selectedSubmission.studentEmail})
                </h3>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                  Submission #{selectedSubmission.id} &bull; {formatDate(selectedSubmission.submittedAt)}
                </div>
              </div>
              <button
                type="button"
                className="btn btn-sm btn-secondary"
                onClick={() => setSelectedSubmission(null)}
                style={{ fontSize: '0.85rem', padding: '0.3rem 0.75rem' }}
              >
                ✕ Close
              </button>
            </div>

            <div style={{ marginBottom: '1rem' }}>
              <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>
                AI Evaluation:
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
                {getScoreBadge(selectedSubmission.score)}
              </div>
              {selectedSubmission.aiFeedback && (
                <div className="ai-feedback-box" style={{ fontSize: '0.875rem' }}>
                  {selectedSubmission.aiFeedback}
                </div>
              )}
            </div>

            <div>
              <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>
                Submitted Code:
              </div>
              <pre
                className="skeleton-preview"
                style={{
                  maxHeight: '350px',
                  overflowY: 'auto',
                  background: '#0d1117',
                  border: '1px solid #30363d',
                  padding: '1rem',
                  borderRadius: '6px',
                  fontSize: '0.85rem',
                  fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
                  whiteSpace: 'pre-wrap',
                }}
              >
                {selectedSubmission.code}
              </pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SubmissionStatusPanel;

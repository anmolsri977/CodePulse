import React from 'react';

const ChallengeCard = ({ challenge, isActive, onStartChallenge, onEndChallenge, onDeleteChallenge }) => {
  const isEnded = Boolean(challenge?.endedAt);
  const isCurrentlyActive = isActive && !isEnded;

  const handleEndClick = () => {
    if (window.confirm('End this challenge for all students? Submissions will be closed immediately.')) {
      onEndChallenge(challenge.id);
    }
  };

  const handleDeleteClick = () => {
    if (window.confirm('Delete this challenge? Any associated student submissions will also be deleted.')) {
      onDeleteChallenge(challenge.id);
    }
  };

  return (
    <div className="challenge-item" style={isCurrentlyActive ? { borderColor: 'var(--accent-primary)', boxShadow: '0 0 10px rgba(99, 102, 241, 0.2)' } : {}}>
      <div className="challenge-item-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
          <h4 className="challenge-item-title">{challenge.title}</h4>
          {isCurrentlyActive && (
            <span className="status-badge status-active" style={{ fontSize: '0.65rem' }}>
              CURRENTLY ACTIVE
            </span>
          )}
          {isEnded && (
            <span className="status-badge" style={{ fontSize: '0.65rem', background: 'rgba(239, 68, 68, 0.15)', color: '#ef4444', borderColor: 'rgba(239, 68, 68, 0.3)' }}>
              ENDED
            </span>
          )}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
          {challenge.timeLimit && (
            <span className="challenge-meta-pill">
              ⏱ {challenge.timeLimit} mins
            </span>
          )}
          {isCurrentlyActive && onEndChallenge && (
            <button
              type="button"
              className="btn btn-sm btn-outline"
              style={{ borderColor: 'rgba(239, 68, 68, 0.5)', color: '#ef4444' }}
              onClick={handleEndClick}
              title="End challenge for all students immediately"
            >
              ⏹ End Challenge
            </button>
          )}
          {!isCurrentlyActive && onDeleteChallenge && (
            <button
              type="button"
              className="btn btn-sm btn-outline"
              style={{ borderColor: 'rgba(239, 68, 68, 0.4)', color: '#ef4444' }}
              onClick={handleDeleteClick}
              title="Delete this challenge and its submissions"
            >
              🗑 Delete
            </button>
          )}
          {onStartChallenge && (
            <button
              type="button"
              className={`btn btn-sm ${isCurrentlyActive ? 'btn-secondary' : 'btn-primary'}`}
              onClick={() => onStartChallenge(challenge.id)}
            >
              {isCurrentlyActive ? '⚡ Re-broadcast' : isEnded ? '🚀 Restart Challenge' : '🚀 Start Challenge'}
            </button>
          )}
        </div>
      </div>

      {challenge.description && (
        <p className="challenge-item-desc">{challenge.description}</p>
      )}

      {challenge.skeleton && (
        <div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '0.25rem' }}>
            Starter Code:
          </div>
          <pre className="skeleton-preview">{challenge.skeleton}</pre>
        </div>
      )}
    </div>
  );
};

export default ChallengeCard;

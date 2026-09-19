import React from 'react';

const ChallengeCard = ({ challenge, isActive, onStartChallenge }) => {
  return (
    <div className="challenge-item" style={isActive ? { borderColor: 'var(--accent-primary)', boxShadow: '0 0 10px rgba(99, 102, 241, 0.2)' } : {}}>
      <div className="challenge-item-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
          <h4 className="challenge-item-title">{challenge.title}</h4>
          {isActive && (
            <span className="status-badge status-active" style={{ fontSize: '0.65rem' }}>
              CURRENTLY ACTIVE
            </span>
          )}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          {challenge.timeLimit && (
            <span className="challenge-meta-pill">
              ⏱ {challenge.timeLimit} mins
            </span>
          )}
          {onStartChallenge && (
            <button
              type="button"
              className={`btn btn-sm ${isActive ? 'btn-secondary' : 'btn-primary'}`}
              onClick={() => onStartChallenge(challenge.id)}
            >
              {isActive ? '⚡ Re-broadcast' : '🚀 Start Challenge'}
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

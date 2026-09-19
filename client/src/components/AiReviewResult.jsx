import React from 'react';

const AiReviewResult = ({ review }) => {
  if (!review) return null;

  const hasScore = review.score !== null && review.score !== undefined;
  const score = Number(review.score);

  const getScoreBadgeClass = () => {
    if (!hasScore) return 'score-badge score-badge-fallback';
    if (score >= 80) return 'score-badge score-badge-high';
    if (score >= 50) return 'score-badge score-badge-mid';
    return 'score-badge score-badge-low';
  };

  const formatDate = (isoString) => {
    if (!isoString) return '';
    try {
      return new Date(isoString).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="ai-review-card">
      <div className="ai-review-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <h3 style={{ fontSize: '1.15rem', fontWeight: 600, color: 'var(--text-primary)' }}>
            🤖 AI Code Review
          </h3>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
            Submission #{review.id} &bull; {formatDate(review.submittedAt)}
          </span>
        </div>

        <div>
          {hasScore ? (
            <span className={getScoreBadgeClass()}>
              ★ {score} / 100
            </span>
          ) : (
            <span className={getScoreBadgeClass()}>
              ℹ️ AI Evaluation Pending / Unavailable
            </span>
          )}
        </div>
      </div>

      <div className="ai-feedback-box">
        {review.feedback || 'Your code has been submitted successfully.'}
      </div>
    </div>
  );
};

export default AiReviewResult;

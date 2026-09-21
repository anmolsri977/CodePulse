import React, { useState, useEffect, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import Editor from '@monaco-editor/react';
import { Client } from '@stomp/stompjs';
import { useAuth } from '../hooks/useAuth';
import { getToken } from '../utils/auth';
import api, { getWsUrl } from '../services/api';
import CreateChallengeForm from '../components/CreateChallengeForm';
import ChallengeCard from '../components/ChallengeCard';
import AiReviewResult from '../components/AiReviewResult';
import SubmissionStatusPanel from '../components/SubmissionStatusPanel';

const RoomPage = () => {
  const { roomCode } = useParams();
  const { user } = useAuth();
  const isTeacher = user?.role === 'TEACHER';

  const defaultTeacherCode = `// Welcome to CodePulse Live Classroom!\n// Type here to broadcast code in real time to students.\n\npublic class Solution {\n    public static void main(String[] args) {\n        System.out.println("Hello, CodePulse!");\n    }\n}\n`;
  const defaultStudentTeacherCode = `// Connected to classroom: ${roomCode || ''}\n// Waiting for teacher live code synchronization...\n`;

  // 1. Completely separate states as required
  const [teacherLiveCode, setTeacherLiveCode] = useState(isTeacher ? defaultTeacherCode : defaultStudentTeacherCode);
  const [studentSolution, setStudentSolution] = useState('');
  const [currentChallenge, setCurrentChallenge] = useState(null);
  const [aiReview, setAiReview] = useState(null);
  const [remainingSeconds, setRemainingSeconds] = useState(null);

  // Challenge live countdown timer
  useEffect(() => {
    if (!currentChallenge?.startedAt || !currentChallenge?.timeLimit) {
      setRemainingSeconds(null);
      return;
    }

    const calculateRemaining = () => {
      const startTime = new Date(currentChallenge.startedAt).getTime();
      const durationMs = Number(currentChallenge.timeLimit) * 60 * 1000;
      const expiresAt = startTime + durationMs;
      const diffSec = Math.floor((expiresAt - Date.now()) / 1000);
      return Math.max(0, diffSec);
    };

    setRemainingSeconds(calculateRemaining());

    const interval = setInterval(() => {
      const remaining = calculateRemaining();
      setRemainingSeconds(remaining);
      if (remaining <= 0) {
        clearInterval(interval);
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [currentChallenge?.startedAt, currentChallenge?.timeLimit]);

  const isChallengeExpired = remainingSeconds !== null && remainingSeconds <= 0;

  const formatCountdown = (totalSeconds) => {
    if (totalSeconds === null || totalSeconds === undefined) return '';
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  };

  // Teacher-specific challenge & submission management
  const [challenges, setChallenges] = useState([]);
  const [activeChallengeId, setActiveChallengeId] = useState(null);
  const [submissions, setSubmissions] = useState([]);

  // Connection & submission status
  const [connectionStatus, setConnectionStatus] = useState('connecting');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');

  // Refs for STOMP lifecycle, debouncing, and challenge tracking
  const stompClientRef = useRef(null);
  const debounceTimerRef = useRef(null);
  const currentChallengeIdRef = useRef(null);

  // Fetch teacher's room challenges on mount
  useEffect(() => {
    if (isTeacher && roomCode) {
      const fetchChallenges = async () => {
        try {
          const res = await api.get(`/rooms/${roomCode}/challenges`);
          setChallenges(res.data || []);
        } catch (err) {
          console.error('Failed to load challenges for room', err);
        }
      };
      fetchChallenges();
    }
  }, [roomCode, isTeacher]);

  // WebSocket / STOMP Lifecycle (Reused single client)
  useEffect(() => {
    const token = getToken();
    const wsUrl = getWsUrl();
    const normalizedRoomCode = (roomCode || '').trim().toUpperCase();

    setConnectionStatus('connecting');

    const client = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: token ? `Bearer ${token}` : '',
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setConnectionStatus('connected');

        // Teacher subscriptions
        if (isTeacher) {
          // Subscribe to live student submission status broadcasts for this room
          client.subscribe(`/topic/room/${normalizedRoomCode}/submissions`, (message) => {
            try {
              const statusMsg = JSON.parse(message.body);
              if (statusMsg && statusMsg.submissionId) {
                setSubmissions((prev) => [
                  { ...statusMsg, receivedAt: Date.now() },
                  ...prev,
                ]);
              }
            } catch (err) {
              console.error('Failed to parse submission status message', err);
            }
          });
        }

        // Student subscriptions
        if (!isTeacher) {
          // 1. Subscribe to teacher's live code updates
          client.subscribe(`/topic/room/${normalizedRoomCode}/editor`, (message) => {
            try {
              const body = JSON.parse(message.body);
              if (body && typeof body.code === 'string') {
                setTeacherLiveCode(body.code);
              }
            } catch (err) {
              console.error('Failed to parse editor sync message', err);
            }
          });

          // 2. Subscribe to challenge broadcasts
          client.subscribe(`/topic/room/${normalizedRoomCode}/challenge`, (message) => {
            try {
              const broadcastChallenge = JSON.parse(message.body);
              if (broadcastChallenge && broadcastChallenge.id) {
                setCurrentChallenge(broadcastChallenge);

                // Constraint 3 & 4:
                // Only reset studentSolution if this is a genuinely new challenge.
                // Do NOT overwrite existing solution if same challenge is re-broadcast.
                if (currentChallengeIdRef.current !== broadcastChallenge.id) {
                  currentChallengeIdRef.current = broadcastChallenge.id;
                  setStudentSolution(broadcastChallenge.skeleton || '// Write your solution here...\n');
                  setAiReview(null); // Clear previous challenge review
                }
              }
            } catch (err) {
              console.error('Failed to parse challenge broadcast message', err);
            }
          });
        }
      },
      onStompError: (frame) => {
        console.error('STOMP Protocol Error:', frame?.headers?.['message']);
        setConnectionStatus('error');
      },
      onWebSocketClose: () => {
        setConnectionStatus('disconnected');
      },
      onWebSocketError: (event) => {
        console.error('WebSocket Transport Error:', event);
        setConnectionStatus('error');
      },
    });

    stompClientRef.current = client;
    client.activate();

    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
      if (client) {
        client.deactivate();
      }
      stompClientRef.current = null;
    };
  }, [roomCode, isTeacher]);

  // Teacher debounced live code sync (~400ms)
  const handleTeacherEditorChange = (newCode) => {
    if (!isTeacher) return;

    const updated = newCode || '';
    setTeacherLiveCode(updated);

    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }

    debounceTimerRef.current = setTimeout(() => {
      const client = stompClientRef.current;
      const normalizedRoomCode = (roomCode || '').trim().toUpperCase();

      if (client && client.connected) {
        client.publish({
          destination: `/app/room/${normalizedRoomCode}/editor`,
          body: JSON.stringify({
            code: updated,
            senderEmail: user?.email || '',
            timestamp: Date.now(),
          }),
        });
      }
    }, 400);
  };

  // Teacher starts a challenge via STOMP (client sends only challengeId)
  const handleStartChallenge = (challengeId) => {
    const client = stompClientRef.current;
    const normalizedRoomCode = (roomCode || '').trim().toUpperCase();

    if (client && client.connected) {
      client.publish({
        destination: `/app/room/${normalizedRoomCode}/challenge`,
        body: JSON.stringify({ challengeId }),
      });
      setActiveChallengeId(challengeId);
    }
  };

  const handleChallengeCreated = (newChallenge) => {
    setChallenges((prev) => [newChallenge, ...prev]);
  };

  // Student submits solution via REST
  const handleSubmitSolution = async () => {
    if (!currentChallenge?.id || !studentSolution.trim()) return;

    if (isChallengeExpired) {
      setSubmitError('Challenge time limit has expired. Submissions are closed.');
      return;
    }

    setSubmitting(true);
    setSubmitError('');

    try {
      const response = await api.post(`/challenges/${currentChallenge.id}/submit`, {
        code: studentSolution,
      });
      setAiReview(response.data);
    } catch (err) {
      let msg = 'Failed to submit solution. Please try again.';
      if (err.response && err.response.data) {
        const data = err.response.data;
        if (typeof data === 'string') msg = data;
        else if (data.message) msg = data.message;
        else if (data.error) msg = data.error;
      } else if (err.message) {
        msg = err.message;
      }
      setSubmitError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const renderStatusPill = () => {
    switch (connectionStatus) {
      case 'connected':
        return (
          <div className="connection-pill">
            <span className="connection-dot conn-connected" />
            <span>Connected</span>
          </div>
        );
      case 'connecting':
        return (
          <div className="connection-pill">
            <span className="connection-dot conn-connecting" />
            <span>Connecting...</span>
          </div>
        );
      case 'error':
        return (
          <div className="connection-pill">
            <span className="connection-dot conn-error" />
            <span>Connection Error</span>
          </div>
        );
      case 'disconnected':
      default:
        return (
          <div className="connection-pill">
            <span className="connection-dot conn-disconnected" />
            <span>Disconnected</span>
          </div>
        );
    }
  };

  const backLink = isTeacher ? '/teacher' : '/student';

  return (
    <div className="classroom-container">
      {/* Classroom Top Header */}
      <div className="classroom-header">
        <div className="classroom-header-left">
          <Link to={backLink} className="btn btn-outline btn-sm" title="Back to dashboard">
            &larr; Dashboard
          </Link>
          <div className="classroom-title-group">
            <div className="classroom-title">
              <span>Classroom</span>
              <span className="room-code-tag">{roomCode?.toUpperCase()}</span>
            </div>
          </div>
          <span className={`classroom-mode-badge ${isTeacher ? 'mode-teacher' : 'mode-student'}`}>
            {isTeacher ? 'Teacher Live Editor' : 'Student Live View'}
          </span>
        </div>

        <div className="classroom-header-right">
          {renderStatusPill()}
        </div>
      </div>

      {/* ========================================================= */}
      {/* TEACHER VIEW: Live Editor + Challenge Management         */}
      {/* ========================================================= */}
      {isTeacher ? (
        <div className="classroom-split">
          {/* Section 1: Live Teacher Code Editor */}
          <div className="editor-container">
            <div className="editor-toolbar">
              <div className="editor-toolbar-left">
                <span className="editor-lang-tag">Java</span>
                <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>Live Teacher Code</span>
              </div>
              <div className="editor-toolbar-right">
                <span className="editor-status-text" style={{ color: '#a5b4fc' }}>
                  ✏️ Live Sync Active (~400ms debounce)
                </span>
              </div>
            </div>

            <Editor
              height="58vh"
              defaultLanguage="java"
              language="java"
              theme="vs-dark"
              value={teacherLiveCode}
              onChange={handleTeacherEditorChange}
              options={{
                readOnly: false,
                fontSize: 14,
                fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
                minimap: { enabled: false },
                scrollBeyondLastLine: false,
                automaticLayout: true,
                tabSize: 4,
                wordWrap: 'on',
                lineNumbers: 'on',
                padding: { top: 12, bottom: 12 },
              }}
              loading={<div style={{ padding: '2rem', color: 'var(--text-muted)' }}>Loading Editor...</div>}
            />
          </div>

          {/* Section 2: Challenge Management & Live Submissions Dashboard */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: '1.5rem' }}>
            {/* Create Challenge Form */}
            <CreateChallengeForm
              roomCode={roomCode}
              onChallengeCreated={handleChallengeCreated}
            />

            {/* Existing Room Challenges */}
            <div className="card">
              <h3 className="section-title">
                <span>Room Challenges</span>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)', fontWeight: 'normal' }}>
                  ({challenges.length})
                </span>
              </h3>
              <p className="page-desc" style={{ marginBottom: '1rem' }}>
                Launch challenges to all connected students. Students will receive starter code in their solution workspace.
              </p>

              {challenges.length === 0 ? (
                <div className="empty-state" style={{ padding: '2rem 1rem' }}>
                  <div className="empty-state-icon">🎯</div>
                  <div className="empty-state-title">No challenges created yet</div>
                  <p style={{ fontSize: '0.85rem' }}>Use the form to create and launch coding tasks.</p>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', maxHeight: '520px', overflowY: 'auto' }}>
                  {challenges.map((ch) => (
                    <ChallengeCard
                      key={ch.id}
                      challenge={ch}
                      isActive={activeChallengeId === ch.id}
                      onStartChallenge={handleStartChallenge}
                    />
                  ))}
                </div>
              )}
            </div>

            {/* Live Submissions Feed for Teacher */}
            <SubmissionStatusPanel
              submissions={submissions}
              challenges={challenges}
              connectionStatus={connectionStatus}
            />
          </div>
        </div>
      ) : (
        /* ========================================================= */
        /* STUDENT VIEW: Live Teacher Code + Current Challenge + Solution */
        /* ========================================================= */
        <div className="classroom-split">
          {/* Section 1: Live Teacher Code (Read-Only) */}
          <div className="editor-container">
            <div className="editor-toolbar">
              <div className="editor-toolbar-left">
                <span className="editor-lang-tag">Java</span>
                <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>Live Teacher Code</span>
              </div>
              <div className="editor-toolbar-right">
                <span className="editor-status-text" style={{ color: '#67e8f9' }}>
                  🔒 Read-Only (Synchronized with Teacher)
                </span>
              </div>
            </div>

            <Editor
              height="35vh"
              defaultLanguage="java"
              language="java"
              theme="vs-dark"
              value={teacherLiveCode}
              options={{
                readOnly: true,
                fontSize: 13,
                fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
                minimap: { enabled: false },
                scrollBeyondLastLine: false,
                automaticLayout: true,
                tabSize: 4,
                wordWrap: 'on',
                lineNumbers: 'on',
                padding: { top: 8, bottom: 8 },
              }}
              loading={<div style={{ padding: '2rem', color: 'var(--text-muted)' }}>Loading Teacher Stream...</div>}
            />
          </div>

          {/* Section 2: Current Challenge Information */}
          {currentChallenge ? (
            <div className="active-challenge-banner">
              <div className="active-challenge-header">
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
                  <span className="badge" style={{ margin: 0 }}>ACTIVE CHALLENGE</span>
                  <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                    {currentChallenge.title}
                  </h2>
                </div>
                {currentChallenge.timeLimit && (
                  <span
                    className="challenge-meta-pill"
                    style={{
                      fontSize: '0.85rem',
                      fontWeight: 600,
                      background: isChallengeExpired
                        ? 'rgba(239, 68, 68, 0.2)'
                        : remainingSeconds !== null && remainingSeconds < 60
                        ? 'rgba(245, 158, 11, 0.2)'
                        : 'rgba(99, 102, 241, 0.15)',
                      color: isChallengeExpired
                        ? '#ef4444'
                        : remainingSeconds !== null && remainingSeconds < 60
                        ? '#f59e0b'
                        : '#818cf8',
                      border: isChallengeExpired ? '1px solid rgba(239, 68, 68, 0.4)' : undefined,
                    }}
                  >
                    {isChallengeExpired ? (
                      <>⏱ 00:00 (Time Expired)</>
                    ) : remainingSeconds !== null ? (
                      <>⏱ {formatCountdown(remainingSeconds)} remaining</>
                    ) : (
                      <>⏱ Time Limit: {currentChallenge.timeLimit} mins</>
                    )}
                  </span>
                )}
              </div>

              {currentChallenge.description && (
                <p style={{ color: 'var(--text-primary)', fontSize: '0.95rem', lineHeight: '1.6' }}>
                  {currentChallenge.description}
                </p>
              )}
            </div>
          ) : (
            <div className="card" style={{ textAlign: 'center', padding: '1.75rem' }}>
              <span style={{ fontSize: '1.5rem', marginBottom: '0.5rem', display: 'inline-block' }}>⏳</span>
              <h3 style={{ fontSize: '1.1rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '0.25rem' }}>
                Waiting for Teacher to Start a Challenge
              </h3>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>
                Follow the live teacher code above. When a challenge starts, your solution workspace will activate here.
              </p>
            </div>
          )}

          {/* Expired Challenge Warning Banner */}
          {currentChallenge && isChallengeExpired && (
            <div className="alert alert-error" style={{ marginBottom: '0.75rem' }}>
              <span>⏱</span>
              <span>The time limit for this challenge has expired. Submissions are now closed.</span>
            </div>
          )}

          {/* Section 3: Student's Solution Editor */}
          {currentChallenge && (
            <div className="editor-container" style={{ borderColor: 'rgba(6, 182, 212, 0.4)' }}>
              <div className="editor-toolbar" style={{ background: '#1a2433' }}>
                <div className="editor-toolbar-left">
                  <span className="editor-lang-tag" style={{ color: '#67e8f9', background: 'rgba(6, 182, 212, 0.15)' }}>
                    Java
                  </span>
                  <span style={{ fontWeight: 600, color: '#67e8f9' }}>Your Solution</span>
                </div>
                <div className="editor-toolbar-right">
                  <button
                    type="button"
                    className="btn btn-primary btn-sm"
                    onClick={handleSubmitSolution}
                    disabled={submitting || !studentSolution.trim() || isChallengeExpired}
                    style={{
                      padding: '0.4rem 1.25rem',
                      ...(isChallengeExpired ? { opacity: 0.6, cursor: 'not-allowed', background: '#475569' } : {}),
                    }}
                  >
                    {submitting ? '⚡ Evaluating with AI...' : isChallengeExpired ? '⏱ Time Expired' : '🚀 Submit Solution'}
                  </button>
                </div>
              </div>

              <Editor
                height="48vh"
                defaultLanguage="java"
                language="java"
                theme="vs-dark"
                value={studentSolution}
                onChange={(val) => setStudentSolution(val || '')}
                options={{
                  readOnly: false,
                  fontSize: 14,
                  fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
                  minimap: { enabled: false },
                  scrollBeyondLastLine: false,
                  automaticLayout: true,
                  tabSize: 4,
                  wordWrap: 'on',
                  lineNumbers: 'on',
                  padding: { top: 12, bottom: 12 },
                }}
                loading={<div style={{ padding: '2rem', color: 'var(--text-muted)' }}>Loading Solution Editor...</div>}
              />
            </div>
          )}

          {/* Error Message for Submission */}
          {submitError && (
            <div className="alert alert-error">
              <span>⚠</span>
              <span>{submitError}</span>
            </div>
          )}

          {/* Section 4: AI Review Results */}
          {aiReview && (
            <AiReviewResult review={aiReview} />
          )}
        </div>
      )}
    </div>
  );
};

export default RoomPage;

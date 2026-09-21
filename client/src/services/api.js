import axios from 'axios';
import { getToken, clearAuth } from '../utils/auth';

/**
 * Normalizes the backend REST API base URL.
 * - Supports VITE_API_URL and VITE_API_BASE_URL
 * - Prevents trailing slashes
 * - Ensures it ends with '/api' without accidental '/api/api' duplication
 */
export const getApiBaseUrl = () => {
  const rawUrl = (import.meta.env.VITE_API_URL || import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').trim();
  const cleanUrl = rawUrl.replace(/\/+$/, '');
  return cleanUrl.endsWith('/api') ? cleanUrl : `${cleanUrl}/api`;
};

/**
 * Resolves and normalizes the WebSocket / STOMP endpoint URL.
 * - Supports VITE_WS_URL or derives from VITE_API_URL / VITE_API_BASE_URL
 * - Enforces 'wss://' in production or over HTTPS
 * - Keeps 'ws://localhost:8080/ws' for local development
 * - Ensures endpoint path ends with '/ws'
 */
export const getWsUrl = () => {
  const envWsUrl = import.meta.env.VITE_WS_URL;
  const envApiUrl = import.meta.env.VITE_API_URL || import.meta.env.VITE_API_BASE_URL;

  let url = (envWsUrl || '').trim();

  // If VITE_WS_URL is not provided, attempt to derive from API URL
  if (!url && envApiUrl) {
    let base = envApiUrl.trim().replace(/\/+$/, '');
    if (base.endsWith('/api')) {
      base = base.slice(0, -4);
    }
    url = `${base}/ws`;
  }

  // Fallback to local development default
  if (!url) {
    url = 'ws://localhost:8080/ws';
  }

  const isHttps = typeof window !== 'undefined' && window.location.protocol === 'https:';
  const isProd = import.meta.env.PROD;
  const isLocalhost = url.includes('localhost') || url.includes('127.0.0.1');

  // Ensure wss:// in production, over HTTPS, or when connecting to remote hosts
  if (url.startsWith('https://')) {
    url = url.replace(/^https:\/\//i, 'wss://');
  } else if (url.startsWith('http://')) {
    url = (isProd || isHttps || !isLocalhost)
      ? url.replace(/^http:\/\//i, 'wss://')
      : url.replace(/^http:\/\//i, 'ws://');
  } else if (url.startsWith('ws://')) {
    if (isProd || isHttps || (!isLocalhost && !url.includes('ws://localhost'))) {
      url = url.replace(/^ws:\/\//i, 'wss://');
    }
  } else if (!url.startsWith('wss://')) {
    const scheme = (isProd || isHttps || !isLocalhost) ? 'wss://' : 'ws://';
    url = `${scheme}${url.replace(/^\/+/, '')}`;
  }

  const cleanWs = url.replace(/\/+$/, '');
  return cleanWs.endsWith('/ws') ? cleanWs : `${cleanWs}/ws`;
};

const api = axios.create({
  baseURL: getApiBaseUrl(),
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor: automatically attach Bearer token if present and prevent duplicate /api
api.interceptors.request.use(
  (config) => {
    // Guard against accidental double /api/api if caller passes a path starting with /api
    if (config.url) {
      if (config.url.startsWith('/api/')) {
        config.url = config.url.substring(4);
      } else if (config.url === '/api') {
        config.url = '';
      }
    }

    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: handle 401 unauthenticated for protected API calls
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const isAuthEndpoint = error.config?.url?.includes('/auth/login') || error.config?.url?.includes('/auth/register');
    if (error.response && error.response.status === 401 && !isAuthEndpoint) {
      clearAuth();
    }
    return Promise.reject(error);
  }
);

export default api;

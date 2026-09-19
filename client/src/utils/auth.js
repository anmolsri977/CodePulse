const TOKEN_KEY = 'codepulse_jwt_token';
const USER_KEY = 'codepulse_user';

/**
 * Retrieve stored JWT from localStorage.
 */
export const getToken = () => {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
};

/**
 * Persist JWT to localStorage.
 */
export const setToken = (token) => {
  try {
    if (token) {
      localStorage.setItem(TOKEN_KEY, token);
    } else {
      localStorage.removeItem(TOKEN_KEY);
    }
  } catch {
    // ignore storage access errors
  }
};

/**
 * Remove JWT from localStorage.
 */
export const removeToken = () => {
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    // ignore
  }
};

/**
 * Retrieve user metadata object from localStorage.
 */
export const getUser = () => {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
};

/**
 * Persist user metadata object to localStorage.
 */
export const setUser = (user) => {
  try {
    if (user) {
      localStorage.setItem(USER_KEY, JSON.stringify(user));
    } else {
      localStorage.removeItem(USER_KEY);
    }
  } catch {
    // ignore
  }
};

/**
 * Remove user metadata from localStorage.
 */
export const removeUser = () => {
  try {
    localStorage.removeItem(USER_KEY);
  } catch {
    // ignore
  }
};

/**
 * Check whether a valid non-empty JWT exists.
 */
export const isAuthenticated = () => {
  const token = getToken();
  return Boolean(token && token.trim().length > 0);
};

/**
 * Clear all authentication tokens and user state.
 */
export const clearAuth = () => {
  removeToken();
  removeUser();
};

import React, { createContext, useContext, useState, useCallback, useMemo } from 'react';
import {
  getToken,
  setToken,
  getUser,
  setUser,
  clearAuth,
} from '../utils/auth';

const AuthContext = createContext(null);

/**
 * AuthProvider wraps the application tree and provides centralized,
 * reactive authentication state synced with localStorage.
 */
export const AuthProvider = ({ children }) => {
  const [token, setTokenState] = useState(getToken());
  const [user, setUserState] = useState(getUser());

  const login = useCallback((jwtToken, userData = null) => {
    setToken(jwtToken);
    setTokenState(jwtToken);
    if (userData) {
      setUser(userData);
      setUserState(userData);
    }
  }, []);

  const logout = useCallback(() => {
    clearAuth();
    setTokenState(null);
    setUserState(null);
  }, []);

  const value = useMemo(
    () => ({
      token,
      user,
      isAuthenticated: Boolean(token),
      login,
      logout,
    }),
    [token, user, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

/**
 * Hook to consume the shared authentication context.
 */
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export default AuthContext;

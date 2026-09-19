import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import PublicOnlyRoute from './components/PublicOnlyRoute';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import TeacherDashboardPage from './pages/TeacherDashboardPage';
import StudentDashboardPage from './pages/StudentDashboardPage';
import RoomPage from './pages/RoomPage';
import NotFoundPage from './pages/NotFoundPage';
import { AuthProvider, useAuth } from './context/AuthContext';

const IndexRedirect = () => {
  const { isAuthenticated: authed, user } = useAuth();
  if (authed) {
    return <Navigate to={user?.role === 'TEACHER' ? '/teacher' : '/student'} replace />;
  }
  return <Navigate to="/login" replace />;
};

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<IndexRedirect />} />

          {/* Public-only routes: Redirect to role dashboard if already authenticated */}
          <Route element={<PublicOnlyRoute />}>
            <Route path="login" element={<LoginPage />} />
            <Route path="register" element={<RegisterPage />} />
          </Route>

          {/* Teacher-only protected route */}
          <Route element={<ProtectedRoute allowedRoles={['TEACHER']} />}>
            <Route path="teacher" element={<TeacherDashboardPage />} />
          </Route>

          {/* Student-only protected route */}
          <Route element={<ProtectedRoute allowedRoles={['STUDENT']} />}>
            <Route path="student" element={<StudentDashboardPage />} />
          </Route>

          {/* Room route: Protected for any authenticated user */}
          <Route element={<ProtectedRoute />}>
            <Route path="room/:roomCode" element={<RoomPage />} />
          </Route>

          {/* 404 Fallback */}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  </AuthProvider>
  );
}

export default App;

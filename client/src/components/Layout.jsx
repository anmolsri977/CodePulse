import React from 'react';
import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';

const Layout = () => {
  return (
    <div className="app-container">
      <Navbar />
      <main className="main-content">
        <Outlet />
      </main>
      <footer className="footer">
        <p>CodePulse Lite &copy; {new Date().getFullYear()} &mdash; Collaborative Coding &amp; AI Review</p>
      </footer>
    </div>
  );
};

export default Layout;

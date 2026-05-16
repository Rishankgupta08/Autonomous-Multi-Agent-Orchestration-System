import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AnimatePresence } from 'motion/react';

// Pages
import LandingPage from '../pages/LandingPage';
import SignUpPage from '../pages/SignUpPage';
import LoginPage from '../pages/LoginPage';
import IntegrationsPage from '../pages/IntegrationsPage';
import WorkflowPage from '../pages/WorkflowPage';
import ProfilePage from '../pages/ProfilePage';
import WorkflowHistoryPage from '../pages/WorkflowHistoryPage';
import NotFoundPage from '../pages/NotFoundPage';

// Components
import ProtectedRoute from '../components/layout/ProtectedRoute';

export default function App() {
  return (
    <BrowserRouter>
      <AnimatePresence mode="wait">
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/signup" element={<SignUpPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/integrations"
            element={
              <ProtectedRoute>
                <IntegrationsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/workflow"
            element={
              <ProtectedRoute>
                <WorkflowPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/profile"
            element={
              <ProtectedRoute>
                <ProfilePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/workflows"
            element={
              <ProtectedRoute>
                <WorkflowHistoryPage />
              </ProtectedRoute>
            }
          />
          <Route path="/404" element={<NotFoundPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AnimatePresence>
    </BrowserRouter>
  );
}

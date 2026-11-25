import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { AppLayout } from './components/layout/AppLayout';
import { LoginPage } from './pages/auth/LoginPage';
import { SalesmanOrderPage } from './pages/salesman/SalesmanOrderPage';
import { AdminOrdersPage } from './pages/admin/AdminOrdersPage';
import { ProductionPage } from './pages/production/ProductionPage';
import { InventoryPage } from './pages/inventory/InventoryPage';
import { MastersAndPurchasesPage } from './pages/admin/MastersAndPurchasesPage';
import { ReportsPage } from './pages/auditor/ReportsPage';
import { OwnerDashboardPage } from './pages/owner/OwnerDashboardPage';

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
};

const HomeRedirect: React.FC = () => {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.roles.includes('OWNER')) return <Navigate to="/owner" replace />;
  if (user.roles.includes('ADMIN')) return <Navigate to="/admin/orders" replace />;
  if (user.roles.includes('PRODUCTION_SUPERVISOR')) return <Navigate to="/production" replace />;
  if (user.roles.includes('ACCOUNTANT')) return <Navigate to="/reports" replace />;
  return <Navigate to="/salesman" replace />;
};

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <AppLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<HomeRedirect />} />
            <Route path="owner" element={<OwnerDashboardPage />} />
            <Route path="salesman" element={<SalesmanOrderPage />} />
            <Route path="admin/orders" element={<AdminOrdersPage />} />
            <Route path="production" element={<ProductionPage />} />
            <Route path="inventory" element={<InventoryPage />} />
            <Route path="masters" element={<MastersAndPurchasesPage />} />
            <Route path="reports" element={<ReportsPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
};

export default App;

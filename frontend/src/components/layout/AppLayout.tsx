import React, { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  ShoppingCart,
  ClipboardCheck,
  Factory,
  Package,
  FileBarChart,
  Database,
  LogOut,
  Menu,
  X,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

export const AppLayout: React.FC = () => {
  const { user, logout, hasRole } = useAuth();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);

  const navItems = [
    ...(hasRole('OWNER') ? [{ to: '/owner', label: 'Owner Overview', icon: LayoutDashboard }] : []),
    ...(hasRole('SALESMAN') ? [{ to: '/salesman', label: 'Take Order', icon: ShoppingCart }] : []),
    ...(hasRole('ADMIN') ? [{ to: '/admin/orders', label: 'Orders & Billing', icon: ClipboardCheck }] : []),
    ...(hasRole('PRODUCTION_SUPERVISOR', 'ADMIN')
      ? [{ to: '/production', label: 'Production Runs', icon: Factory }]
      : []),
    ...(hasRole('ADMIN', 'PRODUCTION_SUPERVISOR', 'OWNER', 'ACCOUNTANT')
      ? [{ to: '/inventory', label: 'Stock & Ledger', icon: Package }]
      : []),
    ...(hasRole('ADMIN', 'PRODUCTION_SUPERVISOR')
      ? [{ to: '/masters', label: 'Masters & Purchases', icon: Database }]
      : []),
    ...(hasRole('ACCOUNTANT', 'ADMIN', 'OWNER')
      ? [{ to: '/reports', label: 'Reports & Audit', icon: FileBarChart }]
      : []),
  ];

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen flex flex-col md:flex-row bg-slate-50 text-slate-900">
      {/* Desktop Sidebar */}
      <aside className="hidden md:flex md:w-64 md:flex-col bg-white border-r border-slate-200">
        <div className="p-5 border-b border-slate-200">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-indigo-600 text-white flex items-center justify-center font-bold">
              FF
            </div>
            <div>
              <h1 className="font-bold text-slate-900 leading-tight">FactoryFlow</h1>
              <p className="text-xs text-slate-500">Order-to-Audit ERP</p>
            </div>
          </div>
        </div>

        <nav className="flex-1 p-3 space-y-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-indigo-50 text-indigo-700'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                }`
              }
            >
              <item.icon className="w-4 h-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="p-4 border-t border-slate-200">
          <div className="text-sm font-medium text-slate-900">{user?.fullName}</div>
          <div className="text-xs text-slate-500 mb-3">{user?.roles.join(', ')}</div>
          <button
            onClick={handleLogout}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 text-sm font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-lg"
          >
            <LogOut className="w-4 h-4" />
            Sign out
          </button>
        </div>
      </aside>

      {/* Mobile Top Bar */}
      <header className="md:hidden bg-white border-b border-slate-200 px-4 py-3 flex items-center justify-between sticky top-0 z-20">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-md bg-indigo-600 text-white flex items-center justify-center font-bold text-sm">
            FF
          </div>
          <span className="font-bold text-slate-900">FactoryFlow</span>
        </div>
        <button
          onClick={() => setMobileOpen(!mobileOpen)}
          className="p-2 rounded-lg text-slate-600 hover:bg-slate-100"
          aria-label="Toggle menu"
        >
          {mobileOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
        </button>
      </header>

      {/* Mobile Drawer */}
      {mobileOpen && (
        <div className="md:hidden bg-white border-b border-slate-200 px-4 py-3 space-y-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={() => setMobileOpen(false)}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-3 rounded-lg text-base font-medium ${
                  isActive ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700'
                }`
              }
            >
              <item.icon className="w-5 h-5" />
              {item.label}
            </NavLink>
          ))}
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-3 px-3 py-3 text-base font-medium text-red-600"
          >
            <LogOut className="w-5 h-5" />
            Sign out ({user?.fullName})
          </button>
        </div>
      )}

      {/* Main Content */}
      <main className="flex-1 p-4 md:p-6 max-w-7xl w-full mx-auto pb-20 md:pb-6">
        <Outlet />
      </main>
    </div>
  );
};

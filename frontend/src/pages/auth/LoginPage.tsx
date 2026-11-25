import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const DEMO_USERS = [
  { username: 'salesman_a', label: 'Salesman A (Mobile)' },
  { username: 'salesman_b', label: 'Salesman B (Mobile)' },
  { username: 'admin', label: 'Admin / Billing' },
  { username: 'supervisor', label: 'Production Supervisor' },
  { username: 'auditor', label: 'Accountant / Auditor' },
  { username: 'owner', label: 'Owner' },
];

export const LoginPage: React.FC = () => {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('password123');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const routeForRoles = (roles: string[]) => {
    if (roles.includes('OWNER')) return '/owner';
    if (roles.includes('ADMIN')) return '/admin/orders';
    if (roles.includes('PRODUCTION_SUPERVISOR')) return '/production';
    if (roles.includes('ACCOUNTANT')) return '/reports';
    return '/salesman';
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const u = await login(username, password);
      navigate(routeForRoles(u.roles));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  const quickLogin = async (uname: string) => {
    setUsername(uname);
    setPassword('password123');
    setError('');
    setLoading(true);
    try {
      const u = await login(uname, 'password123');
      navigate(routeForRoles(u.roles));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
      <div className="w-full max-w-md bg-white border border-slate-200 rounded-2xl p-6 md:p-8 shadow-sm">
        <div className="flex items-center gap-3 mb-6">
          <div className="w-10 h-10 rounded-xl bg-indigo-600 text-white flex items-center justify-center font-bold text-lg">
            FF
          </div>
          <div>
            <h1 className="text-xl font-bold text-slate-900">FactoryFlow</h1>
            <p className="text-sm text-slate-500">Order-to-Audit Management System</p>
          </div>
        </div>

        {error && (
          <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Username</label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full px-3 py-2.5 text-base border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-3 py-2.5 text-base border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
              required
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 px-4 bg-indigo-600 hover:bg-indigo-700 text-white font-semibold rounded-lg transition-colors disabled:opacity-50"
          >
            {loading ? 'Signing in...' : 'Sign in'}
          </button>
        </form>

        <div className="mt-6 pt-5 border-t border-slate-200">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-3">
            Quick Demo Logins (password: password123)
          </p>
          <div className="grid grid-cols-2 gap-2">
            {DEMO_USERS.map((u) => (
              <button
                key={u.username}
                type="button"
                onClick={() => quickLogin(u.username)}
                className="text-left px-3 py-2 text-xs font-medium bg-slate-50 hover:bg-indigo-50 hover:text-indigo-700 border border-slate-200 rounded-lg transition-colors"
              >
                {u.label}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

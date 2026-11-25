import React, { createContext, useContext, useState } from 'react';
import { apiFetch } from '../services/api';

export interface AuthUser {
  userId: string;
  username: string;
  fullName: string;
  roles: string[];
}

interface AuthContextType {
  user: AuthUser | null;
  login: (username: string, password: string) => Promise<AuthUser>;
  logout: () => void;
  hasRole: (...roles: string[]) => boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(() => {
    const saved = localStorage.getItem('ff_user');
    return saved ? JSON.parse(saved) : null;
  });

  const login = async (username: string, password: string): Promise<AuthUser> => {
    const res = await apiFetch<{
      token: string;
      userId: string;
      username: string;
      fullName: string;
      roles: string[];
    }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
    const authUser: AuthUser = {
      userId: res.userId,
      username: res.username,
      fullName: res.fullName,
      roles: res.roles,
    };
    localStorage.setItem('ff_token', res.token);
    localStorage.setItem('ff_user', JSON.stringify(authUser));
    setUser(authUser);
    return authUser;
  };

  const logout = () => {
    localStorage.removeItem('ff_token');
    localStorage.removeItem('ff_user');
    setUser(null);
  };

  const hasRole = (...roles: string[]) => {
    if (!user) return false;
    return roles.some((r) => user.roles.includes(r));
  };

  return (
    <AuthContext.Provider value={{ user, login, logout, hasRole }}>
      {children}
    </AuthContext.Provider>
  );
};

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

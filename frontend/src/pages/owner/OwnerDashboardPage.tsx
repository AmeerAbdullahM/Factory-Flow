import React, { useEffect, useState } from 'react';
import { AlertTriangle, TrendingUp, Package, IndianRupee, ShoppingBag } from 'lucide-react';
import { apiFetch } from '../../services/api';

interface OwnerDashboard {
  todaysOrders: number;
  pendingOrders: number;
  dispatchedOrders: number;
  monthlySales: number;
  totalCollections: number;
  totalReceivables: number;
  overdueInvoices: number;
  rawStockValue: number;
  fgStockPackets: number;
  latestCostPerPacket: number | null;
  varianceAlertsCount: number;
  alerts: string[];
}

export const OwnerDashboardPage: React.FC = () => {
  const [data, setData] = useState<OwnerDashboard | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    apiFetch<OwnerDashboard>('/dashboard/owner')
      .then(setData)
      .catch((e) => setError(e.message));
  }, []);

  if (error) return <div className="p-4 bg-red-50 text-red-700 rounded-xl">{error}</div>;
  if (!data) return <div className="p-6 text-slate-500 text-sm">Loading owner overview...</div>;

  const cards = [
    { label: "Today's Orders", value: String(data.todaysOrders), sub: `${data.pendingOrders} waiting approval`, icon: ShoppingBag },
    { label: 'Monthly Sales', value: `₹${Number(data.monthlySales).toFixed(2)}`, sub: `${data.dispatchedOrders} dispatched`, icon: TrendingUp },
    { label: 'FG Stock On Hand', value: `${Number(data.fgStockPackets).toFixed(0)} pkt`, sub: `Raw value: ₹${Number(data.rawStockValue).toFixed(0)}`, icon: Package },
    { label: 'Cost per Packet', value: data.latestCostPerPacket != null ? `₹${Number(data.latestCostPerPacket).toFixed(2)}` : '—', sub: 'Latest posted run', icon: IndianRupee },
    { label: 'Receivables Due', value: `₹${Number(data.totalReceivables).toFixed(2)}`, sub: `Collected: ₹${Number(data.totalCollections).toFixed(2)}`, icon: IndianRupee },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-slate-900">Owner Business Overview</h2>
        <p className="text-sm text-slate-500">Live database-backed summary across sales, production, inventory, and receivables.</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
        {cards.map((c) => (
          <div key={c.label} className="bg-white border border-slate-200 rounded-xl p-4">
            <div className="flex items-center justify-between text-slate-500 text-xs font-medium mb-1">
              <span>{c.label}</span>
              <c.icon className="w-4 h-4 text-indigo-600" />
            </div>
            <div className="text-2xl font-bold text-slate-900">{c.value}</div>
            <div className="text-xs text-slate-500 mt-1">{c.sub}</div>
          </div>
        ))}
      </div>

      {/* Alerts */}
      <div className="bg-white border border-slate-200 rounded-xl p-5">
        <div className="flex items-center gap-2 font-bold text-slate-900 mb-3">
          <AlertTriangle className="w-4 h-4 text-amber-600" />
          Active Alerts ({data.alerts.length})
        </div>
        {data.alerts.length === 0 ? (
          <p className="text-sm text-slate-500">No active alerts.</p>
        ) : (
          <ul className="space-y-2">
            {data.alerts.map((a, idx) => (
              <li key={idx} className="text-sm px-3 py-2 rounded-lg bg-amber-50 border border-amber-200 text-amber-900">
                {a}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};

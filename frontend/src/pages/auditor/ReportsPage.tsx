import React, { useEffect, useState } from 'react';
import { FileDown } from 'lucide-react';
import { apiFetch, downloadBlob } from '../../services/api';

type ReportTab = 'sales' | 'gst' | 'purchases' | 'stock' | 'production' | 'outstanding' | 'audit';

export const ReportsPage: React.FC = () => {
  const [tab, setTab] = useState<ReportTab>('sales');
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [error, setError] = useState('');

  const endpointMap: Record<ReportTab, string> = {
    sales: '/reports/sales',
    gst: '/reports/gst',
    purchases: '/reports/purchases',
    stock: '/reports/stock',
    production: '/reports/production',
    outstanding: '/reports/outstanding',
    audit: '/audit',
  };

  useEffect(() => {
    setError('');
    apiFetch<Record<string, unknown>[]>(endpointMap[tab])
      .then(setRows)
      .catch((e) => setError(e.message));
  }, [tab]);

  const cols = rows.length > 0 ? Object.keys(rows[0]) : [];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-slate-900">Auditor Reports & Exports</h2>
          <p className="text-sm text-slate-500">All figures reconcile directly with persisted transactions and the stock ledger.</p>
        </div>
        {tab !== 'audit' && (
          <div className="flex gap-2">
            <button
              onClick={() => downloadBlob(`${endpointMap[tab]}?format=csv`, `${tab}-report.csv`)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium bg-white border border-slate-300 hover:bg-slate-50 rounded-lg"
            >
              <FileDown className="w-4 h-4" /> CSV
            </button>
            <button
              onClick={() => downloadBlob(`${endpointMap[tab]}?format=xlsx`, `${tab}-report.xlsx`)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium bg-white border border-slate-300 hover:bg-slate-50 rounded-lg"
            >
              <FileDown className="w-4 h-4" /> Excel
            </button>
            <button
              onClick={() => downloadBlob(`${endpointMap[tab]}?format=pdf`, `${tab}-report.pdf`)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium bg-indigo-600 text-white hover:bg-indigo-700 rounded-lg"
            >
              <FileDown className="w-4 h-4" /> PDF
            </button>
          </div>
        )}
      </div>

      {/* Category Tabs */}
      <div className="flex flex-wrap gap-2">
        {(
          [
            ['sales', 'Sales Register'],
            ['gst', 'GST Summary (GSTR-1/3B)'],
            ['purchases', 'Purchase Register'],
            ['stock', 'Stock Summary'],
            ['production', 'Production Report'],
            ['outstanding', 'Outstanding & Ageing'],
            ['audit', 'Audit Trail'],
          ] as [ReportTab, string][]
        ).map(([key, label]) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`px-3.5 py-2 text-sm font-medium rounded-lg border transition-colors ${
              tab === key
                ? 'bg-indigo-600 text-white border-indigo-600'
                : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {error && <div className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">{error}</div>}

      {/* Data Table */}
      <div className="bg-white border border-slate-200 rounded-xl p-4 overflow-x-auto">
        {rows.length === 0 ? (
          <p className="text-sm text-slate-500 py-6 text-center">No records found for this report.</p>
        ) : (
          <table className="w-full text-xs text-left">
            <thead className="uppercase text-slate-500 border-b border-slate-200">
              <tr>
                {cols.map((c) => (
                  <th key={c} className="py-2 pr-3 whitespace-nowrap">{c}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((r, idx) => (
                <tr key={idx}>
                  {cols.map((c) => (
                    <td key={c} className="py-2 pr-3 whitespace-nowrap">
                      {typeof r[c] === 'object' && r[c] !== null ? JSON.stringify(r[c]) : String(r[c] ?? '—')}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};

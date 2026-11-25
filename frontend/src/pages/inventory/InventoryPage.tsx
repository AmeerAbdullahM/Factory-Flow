import React, { useEffect, useState } from 'react';
import { RefreshCw, CheckCircle2, AlertTriangle } from 'lucide-react';
import { apiFetch } from '../../services/api';

interface Balance {
  id: string;
  itemType: string;
  itemName: string;
  itemCode: string;
  currentQuantity: number;
  unitSymbol: string;
  weightedAvgCost: number;
  totalValue: number;
  lowStock: boolean;
}

interface Movement {
  id: string;
  movementDate: string;
  itemType: string;
  itemName: string;
  movementType: string;
  qtyIn: number | null;
  qtyOut: number | null;
  runningBalance: number;
  unitCost: number | null;
  totalValue: number | null;
  sourceDocumentType: string | null;
  referenceNumber: string | null;
  createdBy: string;
}

export const InventoryPage: React.FC = () => {
  const [balances, setBalances] = useState<Balance[]>([]);
  const [ledger, setLedger] = useState<Movement[]>([]);
  const [filterType, setFilterType] = useState('');
  const [reconcileMsg, setReconcileMsg] = useState<string | null>(null);

  const loadData = async () => {
    const [bals, movs] = await Promise.all([
      apiFetch<Balance[]>('/inventory/summary'),
      apiFetch<Movement[]>('/inventory/ledger'),
    ]);
    setBalances(bals);
    setLedger(movs);
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleReconcile = async () => {
    const res = await apiFetch<{ reconciled: boolean; message: string }>('/inventory/reconcile');
    setReconcileMsg(res.message);
  };

  const filteredBalances = filterType ? balances.filter((b) => b.itemType === filterType) : balances;
  const filteredLedger = filterType ? ledger.filter((m) => m.itemType === filterType) : ledger;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-slate-900">Stock Summary & Ledger</h2>
          <p className="text-sm text-slate-500">Weighted-average valuation derived from immutable stock ledger movements.</p>
        </div>
        <div className="flex items-center gap-2">
          <select
            value={filterType}
            onChange={(e) => setFilterType(e.target.value)}
            className="px-3 py-2 text-sm border border-slate-300 rounded-lg bg-white"
          >
            <option value="">All Categories</option>
            <option value="RAW_MATERIAL">Raw Materials</option>
            <option value="PACKAGING">Packaging</option>
            <option value="FINISHED_GOODS">Finished Goods</option>
          </select>
          <button
            onClick={handleReconcile}
            className="inline-flex items-center gap-1.5 px-3 py-2 text-sm font-medium bg-white border border-slate-300 hover:bg-slate-50 rounded-lg"
          >
            <RefreshCw className="w-4 h-4" /> Reconcile Ledger
          </button>
        </div>
      </div>

      {reconcileMsg && (
        <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4" /> {reconcileMsg}
        </div>
      )}

      {/* Stock Summary Cards / Table */}
      <div className="bg-white border border-slate-200 rounded-xl p-5">
        <h3 className="font-bold text-slate-900 mb-3">Current Stock Balances</h3>
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs uppercase text-slate-500 border-b border-slate-200">
              <tr>
                <th className="py-2 pr-3">Category</th>
                <th className="py-2 pr-3">Item</th>
                <th className="py-2 pr-3">On Hand</th>
                <th className="py-2 pr-3">Weighted Avg Cost</th>
                <th className="py-2 pr-3">Total Value</th>
                <th className="py-2">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredBalances.map((b) => (
                <tr key={b.id}>
                  <td className="py-2.5 pr-3 text-xs font-medium text-slate-500">{b.itemType.replace('_', ' ')}</td>
                  <td className="py-2.5 pr-3 font-semibold text-slate-900">{b.itemName}</td>
                  <td className="py-2.5 pr-3 font-medium">
                    {Number(b.currentQuantity).toFixed(2)} {b.unitSymbol}
                  </td>
                  <td className="py-2.5 pr-3">₹{Number(b.weightedAvgCost).toFixed(3)}</td>
                  <td className="py-2.5 pr-3 font-bold">₹{Number(b.totalValue).toFixed(2)}</td>
                  <td className="py-2.5">
                    {b.lowStock ? (
                      <span className="inline-flex items-center gap-1 text-xs font-semibold text-amber-800 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-full">
                        <AlertTriangle className="w-3 h-3" /> Low Stock
                      </span>
                    ) : (
                      <span className="text-xs font-medium text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded-full">
                        OK
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Stock Ledger */}
      <div className="bg-white border border-slate-200 rounded-xl p-5">
        <h3 className="font-bold text-slate-900 mb-3">Stock Ledger (Audit Trail)</h3>
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs uppercase text-slate-500 border-b border-slate-200">
              <tr>
                <th className="py-2 pr-3">Date</th>
                <th className="py-2 pr-3">Item</th>
                <th className="py-2 pr-3">Movement</th>
                <th className="py-2 pr-3">Source / Ref</th>
                <th className="py-2 pr-3">In</th>
                <th className="py-2 pr-3">Out</th>
                <th className="py-2 pr-3">Balance</th>
                <th className="py-2">User</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredLedger.map((m) => (
                <tr key={m.id}>
                  <td className="py-2 pr-3 text-xs text-slate-500">{m.movementDate.slice(0, 10)}</td>
                  <td className="py-2 pr-3 font-medium text-slate-900">{m.itemName}</td>
                  <td className="py-2 pr-3 text-xs">{m.movementType}</td>
                  <td className="py-2 pr-3 text-xs text-slate-600">
                    {m.sourceDocumentType} {m.referenceNumber ? `(${m.referenceNumber})` : ''}
                  </td>
                  <td className="py-2 pr-3 text-emerald-700 font-medium">{m.qtyIn ?? '—'}</td>
                  <td className="py-2 pr-3 text-red-700 font-medium">{m.qtyOut ?? '—'}</td>
                  <td className="py-2 pr-3 font-bold">{m.runningBalance}</td>
                  <td className="py-2 text-xs text-slate-500">{m.createdBy}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

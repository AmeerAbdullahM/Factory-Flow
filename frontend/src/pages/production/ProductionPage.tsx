import React, { useEffect, useState } from 'react';
import { Factory, AlertTriangle, Plus } from 'lucide-react';
import { apiFetch } from '../../services/api';
import { StatusChip } from '../../components/common/StatusChip';

interface Product {
  id: string;
  name: string;
  sellingPrice: number;
}

interface MaterialIssue {
  id: string;
  itemName: string;
  itemType: string;
  plannedQuantity: number;
  actualQuantity: number;
  damagedQuantity: number;
  unitSymbol: string;
  variancePercent: number | null;
  varianceFlagged: boolean;
  unitCost: number | null;
  totalCost: number | null;
}

interface CostItem {
  id: string;
  costType: string;
  description: string;
  amount: number;
}

interface ProductionRun {
  id: string;
  referenceNumber: string;
  productName: string;
  bomVersion: number;
  plannedBatches: number;
  plannedOutput: number;
  actualGoodOutput: number | null;
  wastagePackets: number;
  status: string;
  runDate: string;
  totalMaterialCost: number | null;
  totalPackagingCost: number | null;
  totalLabourCost: number;
  totalOverheadCost: number;
  totalCost: number | null;
  costPerPacket: number | null;
  sellingPrice: number | null;
  marginPerPacket: number | null;
  marginPercent: number | null;
  yieldVariancePercent: number | null;
  yieldFlagged: boolean;
  materialIssues: MaterialIssue[];
  costs: CostItem[];
}

export const ProductionPage: React.FC = () => {
  const [products, setProducts] = useState<Product[]>([]);
  const [runs, setRuns] = useState<ProductionRun[]>([]);
  const [selectedRun, setSelectedRun] = useState<ProductionRun | null>(null);
  const [newProductId, setNewProductId] = useState('');
  const [batches, setBatches] = useState(1);

  // Actuals editor state
  const [actualGood, setActualGood] = useState(200);
  const [wastage, setWastage] = useState(0);
  const [actualMap, setActualMap] = useState<Record<string, { actual: number; damaged: number }>>({});

  // Post-posting overhead state
  const [costType, setCostType] = useState('LABOUR');
  const [costDesc, setCostDesc] = useState('');
  const [costAmt, setCostAmt] = useState('');

  const [msg, setMsg] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);

  const loadData = async () => {
    const [prods, runList] = await Promise.all([
      apiFetch<Product[]>('/products'),
      apiFetch<ProductionRun[]>('/production/runs'),
    ]);
    setProducts(prods);
    setRuns(runList);
    if (prods.length > 0 && !newProductId) setNewProductId(prods[0].id);
    if (selectedRun) {
      const updated = runList.find((r) => r.id === selectedRun.id);
      if (updated) selectRun(updated);
    }
  };

  useEffect(() => {
    loadData().catch((e) => setMsg({ type: 'err', text: e.message }));
  }, []);

  const selectRun = (run: ProductionRun) => {
    setSelectedRun(run);
    setActualGood(run.actualGoodOutput ?? run.plannedOutput);
    setWastage(run.wastagePackets ?? 0);
    const map: Record<string, { actual: number; damaged: number }> = {};
    run.materialIssues.forEach((mi) => {
      map[mi.id] = { actual: mi.actualQuantity, damaged: mi.damagedQuantity || 0 };
    });
    setActualMap(map);
  };

  const createRun = async (e: React.FormEvent) => {
    e.preventDefault();
    setMsg(null);
    try {
      const created = await apiFetch<ProductionRun>('/production/runs', {
        method: 'POST',
        body: JSON.stringify({
          productId: newProductId,
          plannedBatches: batches,
          runDate: new Date().toISOString().slice(0, 10),
        }),
      });
      setMsg({ type: 'ok', text: `Production run ${created.referenceNumber} created.` });
      await loadData();
      selectRun(created);
    } catch (err) {
      setMsg({ type: 'err', text: err instanceof Error ? err.message : 'Create failed' });
    }
  };

  const saveAndPostRun = async () => {
    if (!selectedRun) return;
    setMsg(null);
    try {
      await apiFetch(`/production/runs/${selectedRun.id}/actuals`, {
        method: 'PUT',
        body: JSON.stringify({
          actualGoodOutput: actualGood,
          wastagePackets: wastage,
          materials: selectedRun.materialIssues.map((mi) => ({
            materialIssueId: mi.id,
            actualQuantity: actualMap[mi.id]?.actual ?? mi.actualQuantity,
            damagedQuantity: actualMap[mi.id]?.damaged ?? 0,
          })),
        }),
      });
      const posted = await apiFetch<ProductionRun>(`/production/runs/${selectedRun.id}/post`, {
        method: 'POST',
      });
      setMsg({ type: 'ok', text: `Run ${posted.referenceNumber} posted! Stock and costing updated.` });
      await loadData();
      selectRun(posted);
    } catch (err) {
      setMsg({ type: 'err', text: err instanceof Error ? err.message : 'Post failed' });
    }
  };

  const addOverhead = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedRun || !costAmt) return;
    setMsg(null);
    try {
      const updated = await apiFetch<ProductionRun>(`/production/runs/${selectedRun.id}/costs`, {
        method: 'POST',
        body: JSON.stringify({
          costType,
          description: costDesc || costType,
          amount: parseFloat(costAmt),
        }),
      });
      setCostAmt('');
      setCostDesc('');
      setMsg({ type: 'ok', text: `Added ${costType} cost. Cost per packet recalculated.` });
      await loadData();
      selectRun(updated);
    } catch (err) {
      setMsg({ type: 'err', text: err instanceof Error ? err.message : 'Add cost failed' });
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-slate-900">Production Runs & Costing</h2>
        <p className="text-sm text-slate-500">BOM planned vs actual consumption, variance flags (&gt;3% tolerance), and per-packet costing.</p>
      </div>

      {msg && (
        <div
          className={`p-3 rounded-xl border text-sm ${
            msg.type === 'ok' ? 'bg-emerald-50 border-emerald-200 text-emerald-800' : 'bg-red-50 border-red-200 text-red-700'
          }`}
        >
          {msg.text}
        </div>
      )}

      {/* Start New Run */}
      <form onSubmit={createRun} className="bg-white border border-slate-200 rounded-xl p-4 flex flex-wrap items-end gap-4">
        <div className="flex-1 min-w-[200px]">
          <label className="block text-xs font-medium text-slate-600 mb-1">Product (with active BOM)</label>
          <select
            value={newProductId}
            onChange={(e) => setNewProductId(e.target.value)}
            className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg bg-white"
          >
            {products.map((p) => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
        </div>
        <div className="w-32">
          <label className="block text-xs font-medium text-slate-600 mb-1">Batches</label>
          <input
            type="number"
            min={1}
            value={batches}
            onChange={(e) => setBatches(Math.max(1, parseInt(e.target.value || '1', 10)))}
            className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg"
          />
        </div>
        <button
          type="submit"
          className="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-lg"
        >
          <Factory className="w-4 h-4" /> Start Production Run
        </button>
      </form>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Run List */}
        <div className="bg-white border border-slate-200 rounded-xl p-4 space-y-2">
          <h3 className="font-bold text-slate-900 mb-2">Production Runs</h3>
          {runs.length === 0 ? (
            <p className="text-sm text-slate-500">No runs yet.</p>
          ) : (
            runs.map((r) => (
              <button
                key={r.id}
                onClick={() => selectRun(r)}
                className={`w-full text-left p-3 rounded-lg border transition-colors ${
                  selectedRun?.id === r.id ? 'border-indigo-400 bg-indigo-50/50' : 'border-slate-200 hover:bg-slate-50'
                }`}
              >
                <div className="flex justify-between items-center">
                  <span className="font-semibold text-sm text-slate-900">{r.referenceNumber}</span>
                  <StatusChip status={r.status} />
                </div>
                <div className="text-xs text-slate-600 mt-1">
                  {r.productName} • {r.runDate}
                </div>
                <div className="text-xs text-slate-500 mt-0.5">
                  Output: {r.actualGoodOutput ?? '—'} / {r.plannedOutput} pkt
                  {r.costPerPacket != null && ` • ₹${Number(r.costPerPacket).toFixed(2)}/pkt`}
                </div>
              </button>
            ))
          )}
        </div>

        {/* Selected Run Details */}
        <div className="lg:col-span-2 space-y-4">
          {!selectedRun ? (
            <div className="bg-white border border-slate-200 rounded-xl p-8 text-center text-sm text-slate-500">
              Select a production run on the left or start a new run above.
            </div>
          ) : (
            <div className="bg-white border border-slate-200 rounded-xl p-5 space-y-5">
              <div className="flex flex-wrap justify-between items-start gap-2">
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="text-lg font-bold text-slate-900">{selectedRun.referenceNumber}</h3>
                    <StatusChip status={selectedRun.status} />
                    {selectedRun.yieldFlagged && (
                      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-50 text-amber-800 border border-amber-200">
                        <AlertTriangle className="w-3.5 h-3.5" />
                        Yield {Number(selectedRun.yieldVariancePercent).toFixed(1)}%
                      </span>
                    )}
                  </div>
                  <p className="text-sm text-slate-500">
                    {selectedRun.productName} (BOM v{selectedRun.bomVersion}) • Expected {selectedRun.plannedOutput} packets
                  </p>
                </div>
              </div>

              {/* Expected vs Actual Table */}
              <div className="overflow-x-auto">
                <table className="w-full text-sm text-left">
                  <thead className="text-xs uppercase text-slate-500 border-b border-slate-200">
                    <tr>
                      <th className="py-2 pr-3">Material</th>
                      <th className="py-2 pr-3">Planned</th>
                      <th className="py-2 pr-3">Actual Used</th>
                      <th className="py-2 pr-3">Damaged</th>
                      <th className="py-2 pr-3">Variance</th>
                      <th className="py-2">Cost</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {selectedRun.materialIssues.map((mi) => (
                      <tr key={mi.id}>
                        <td className="py-2.5 pr-3 font-medium text-slate-900">{mi.itemName}</td>
                        <td className="py-2.5 pr-3">{mi.plannedQuantity} {mi.unitSymbol}</td>
                        <td className="py-2.5 pr-3">
                          {selectedRun.status === 'DRAFT' ? (
                            <input
                              type="number"
                              step="0.01"
                              value={actualMap[mi.id]?.actual ?? mi.actualQuantity}
                              onChange={(e) =>
                                setActualMap((p) => ({
                                  ...p,
                                  [mi.id]: { ...p[mi.id], actual: parseFloat(e.target.value || '0') },
                                }))
                              }
                              className="w-24 px-2 py-1 border border-slate-300 rounded"
                            />
                          ) : (
                            `${mi.actualQuantity} ${mi.unitSymbol}`
                          )}
                        </td>
                        <td className="py-2.5 pr-3">
                          {selectedRun.status === 'DRAFT' && mi.itemType === 'PACKAGING' ? (
                            <input
                              type="number"
                              step="1"
                              value={actualMap[mi.id]?.damaged ?? 0}
                              onChange={(e) =>
                                setActualMap((p) => ({
                                  ...p,
                                  [mi.id]: { ...p[mi.id], damaged: parseFloat(e.target.value || '0') },
                                }))
                              }
                              className="w-20 px-2 py-1 border border-slate-300 rounded"
                            />
                          ) : (
                            `${mi.damagedQuantity || 0} ${mi.unitSymbol}`
                          )}
                        </td>
                        <td className="py-2.5 pr-3">
                          {mi.variancePercent != null ? (
                            <span
                              className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                                mi.varianceFlagged
                                  ? 'bg-amber-50 text-amber-800 border border-amber-200'
                                  : 'bg-slate-100 text-slate-700'
                              }`}
                            >
                              {Number(mi.variancePercent) > 0 ? '+' : ''}
                              {Number(mi.variancePercent).toFixed(1)}%
                            </span>
                          ) : '—'}
                        </td>
                        <td className="py-2.5 font-medium">
                          {mi.totalCost != null ? `₹${Number(mi.totalCost).toFixed(2)}` : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Output Entry & Post */}
              {selectedRun.status === 'DRAFT' ? (
                <div className="bg-slate-50 p-4 rounded-xl flex flex-wrap items-end gap-4">
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1">Actual Good Packets</label>
                    <input
                      type="number"
                      value={actualGood}
                      onChange={(e) => setActualGood(parseInt(e.target.value || '0', 10))}
                      className="w-32 px-3 py-2 text-sm border border-slate-300 rounded-lg bg-white"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1">Wastage Packets</label>
                    <input
                      type="number"
                      value={wastage}
                      onChange={(e) => setWastage(parseInt(e.target.value || '0', 10))}
                      className="w-32 px-3 py-2 text-sm border border-slate-300 rounded-lg bg-white"
                    />
                  </div>
                  <button
                    onClick={saveAndPostRun}
                    className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-lg"
                  >
                    Post Production Run
                  </button>
                </div>
              ) : (
                <>
                  {/* Costing Breakdown */}
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-slate-50 p-4 rounded-xl text-sm">
                    <div>
                      <div className="text-xs text-slate-500">Materials + Packaging</div>
                      <div className="font-bold text-slate-900">
                        ₹{((selectedRun.totalMaterialCost || 0) + (selectedRun.totalPackagingCost || 0)).toFixed(2)}
                      </div>
                    </div>
                    <div>
                      <div className="text-xs text-slate-500">Labour + Overheads</div>
                      <div className="font-bold text-slate-900">
                        ₹{((selectedRun.totalLabourCost || 0) + (selectedRun.totalOverheadCost || 0)).toFixed(2)}
                      </div>
                    </div>
                    <div>
                      <div className="text-xs text-slate-500">Total Cost</div>
                      <div className="font-bold text-indigo-700">₹{Number(selectedRun.totalCost || 0).toFixed(2)}</div>
                    </div>
                    <div>
                      <div className="text-xs text-slate-500">Cost / Packet (Margin)</div>
                      <div className="font-bold text-emerald-700">
                        ₹{Number(selectedRun.costPerPacket || 0).toFixed(2)} ({Number(selectedRun.marginPercent || 0).toFixed(1)}%)
                      </div>
                    </div>
                  </div>

                  {/* Add Overheads After Posting */}
                  <form onSubmit={addOverhead} className="flex flex-wrap items-end gap-3 border-t border-slate-200 pt-4">
                    <div>
                      <label className="block text-xs font-medium text-slate-600 mb-1">Cost Type</label>
                      <select
                        value={costType}
                        onChange={(e) => setCostType(e.target.value)}
                        className="px-3 py-2 text-sm border border-slate-300 rounded-lg bg-white"
                      >
                        <option value="LABOUR">LABOUR</option>
                        <option value="GAS">GAS</option>
                        <option value="POWER">POWER</option>
                        <option value="OTHER">OTHER</option>
                      </select>
                    </div>
                    <div className="flex-1 min-w-[140px]">
                      <label className="block text-xs font-medium text-slate-600 mb-1">Description</label>
                      <input
                        type="text"
                        placeholder="e.g. Shift labour"
                        value={costDesc}
                        onChange={(e) => setCostDesc(e.target.value)}
                        className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg"
                      />
                    </div>
                    <div className="w-28">
                      <label className="block text-xs font-medium text-slate-600 mb-1">Amount (₹)</label>
                      <input
                        type="number"
                        step="0.01"
                        value={costAmt}
                        onChange={(e) => setCostAmt(e.target.value)}
                        className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg"
                        required
                      />
                    </div>
                    <button
                      type="submit"
                      className="inline-flex items-center gap-1 px-3 py-2 bg-slate-800 hover:bg-slate-900 text-white text-sm font-medium rounded-lg"
                    >
                      <Plus className="w-4 h-4" /> Add Overhead
                    </button>
                  </form>
                </>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

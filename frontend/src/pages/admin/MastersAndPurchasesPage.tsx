import React, { useEffect, useState } from 'react';
import { PackagePlus, CheckCircle2 } from 'lucide-react';
import { apiFetch } from '../../services/api';
import { StatusChip } from '../../components/common/StatusChip';

interface Supplier { id: string; name: string; state: string; gstin: string }
interface RawMaterial { id: string; name: string; code: string; unitId: string; unitSymbol: string }
interface Purchase {
  id: string;
  referenceNumber: string;
  supplierName: string;
  purchaseDate: string;
  status: string;
  totalAmount: number;
  items: { rawMaterialName?: string; packagingMaterialName?: string; quantity: number; unitSymbol: string; unitRate: number }[];
}
interface BomHeader {
  id: string;
  code: string;
  productName: string;
  expectedOutput: number;
  version: number;
  items: { itemType: string; rawMaterialName?: string; packagingMaterialName?: string; quantity: number; unitSymbol: string }[];
}

export const MastersAndPurchasesPage: React.FC = () => {
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [rawMaterials, setRawMaterials] = useState<RawMaterial[]>([]);
  const [purchases, setPurchases] = useState<Purchase[]>([]);
  const [boms, setBoms] = useState<BomHeader[]>([]);

  const [supplierId, setSupplierId] = useState('');
  const [rawMatId, setRawMatId] = useState('');
  const [qty, setQty] = useState('60');
  const [rate, setRate] = useState('45');
  const [msg, setMsg] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);

  const loadData = async () => {
    const [sups, rms, purs, bomList] = await Promise.all([
      apiFetch<Supplier[]>('/suppliers'),
      apiFetch<RawMaterial[]>('/materials/raw'),
      apiFetch<Purchase[]>('/purchases'),
      apiFetch<BomHeader[]>('/bom'),
    ]);
    setSuppliers(sups);
    setRawMaterials(rms);
    setPurchases(purs);
    setBoms(bomList);
    if (sups.length > 0 && !supplierId) setSupplierId(sups[0].id);
    if (rms.length > 0 && !rawMatId) setRawMatId(rms[0].id);
  };

  useEffect(() => {
    loadData().catch((e) => setMsg({ type: 'err', text: e.message }));
  }, []);

  const createAndReceivePurchase = async (e: React.FormEvent) => {
    e.preventDefault();
    setMsg(null);
    const mat = rawMaterials.find((r) => r.id === rawMatId);
    if (!mat) return;
    try {
      const created = await apiFetch<Purchase>('/purchases', {
        method: 'POST',
        body: JSON.stringify({
          supplierId,
          purchaseDate: new Date().toISOString().slice(0, 10),
          items: [
            {
              itemType: 'RAW_MATERIAL',
              rawMaterialId: mat.id,
              unitId: mat.unitId,
              quantity: parseFloat(qty),
              unitRate: parseFloat(rate),
              gstRate: 0,
            },
          ],
        }),
      });
      await apiFetch(`/purchases/${created.id}/receive`, { method: 'POST' });
      setMsg({ type: 'ok', text: `Purchase ${created.referenceNumber} received into stock!` });
      await loadData();
    } catch (err) {
      setMsg({ type: 'err', text: err instanceof Error ? err.message : 'Purchase failed' });
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-slate-900">Purchases & Bill of Materials</h2>
        <p className="text-sm text-slate-500">Record raw material purchases (auto-updates weighted average cost) and inspect BOMs.</p>
      </div>

      {msg && (
        <div className={`p-3 rounded-xl border text-sm flex items-center gap-2 ${
          msg.type === 'ok' ? 'bg-emerald-50 border-emerald-200 text-emerald-800' : 'bg-red-50 border-red-200 text-red-700'
        }`}>
          {msg.type === 'ok' && <CheckCircle2 className="w-4 h-4" />} {msg.text}
        </div>
      )}

      {/* Quick Purchase Receipt */}
      <form onSubmit={createAndReceivePurchase} className="bg-white border border-slate-200 rounded-xl p-5 space-y-4">
        <h3 className="font-bold text-slate-900">Record & Receive Raw Material Purchase</h3>
        <div className="grid grid-cols-1 sm:grid-cols-4 gap-3">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Supplier</label>
            <select value={supplierId} onChange={(e) => setSupplierId(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg bg-white">
              {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Material</label>
            <select value={rawMatId} onChange={(e) => setRawMatId(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg bg-white">
              {rawMaterials.map((r) => <option key={r.id} value={r.id}>{r.name} ({r.unitSymbol})</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Quantity</label>
            <input type="number" step="0.01" value={qty} onChange={(e) => setQty(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg" required />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Unit Rate (₹)</label>
            <input type="number" step="0.01" value={rate} onChange={(e) => setRate(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg" required />
          </div>
        </div>
        <button type="submit"
          className="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-lg">
          <PackagePlus className="w-4 h-4" /> Receive Purchase into Stock
        </button>
      </form>

      {/* Purchase History */}
      <div className="bg-white border border-slate-200 rounded-xl p-5">
        <h3 className="font-bold text-slate-900 mb-3">Purchase Register</h3>
        {purchases.length === 0 ? (
          <p className="text-sm text-slate-500">No purchases recorded yet.</p>
        ) : (
          <div className="space-y-2">
            {purchases.map((p) => (
              <div key={p.id} className="p-3 border border-slate-200 rounded-lg flex flex-wrap justify-between items-center gap-2 text-sm">
                <div>
                  <span className="font-semibold">{p.referenceNumber}</span> • {p.supplierName} • {p.purchaseDate}
                  <div className="text-xs text-slate-500">
                    {p.items.map((i) => `${i.rawMaterialName || i.packagingMaterialName}: ${i.quantity} ${i.unitSymbol} @ ₹${i.unitRate}`).join(', ')}
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span className="font-bold">₹{Number(p.totalAmount).toFixed(2)}</span>
                  <StatusChip status={p.status} />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Active BOMs */}
      <div className="bg-white border border-slate-200 rounded-xl p-5 space-y-3">
        <h3 className="font-bold text-slate-900">Bill of Materials (BOM)</h3>
        {boms.map((b) => (
          <div key={b.id} className="border border-slate-200 rounded-lg p-4">
            <div className="font-bold text-slate-900">{b.productName} — {b.code} (v{b.version})</div>
            <div className="text-xs text-slate-500 mb-2">1 Batch = {b.expectedOutput} packets</div>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 text-sm">
              {b.items.map((item, idx) => (
                <div key={idx} className="bg-slate-50 px-3 py-1.5 rounded">
                  <span className="font-medium">{item.rawMaterialName || item.packagingMaterialName}</span>:{' '}
                  {item.quantity} {item.unitSymbol}
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

import React, { useEffect, useState } from 'react';
import { Plus, Minus, Search, CheckCircle2 } from 'lucide-react';
import { apiFetch } from '../../services/api';
import { StatusChip } from '../../components/common/StatusChip';

interface Customer {
  id: string;
  name: string;
  type: string;
  state: string;
}

interface Product {
  id: string;
  name: string;
  code: string;
  category: string;
  packSizeDescription: string;
  sellingPrice: number;
  gstRate: number;
}

interface Order {
  id: string;
  orderNumber: string;
  customerName: string;
  orderDate: string;
  status: string;
  grandTotal: number;
  items: { productName: string; orderedQuantity: number; totalAmount: number }[];
}

export const SalesmanOrderPage: React.FC = () => {
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [customerSearch, setCustomerSearch] = useState('');
  const [selectedCustomerId, setSelectedCustomerId] = useState<string>('');
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [notes, setNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);

  const loadData = async () => {
    const [custs, prods, ords] = await Promise.all([
      apiFetch<Customer[]>('/customers'),
      apiFetch<Product[]>('/products'),
      apiFetch<Order[]>('/orders'),
    ]);
    setCustomers(custs);
    setProducts(prods);
    setOrders(ords);
    if (custs.length > 0 && !selectedCustomerId) {
      setSelectedCustomerId(custs[0].id);
    }
  };

  useEffect(() => {
    loadData().catch((e) => setMessage({ type: 'err', text: e.message }));
  }, []);

  const filteredCustomers = customers.filter((c) =>
    c.name.toLowerCase().includes(customerSearch.toLowerCase())
  );

  const adjustQty = (productId: string, delta: number) => {
    setQuantities((prev) => {
      const next = Math.max(0, (prev[productId] || 0) + delta);
      return { ...prev, [productId]: next };
    });
  };

  const selectedItems = products
    .filter((p) => (quantities[p.id] || 0) > 0)
    .map((p) => ({
      product: p,
      qty: quantities[p.id] || 0,
      subtotal: p.sellingPrice * (quantities[p.id] || 0),
    }));

  const runningTotal = selectedItems.reduce((sum, i) => sum + i.subtotal, 0);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedCustomerId || selectedItems.length === 0) {
      setMessage({ type: 'err', text: 'Please select a customer and at least one product.' });
      return;
    }
    setSubmitting(true);
    setMessage(null);
    try {
      const created = await apiFetch<Order>('/orders', {
        method: 'POST',
        body: JSON.stringify({
          customerId: selectedCustomerId,
          orderDate: new Date().toISOString().slice(0, 10),
          notes,
          items: selectedItems.map((i) => ({ productId: i.product.id, quantity: i.qty })),
        }),
      });
      setQuantities({});
      setNotes('');
      setMessage({ type: 'ok', text: `Order ${created.orderNumber} submitted for approval!` });
      await loadData();
    } catch (err) {
      setMessage({ type: 'err', text: err instanceof Error ? err.message : 'Submit failed' });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-slate-900">New Sales Order</h2>
        <p className="text-sm text-slate-500">Select customer, pick packs, and submit.</p>
      </div>

      {message && (
        <div
          className={`p-3 rounded-xl border text-sm flex items-center gap-2 ${
            message.type === 'ok'
              ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
              : 'bg-red-50 border-red-200 text-red-700'
          }`}
        >
          {message.type === 'ok' && <CheckCircle2 className="w-4 h-4 shrink-0" />}
          {message.text}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-5">
        {/* Customer Search & Select */}
        <div className="bg-white border border-slate-200 rounded-xl p-4 space-y-3">
          <label className="block text-sm font-semibold text-slate-800">1. Select Customer</label>
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
            <input
              type="text"
              placeholder="Search customer..."
              value={customerSearch}
              onChange={(e) => setCustomerSearch(e.target.value)}
              className="w-full pl-9 pr-3 py-2 text-base border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <select
            value={selectedCustomerId}
            onChange={(e) => setSelectedCustomerId(e.target.value)}
            className="w-full px-3 py-2.5 text-base border border-slate-300 rounded-lg bg-white"
          >
            {filteredCustomers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.type} — {c.state})
              </option>
            ))}
          </select>
        </div>

        {/* Product Steppers */}
        <div className="bg-white border border-slate-200 rounded-xl p-4 space-y-3">
          <div className="text-sm font-semibold text-slate-800">2. Pick Products</div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {products.map((p) => {
              const qty = quantities[p.id] || 0;
              return (
                <div
                  key={p.id}
                  className={`p-3.5 rounded-xl border transition-colors ${
                    qty > 0 ? 'border-indigo-300 bg-indigo-50/40' : 'border-slate-200'
                  }`}
                >
                  <div className="flex justify-between items-start mb-2">
                    <div>
                      <div className="font-semibold text-slate-900 text-base">{p.name}</div>
                      <div className="text-xs text-slate-500">
                        ₹{p.sellingPrice.toFixed(2)} / pkt • GST {p.gstRate}%
                      </div>
                    </div>
                    {qty > 0 && (
                      <span className="text-sm font-semibold text-indigo-700">
                        ₹{(qty * p.sellingPrice).toFixed(2)}
                      </span>
                    )}
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => adjustQty(p.id, -10)}
                      className="px-2.5 py-2 text-sm font-medium bg-slate-100 hover:bg-slate-200 rounded-lg"
                    >
                      −10
                    </button>
                    <button
                      type="button"
                      onClick={() => adjustQty(p.id, -1)}
                      className="p-2.5 bg-slate-100 hover:bg-slate-200 rounded-lg"
                      aria-label="Decrease"
                    >
                      <Minus className="w-4 h-4" />
                    </button>
                    <input
                      type="number"
                      min={0}
                      value={qty}
                      onChange={(e) =>
                        setQuantities((prev) => ({
                          ...prev,
                          [p.id]: Math.max(0, parseInt(e.target.value || '0', 10)),
                        }))
                      }
                      className="w-20 text-center py-2 text-base font-semibold border border-slate-300 rounded-lg"
                    />
                    <button
                      type="button"
                      onClick={() => adjustQty(p.id, 1)}
                      className="p-2.5 bg-indigo-50 text-indigo-700 hover:bg-indigo-100 rounded-lg"
                      aria-label="Increase"
                    >
                      <Plus className="w-4 h-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => adjustQty(p.id, 10)}
                      className="px-2.5 py-2 text-sm font-medium bg-indigo-50 text-indigo-700 hover:bg-indigo-100 rounded-lg"
                    >
                      +10
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Review & Submit */}
        <div className="bg-white border border-slate-200 rounded-xl p-4 space-y-3">
          <div className="flex justify-between items-center">
            <span className="text-sm text-slate-600">Estimated Subtotal (before GST)</span>
            <span className="text-xl font-bold text-slate-900">₹{runningTotal.toFixed(2)}</span>
          </div>
          <input
            type="text"
            placeholder="Delivery notes (optional)"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            className="w-full px-3 py-2 text-base border border-slate-300 rounded-lg"
          />
          <button
            type="submit"
            disabled={submitting || selectedItems.length === 0}
            className="w-full py-3.5 px-4 bg-indigo-600 hover:bg-indigo-700 text-white text-base font-semibold rounded-xl disabled:opacity-50"
          >
            {submitting ? 'Submitting...' : 'Submit order'}
          </button>
        </div>
      </form>

      {/* My Recent Orders */}
      <div className="bg-white border border-slate-200 rounded-xl p-4">
        <h3 className="text-base font-bold text-slate-900 mb-3">My Orders</h3>
        {orders.length === 0 ? (
          <p className="text-sm text-slate-500">No orders yet.</p>
        ) : (
          <div className="space-y-3">
            {orders.map((o) => (
              <div key={o.id} className="p-3 border border-slate-200 rounded-lg flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-slate-900">{o.orderNumber}</span>
                    <StatusChip status={o.status} />
                  </div>
                  <div className="text-sm text-slate-600">{o.customerName} • {o.orderDate}</div>
                  <div className="text-xs text-slate-500">
                    {o.items.map((i) => `${i.productName} × ${i.orderedQuantity}`).join(', ')}
                  </div>
                </div>
                <div className="text-base font-bold text-slate-900">₹{Number(o.grandTotal).toFixed(2)}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

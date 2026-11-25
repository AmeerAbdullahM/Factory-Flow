import React, { useEffect, useState } from 'react';
import { FileDown, Truck, CreditCard, AlertTriangle, Check, X } from 'lucide-react';
import { apiFetch, downloadBlob } from '../../services/api';
import { StatusChip } from '../../components/common/StatusChip';

interface OrderItem {
  id: string;
  productId: string;
  productName: string;
  orderedQuantity: number;
  approvedQuantity: number;
  unitPrice: number;
  totalAmount: number;
}

interface Order {
  id: string;
  orderNumber: string;
  customerId: string;
  customerName: string;
  customerState: string;
  salesmanName: string;
  orderDate: string;
  status: string;
  grandTotal: number;
  items: OrderItem[];
}

interface StockCheck {
  orderItemId: string;
  productId: string;
  productName: string;
  requiredQty: number;
  availableQty: number;
  sufficient: boolean;
  shortageQty: number;
}

interface Invoice {
  id: string;
  invoiceNumber: string;
  salesOrderId: string;
  customerId: string;
  customerName: string;
  invoiceDate: string;
  interstate: boolean;
  status: string;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  totalAmount: number;
  items: { productId: string; productName: string; quantity: number }[];
}

interface Outstanding {
  customerId: string;
  customerName: string;
  invoiceId: string;
  invoiceNumber: string;
  invoiceDate: string;
  invoiceTotal: number;
  paidAmount: number;
  outstanding: number;
  daysSinceInvoice: number;
  agingBucket: string;
  overdue: boolean;
}

export const AdminOrdersPage: React.FC = () => {
  const [orders, setOrders] = useState<Order[]>([]);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [outstanding, setOutstanding] = useState<Outstanding[]>([]);
  const [stockChecks, setStockChecks] = useState<Record<string, StockCheck[]>>({});
  const [adjustedQtys, setAdjustedQtys] = useState<Record<string, number>>({});
  const [payAmount, setPayAmount] = useState<Record<string, string>>({});
  const [msg, setMsg] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);

  const loadAll = async () => {
    const [ords, invs, outs] = await Promise.all([
      apiFetch<Order[]>('/orders'),
      apiFetch<Invoice[]>('/invoices'),
      apiFetch<Outstanding[]>('/payments/outstanding'),
    ]);
    setOrders(ords);
    setInvoices(invs);
    setOutstanding(outs);

    const checks: Record<string, StockCheck[]> = {};
    for (const o of ords.filter((x) => x.status === 'SUBMITTED' || x.status === 'APPROVED')) {
      try {
        checks[o.id] = await apiFetch<StockCheck[]>(`/orders/${o.id}/stock-check`);
      } catch {
        // ignore
      }
    }
    setStockChecks(checks);
  };

  useEffect(() => {
    loadAll().catch((e) => setMsg({ type: 'err', text: e.message }));
  }, []);

  const approveOrder = async (o: Order) => {
    try {
      const adjustments = o.items.map((item) => ({
        orderItemId: item.id,
        approvedQuantity:
          adjustedQtys[item.id] !== undefined ? adjustedQtys[item.id] : item.approvedQuantity ?? item.orderedQuantity,
      }));
      await apiFetch(`/orders/${o.id}/approve`, {
        method: 'POST',
        body: JSON.stringify({ adjustments }),
      });
      setMsg({ type: 'ok', text: `Order ${o.orderNumber} approved.` });
      await loadAll();
    } catch (e) {
      setMsg({ type: 'err', text: e instanceof Error ? e.message : 'Approve failed' });
    }
  };

  const rejectOrder = async (o: Order) => {
    try {
      await apiFetch(`/orders/${o.id}/reject`, {
        method: 'POST',
        body: JSON.stringify({ reason: 'Rejected by admin' }),
      });
      setMsg({ type: 'ok', text: `Order ${o.orderNumber} rejected.` });
      await loadAll();
    } catch (e) {
      setMsg({ type: 'err', text: e instanceof Error ? e.message : 'Reject failed' });
    }
  };

  const createInvoice = async (orderId: string) => {
    try {
      const inv = await apiFetch<Invoice>('/invoices', {
        method: 'POST',
        body: JSON.stringify({ salesOrderId: orderId, invoiceDate: new Date().toISOString().slice(0, 10) }),
      });
      setMsg({ type: 'ok', text: `Invoice ${inv.invoiceNumber} generated!` });
      await loadAll();
    } catch (e) {
      setMsg({ type: 'err', text: e instanceof Error ? e.message : 'Invoice failed' });
    }
  };

  const recordDispatch = async (inv: Invoice) => {
    try {
      await apiFetch('/dispatches', {
        method: 'POST',
        body: JSON.stringify({
          invoiceId: inv.id,
          dispatchDate: new Date().toISOString().slice(0, 10),
          transportDetails: 'Factory Van',
          deliveryReference: `LR-${Date.now()}`,
          items: inv.items.map((i) => ({ productId: i.productId, quantity: i.quantity })),
        }),
      });
      setMsg({ type: 'ok', text: `Dispatched ${inv.invoiceNumber}. FG stock updated.` });
      await loadAll();
    } catch (e) {
      setMsg({ type: 'err', text: e instanceof Error ? e.message : 'Dispatch failed' });
    }
  };

  const recordPayment = async (o: Outstanding) => {
    const amt = parseFloat(payAmount[o.invoiceId] || '0');
    if (amt <= 0) return;
    try {
      await apiFetch('/payments', {
        method: 'POST',
        body: JSON.stringify({
          customerId: o.customerId,
          paymentDate: new Date().toISOString().slice(0, 10),
          paymentMethod: 'UPI',
          amount: amt,
          allocations: [{ invoiceId: o.invoiceId, amount: amt }],
        }),
      });
      setPayAmount((prev) => ({ ...prev, [o.invoiceId]: '' }));
      setMsg({ type: 'ok', text: `Recorded ₹${amt.toFixed(2)} payment for ${o.invoiceNumber}.` });
      await loadAll();
    } catch (e) {
      setMsg({ type: 'err', text: e instanceof Error ? e.message : 'Payment failed' });
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-slate-900">Orders, GST Billing & Dispatch</h2>
        <p className="text-sm text-slate-500">Verify FG stock coverage, approve orders, raise GST invoices, dispatch, and collect payments.</p>
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

      {/* Incoming & Active Orders */}
      <div className="bg-white border border-slate-200 rounded-xl p-5 space-y-4">
        <h3 className="font-bold text-slate-900">Sales Orders</h3>
        {orders.length === 0 ? (
          <p className="text-sm text-slate-500">No sales orders yet.</p>
        ) : (
          <div className="space-y-4">
            {orders.map((o) => {
              const checks = stockChecks[o.id] || [];
              return (
                <div key={o.id} className="border border-slate-200 rounded-xl p-4 space-y-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-slate-900">{o.orderNumber}</span>
                        <StatusChip status={o.status} />
                      </div>
                      <div className="text-sm text-slate-600">
                        {o.customerName} ({o.customerState}) • By {o.salesmanName} • {o.orderDate}
                      </div>
                    </div>
                    <div className="text-lg font-bold text-slate-900">₹{Number(o.grandTotal).toFixed(2)}</div>
                  </div>

                  {/* Line items + stock check */}
                  <div className="space-y-2">
                    {o.items.map((item) => {
                      const chk = checks.find((c) => c.orderItemId === item.id);
                      const qtyVal = adjustedQtys[item.id] ?? item.approvedQuantity ?? item.orderedQuantity;
                      return (
                        <div key={item.id} className="flex flex-wrap items-center justify-between gap-2 bg-slate-50 px-3 py-2 rounded-lg text-sm">
                          <div>
                            <span className="font-medium text-slate-900">{item.productName}</span>
                            <span className="text-slate-500 ml-2">Ordered: {item.orderedQuantity} pkt @ ₹{item.unitPrice}</span>
                          </div>
                          {chk && (
                            <div className="flex items-center gap-2">
                              {chk.sufficient ? (
                                <span className="text-xs font-medium text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded-full">
                                  Available: {chk.availableQty} pkt
                                </span>
                              ) : (
                                <span className="inline-flex items-center gap-1 text-xs font-semibold text-red-700 bg-red-50 border border-red-200 px-2.5 py-0.5 rounded-full">
                                  <AlertTriangle className="w-3.5 h-3.5" />
                                  Short by {chk.shortageQty} packets (Avail: {chk.availableQty})
                                </span>
                              )}
                              {o.status === 'SUBMITTED' && (
                                <input
                                  type="number"
                                  min={0}
                                  value={qtyVal}
                                  onChange={(e) =>
                                    setAdjustedQtys((prev) => ({
                                      ...prev,
                                      [item.id]: Math.max(0, parseInt(e.target.value || '0', 10)),
                                    }))
                                  }
                                  className="w-20 px-2 py-1 text-sm border border-slate-300 rounded bg-white"
                                  title="Approved quantity"
                                />
                              )}
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>

                  {/* Actions */}
                  <div className="flex flex-wrap gap-2 pt-1">
                    {o.status === 'SUBMITTED' && (
                      <>
                        <button
                          onClick={() => approveOrder(o)}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium rounded-lg"
                        >
                          <Check className="w-4 h-4" /> Approve
                        </button>
                        <button
                          onClick={() => rejectOrder(o)}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-red-50 hover:bg-red-100 text-red-700 border border-red-200 text-sm font-medium rounded-lg"
                        >
                          <X className="w-4 h-4" /> Reject
                        </button>
                      </>
                    )}
                    {o.status === 'APPROVED' && (
                      <button
                        onClick={() => createInvoice(o.id)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium rounded-lg"
                      >
                        Raise GST Invoice
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Invoices & Dispatch */}
      <div className="bg-white border border-slate-200 rounded-xl p-5 space-y-3">
        <h3 className="font-bold text-slate-900">GST Invoices & Dispatch</h3>
        {invoices.length === 0 ? (
          <p className="text-sm text-slate-500">No invoices generated yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="text-xs uppercase text-slate-500 border-b border-slate-200">
                <tr>
                  <th className="py-2 pr-3">Invoice</th>
                  <th className="py-2 pr-3">Customer</th>
                  <th className="py-2 pr-3">Taxable</th>
                  <th className="py-2 pr-3">GST</th>
                  <th className="py-2 pr-3">Total</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {invoices.map((inv) => (
                  <tr key={inv.id}>
                    <td className="py-2.5 pr-3 font-medium">{inv.invoiceNumber}</td>
                    <td className="py-2.5 pr-3">{inv.customerName}</td>
                    <td className="py-2.5 pr-3">₹{Number(inv.taxableAmount).toFixed(2)}</td>
                    <td className="py-2.5 pr-3">
                      {inv.interstate
                        ? `IGST ₹${Number(inv.igstAmount).toFixed(2)}`
                        : `CGST ₹${Number(inv.cgstAmount).toFixed(2)} + SGST ₹${Number(inv.sgstAmount).toFixed(2)}`}
                    </td>
                    <td className="py-2.5 pr-3 font-bold">₹{Number(inv.totalAmount).toFixed(2)}</td>
                    <td className="py-2.5 pr-3"><StatusChip status={inv.status} /></td>
                    <td className="py-2.5 flex flex-wrap gap-2">
                      <button
                        onClick={() => downloadBlob(`/invoices/${inv.id}/pdf`, `${inv.invoiceNumber.replace(/\//g, '-')}.pdf`)}
                        className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium bg-slate-100 hover:bg-slate-200 rounded-md"
                      >
                        <FileDown className="w-3.5 h-3.5" /> PDF
                      </button>
                      {inv.status === 'ACTIVE' && (
                        <button
                          onClick={() => recordDispatch(inv)}
                          className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium bg-indigo-50 text-indigo-700 hover:bg-indigo-100 rounded-md"
                        >
                          <Truck className="w-3.5 h-3.5" /> Dispatch
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Outstanding & Payments */}
      <div className="bg-white border border-slate-200 rounded-xl p-5 space-y-3">
        <h3 className="font-bold text-slate-900">Outstanding Receivables & Collections</h3>
        {outstanding.length === 0 ? (
          <p className="text-sm text-slate-500">All invoices are settled.</p>
        ) : (
          <div className="space-y-2">
            {outstanding.map((o) => (
              <div key={o.invoiceId} className="flex flex-wrap items-center justify-between gap-3 p-3 border border-slate-200 rounded-lg">
                <div>
                  <div className="font-semibold text-slate-900">{o.customerName} — {o.invoiceNumber}</div>
                  <div className="text-xs text-slate-500">
                    Total: ₹{Number(o.invoiceTotal).toFixed(2)} • Paid: ₹{Number(o.paidAmount).toFixed(2)} • Bucket: {o.agingBucket} days
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className="font-bold text-amber-700">Due: ₹{Number(o.outstanding).toFixed(2)}</span>
                  <input
                    type="number"
                    placeholder="Amount"
                    value={payAmount[o.invoiceId] || ''}
                    onChange={(e) => setPayAmount((p) => ({ ...p, [o.invoiceId]: e.target.value }))}
                    className="w-28 px-2 py-1 text-sm border border-slate-300 rounded-lg"
                  />
                  <button
                    onClick={() => recordPayment(o)}
                    className="inline-flex items-center gap-1 px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-medium rounded-lg"
                  >
                    <CreditCard className="w-3.5 h-3.5" /> Record Payment
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

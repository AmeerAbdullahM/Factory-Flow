import React from 'react';

const STATUS_STYLES: Record<string, string> = {
  SUBMITTED: 'bg-blue-50 text-blue-700 border-blue-200',
  DRAFT: 'bg-slate-100 text-slate-700 border-slate-200',
  APPROVED: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  INVOICED: 'bg-amber-50 text-amber-800 border-amber-200',
  PARTIALLY_DISPATCHED: 'bg-orange-50 text-orange-800 border-orange-200',
  DISPATCHED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  POSTED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  RECEIVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  ACTIVE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  REJECTED: 'bg-red-50 text-red-700 border-red-200',
  CANCELLED: 'bg-red-50 text-red-700 border-red-200',
};

export const StatusChip: React.FC<{ status: string }> = ({ status }) => {
  const cls = STATUS_STYLES[status] || 'bg-slate-100 text-slate-700 border-slate-200';
  const label = status.replace(/_/g, ' ');
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${cls}`}>
      {label}
    </span>
  );
};

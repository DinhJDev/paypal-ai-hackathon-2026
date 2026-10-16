"use client";

import { useMemo } from "react";
import { AllCommunityModule, type ColDef, type ICellRendererParams, themeQuartz } from "ag-grid-community";
import { AgGridProvider, AgGridReact } from "ag-grid-react";
import type { RentSchedule } from "@/lib/rent-dashboard-model";

function money(amount: number): string {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(amount);
}

function statusStyles(status: RentSchedule["status"]): string {
  switch (status) {
    case "ACTIVE": return "border-emerald-200 bg-emerald-50 text-emerald-800";
    case "PENDING_APPROVAL": return "border-amber-200 bg-amber-50 text-amber-800";
    case "PAYMENT_FAILED":
    case "SETUP_FAILED":
    case "PAST_DUE": return "border-rose-200 bg-rose-50 text-rose-800";
    case "PAUSED": return "border-slate-200 bg-slate-100 text-slate-700";
  }
}

function statusLabel(schedule: RentSchedule): string {
  if (schedule.status === "ACTIVE" && !schedule.autopayEnabled) return "approved · charges off";
  return schedule.status.toLowerCase().replaceAll("_", " ");
}

type PortfolioGridProps = {
  schedules: RentSchedule[];
  searchText: string;
  updatingScheduleId: string | null;
  onUpdateSchedule: (id: string, action: "approval-check" | "pause") => void;
};

export function PortfolioGrid({ schedules, searchText, updatingScheduleId, onUpdateSchedule }: PortfolioGridProps) {
  const columnDefs = useMemo<ColDef<RentSchedule>[]>(() => [
    {
      headerName: "Suite / tenant",
      field: "tenantLabel",
      flex: 1.4,
      minWidth: 175,
      cellClass: "font-medium text-slate-950",
    },
    {
      headerName: "Rent",
      field: "amount",
      minWidth: 130,
      valueFormatter: params => typeof params.value === "number" ? money(params.value) : "—",
    },
    {
      headerName: "Billing",
      field: "frequency",
      minWidth: 115,
      valueFormatter: params => typeof params.value === "string"
        ? params.value.charAt(0) + params.value.slice(1).toLowerCase()
        : "—",
    },
    {
      headerName: "Next due",
      field: "nextDueDate",
      minWidth: 140,
      valueFormatter: params => {
        if (typeof params.value !== "string") return "—";
        const date = new Date(params.value + "T12:00:00");
        return Number.isNaN(date.getTime()) ? params.value : new Intl.DateTimeFormat("en-US", {
          month: "short", day: "numeric", year: "numeric",
        }).format(date);
      },
    },
    {
      headerName: "Autopay",
      field: "status",
      minWidth: 155,
      cellRenderer: (params: ICellRendererParams<RentSchedule>) => {
        const schedule = params.data;
        const status = schedule?.status;
        return status && schedule
          ? <span className={"inline-flex rounded-full border px-2.5 py-1 text-xs font-medium capitalize " + statusStyles(status)}>{statusLabel(schedule)}</span>
          : null;
      },
    },
    {
      headerName: "Actions",
      colId: "actions",
      sortable: false,
      filter: false,
      resizable: false,
      minWidth: 225,
      cellRenderer: (params: ICellRendererParams<RentSchedule>) => {
        const schedule = params.data;
        if (!schedule) return null;
        const busy = updatingScheduleId === schedule.id;
        return (
          <div className="flex h-full items-center gap-3 text-xs">
            {schedule.status === "PENDING_APPROVAL" && schedule.approvalUrl && <>
              <a href={schedule.approvalUrl} target="_blank" rel="noreferrer" className="font-medium underline underline-offset-4">Open approval</a>
              <button type="button" disabled={busy} onClick={() => onUpdateSchedule(schedule.id, "approval-check")}
                className="cursor-pointer font-medium underline underline-offset-4 disabled:cursor-wait disabled:opacity-50">
                {busy ? "Checking…" : "Check approval"}
              </button>
            </>}
            {schedule.status === "ACTIVE" && <button type="button" disabled={busy}
              onClick={() => onUpdateSchedule(schedule.id, "pause")}
              className="cursor-pointer font-medium underline underline-offset-4 disabled:cursor-wait disabled:opacity-50">
              {busy ? "Pausing…" : "Pause autopay"}
            </button>}
            {schedule.status !== "PENDING_APPROVAL" && schedule.status !== "ACTIVE" && <span className="text-slate-400">—</span>}
          </div>
        );
      },
    },
  ], [onUpdateSchedule, updatingScheduleId]);

  const defaultColDef = useMemo<ColDef<RentSchedule>>(() => ({
    sortable: true,
    resizable: true,
    suppressMovable: true,
    unSortIcon: true,
  }), []);

  return (
    <div role="region" aria-label="Rent schedule portfolio" className="overflow-x-auto">
      <div className="min-w-[930px]">
        <AgGridProvider modules={[AllCommunityModule]}>
          <AgGridReact<RentSchedule>
            rowData={schedules}
            columnDefs={columnDefs}
            defaultColDef={defaultColDef}
            quickFilterText={searchText}
            theme={themeQuartz}
            domLayout="autoHeight"
            rowHeight={60}
            headerHeight={44}
            getRowId={params => params.data.id}
            overlayNoRowsTemplate="<span class='ag-overlay-no-rows-center'>No rent schedules match your search.</span>"
          />
        </AgGridProvider>
      </div>
    </div>
  );
}

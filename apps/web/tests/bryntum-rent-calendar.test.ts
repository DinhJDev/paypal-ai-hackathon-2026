import { describe, expect, it } from "vitest";
import { mapRentEventsToBryntum } from "@/lib/bryntum-rent-calendar";
import type { RentCalendarEvent } from "@/lib/rent-dashboard-model";

describe("Bryntum rent calendar adapter", () => {
  it("maps Suitefolk due dates and actual payment events to read-only all-day calendar entries", () => {
    const events: RentCalendarEvent[] = [
      {
        id: "suite-1:due:2026-10-16",
        scheduleId: "suite-1",
        tenantLabel: "Suite 1",
        date: "2026-10-16",
        amount: 1200,
        kind: "RENT_DUE",
        status: "ACTIVE",
      },
      {
        id: "suite-2:payment:2026-10-15:FAILED",
        scheduleId: "suite-2",
        tenantLabel: "Suite 2",
        date: "2026-10-15",
        amount: 800,
        kind: "PAYMENT",
        status: "FAILED",
      },
    ];

    expect(mapRentEventsToBryntum(events)).toEqual([
      {
        id: "suite-1:due:2026-10-16",
        name: "Rent due · Suite 1 · $1,200.00",
        startDate: "2026-10-16T00:00:00",
        endDate: "2026-10-17T00:00:00",
        allDay: true,
      },
      {
        id: "suite-2:payment:2026-10-15:FAILED",
        name: "Payment failed · Suite 2 · $800.00",
        startDate: "2026-10-15T00:00:00",
        endDate: "2026-10-16T00:00:00",
        allDay: true,
      },
    ]);
  });
});

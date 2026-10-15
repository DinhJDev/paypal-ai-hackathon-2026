"use client";

import { useMemo, useState } from "react";
import { BryntumCalendar } from "@bryntum/calendar-react";
import type { BryntumCalendarProps } from "@bryntum/calendar-react";
import { mapRentEventsToBryntum } from "@/lib/bryntum-rent-calendar";
import type { RentCalendarEvent } from "@/lib/rent-dashboard-model";

type CalendarConfig = Pick<BryntumCalendarProps, "date" | "mode" | "modes" | "readOnly" | "sidebar">;

export function RentCalendar({ events }: { events: RentCalendarEvent[] }) {
  const [calendarConfig] = useState<CalendarConfig>(() => ({
    date: new Date(),
    mode: "month",
    modes: { month: { readOnly: true } },
    readOnly: true,
    sidebar: false,
  }));
  const bryntumEvents = useMemo(() => mapRentEventsToBryntum(events), [events]);

  return (
    <div role="region" aria-label="Rent due and payment calendar" className="h-[680px] min-w-0 w-full p-3 sm:p-5">
      <BryntumCalendar {...calendarConfig} events={bryntumEvents} />
    </div>
  );
}

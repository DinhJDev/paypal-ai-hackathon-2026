import type { EventModelConfig } from "@bryntum/calendar";
import type { RentCalendarEvent } from "@/lib/rent-dashboard-model";

function followingDate(date: string): string {
  const parsed = new Date(`${date}T00:00:00.000Z`);
  parsed.setUTCDate(parsed.getUTCDate() + 1);
  return parsed.toISOString().slice(0, 10);
}

function eventName(event: RentCalendarEvent): string {
  const amount = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(event.amount);
  if (event.kind === "RENT_DUE") return `Rent due · ${event.tenantLabel} · ${amount}`;

  const paymentLabel: Record<Extract<RentCalendarEvent, { kind: "PAYMENT" }>["status"], string> = {
    PROCESSING: "Payment processing",
    COMPLETED: "Rent received",
    FAILED: "Payment failed",
    REFUNDED: "Rent refunded",
  };
  return `${paymentLabel[event.status as keyof typeof paymentLabel]} · ${event.tenantLabel} · ${amount}`;
}

/** Convert Suitefolk's vendor-neutral rent dates to Bryntum's all-day event shape. */
export function mapRentEventsToBryntum(events: RentCalendarEvent[]): EventModelConfig[] {
  return events.map(event => ({
    id: event.id,
    name: eventName(event),
    startDate: `${event.date}T00:00:00`,
    endDate: `${followingDate(event.date)}T00:00:00`,
    allDay: true,
  }));
}

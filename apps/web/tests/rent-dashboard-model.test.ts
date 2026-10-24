import { describe, expect, it } from "vitest";
import {
  buildRentAttentionItems,
  buildRentCalendarEvents,
  buildRecentRentPayments,
  summarizeRentSchedules,
  type RentSchedule,
} from "@/lib/rent-dashboard-model";

const schedule = (overrides: Partial<RentSchedule> & { autopayEnabled?: boolean } = {}): RentSchedule => ({
  id: "f216e512-81d4-4f50-a0fc-317952e91f15",
  tenantLabel: "Suite 1",
  amount: 500,
  currency: "USD",
  frequency: "MONTHLY",
  nextDueDate: "2026-10-16",
  status: "ACTIVE",
  autopayEnabled: true,
  approvalUrl: null,
  payments: [],
  ...overrides,
});

describe("rent dashboard model", () => {
  it("summarizes due amounts and operational statuses from schedules", () => {
    const schedules = [
      schedule({ nextDueDate: "2026-10-15", amount: 250 }),
      schedule({ id: "suite-2", status: "PENDING_APPROVAL", nextDueDate: "2026-10-22", amount: 800 }),
      schedule({ id: "suite-3", status: "PAYMENT_FAILED", nextDueDate: "2026-10-23", amount: 1200 }),
      schedule({ id: "suite-4", status: "SETUP_FAILED", nextDueDate: "2026-10-16", amount: 1000 }),
      schedule({ id: "suite-5", status: "PAUSED", nextDueDate: "2026-10-20", amount: 350 }),
    ];

    expect(summarizeRentSchedules(schedules, "2026-10-15")).toEqual({
      activeAutopayCount: 1,
      pendingApprovalCount: 1,
      paymentIssueCount: 1,
      dueWithinSevenDaysCount: 3,
      dueWithinSevenDaysAmount: 1400,
    });
  });

  it("counts only schedules with automatic charges enabled", () => {
    const schedules = [
      schedule(),
      schedule({ id: "charges-off", autopayEnabled: false }),
    ];

    expect(summarizeRentSchedules(schedules, "2026-10-15").activeAutopayCount).toBe(1);
  });

  it("builds a vendor-neutral, date-sorted calendar feed from upcoming rent and recorded payments", () => {
    const schedules = [
      schedule({
        payments: [{ dueDate: "2026-10-02", amount: 500, currency: "USD", status: "COMPLETED" }],
      }),
      schedule({
        id: "suite-2",
        tenantLabel: "Suite 2",
        status: "PENDING_APPROVAL",
        nextDueDate: "2026-10-22",
        amount: 800,
      }),
      schedule({
        id: "suite-3",
        tenantLabel: "Suite 3",
        nextDueDate: "2026-11-01",
      }),
    ];

    expect(buildRentCalendarEvents(schedules, "2026-10-01", "2026-10-31")).toEqual([
      {
        id: "f216e512-81d4-4f50-a0fc-317952e91f15:payment:2026-10-02:COMPLETED",
        scheduleId: "f216e512-81d4-4f50-a0fc-317952e91f15",
        tenantLabel: "Suite 1",
        date: "2026-10-02",
        amount: 500,
        kind: "PAYMENT",
        status: "COMPLETED",
      },
      {
        id: "f216e512-81d4-4f50-a0fc-317952e91f15:due:2026-10-16",
        scheduleId: "f216e512-81d4-4f50-a0fc-317952e91f15",
        tenantLabel: "Suite 1",
        date: "2026-10-16",
        amount: 500,
        kind: "RENT_DUE",
        status: "ACTIVE",
      },
      {
        id: "suite-2:due:2026-10-22",
        scheduleId: "suite-2",
        tenantLabel: "Suite 2",
        date: "2026-10-22",
        amount: 800,
        kind: "RENT_DUE",
        status: "PENDING_APPROVAL",
      },
    ]);
  });

  it("surfaces only schedules that need owner follow-up, with a useful reason", () => {
    const schedules = [
      schedule({ id: "active" }),
      schedule({ id: "pending", tenantLabel: "Suite 2", status: "PENDING_APPROVAL" }),
      schedule({ id: "failed", tenantLabel: "Suite 3", status: "PAYMENT_FAILED" }),
      schedule({ id: "setup", tenantLabel: "Suite 4", status: "SETUP_FAILED" }),
      schedule({ id: "past-due", tenantLabel: "Suite 5", status: "PAST_DUE" }),
    ];

    expect(buildRentAttentionItems(schedules)).toEqual([
      { scheduleId: "past-due", tenantLabel: "Suite 5", status: "PAST_DUE", message: "A rent date was missed. Automatic charges are paused for owner review." },
      { scheduleId: "failed", tenantLabel: "Suite 3", status: "PAYMENT_FAILED", message: "A rent payment needs attention." },
      { scheduleId: "setup", tenantLabel: "Suite 4", status: "SETUP_FAILED", message: "Autopay setup needs attention." },
      { scheduleId: "pending", tenantLabel: "Suite 2", status: "PENDING_APPROVAL", message: "Tenant approval is still pending." },
    ]);
  });

  it("lists recorded payments newest first without inventing recurring entries", () => {
    const schedules = [
      schedule({ payments: [
        { dueDate: "2026-09-16", amount: 500, currency: "USD", status: "COMPLETED" },
        { dueDate: "2026-10-16", amount: 500, currency: "USD", status: "PROCESSING" },
      ] }),
      schedule({ id: "suite-2", tenantLabel: "Suite 2", payments: [
        { dueDate: "2026-10-16", amount: 800, currency: "USD", status: "FAILED" },
      ] }),
    ];

    expect(buildRecentRentPayments(schedules)).toEqual([
      { scheduleId: "suite-2", tenantLabel: "Suite 2", dueDate: "2026-10-16", amount: 800, status: "FAILED" },
      { scheduleId: "f216e512-81d4-4f50-a0fc-317952e91f15", tenantLabel: "Suite 1", dueDate: "2026-10-16", amount: 500, status: "PROCESSING" },
      { scheduleId: "f216e512-81d4-4f50-a0fc-317952e91f15", tenantLabel: "Suite 1", dueDate: "2026-09-16", amount: 500, status: "COMPLETED" },
    ]);
  });
});

it("surfaces ledger review holds even when the schedule still has approved autopay", () => {
  const schedules = [schedule({ status: "ACTIVE", reviewRequiredCount: 1 })];
  expect(summarizeRentSchedules(schedules, "2026-10-15").paymentIssueCount).toBe(1);
  expect(buildRentAttentionItems(schedules)).toEqual([{
    scheduleId: schedules[0].id, tenantLabel: "Suite 1", status: "OWNER_REVIEW",
    message: "A rent period needs owner review. Check the ledger before further collection.",
  }]);
});

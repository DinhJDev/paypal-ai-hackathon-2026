export type RentFrequency = "WEEKLY" | "MONTHLY";
export type RentScheduleStatus =
  | "SCHEDULED"
  | "PENDING_APPROVAL"
  | "SETUP_FAILED"
  | "ACTIVE"
  | "PAUSED"
  | "PAYMENT_FAILED"
  | "PAST_DUE";
export type RentPaymentStatus = "PROCESSING" | "COMPLETED" | "FAILED" | "REFUNDED";

export type RentPayment = {
  dueDate: string;
  amount: number;
  currency: "USD";
  status: RentPaymentStatus;
};

export type RentSchedule = {
  id: string;
  tenantLabel: string;
  amount: number;
  currency: "USD";
  frequency: RentFrequency;
  nextDueDate: string;
  status: RentScheduleStatus;
  autopayEnabled: boolean;
  approvalUrl: string | null;
  payments: RentPayment[];
  autopayStartDate?: string;
  endDate?: string | null;
  scheduleEnded?: boolean;
  outstandingBalance?: number;
  reviewRequiredCount?: number;
};

export type RentCalendarEvent =
  | {
      id: string;
      scheduleId: string;
      tenantLabel: string;
      date: string;
      amount: number;
      kind: "RENT_DUE";
      status: RentScheduleStatus;
    }
  | {
      id: string;
      scheduleId: string;
      tenantLabel: string;
      date: string;
      amount: number;
      kind: "PAYMENT";
      status: RentPaymentStatus;
    };

export type RentDashboardSummary = {
  activeAutopayCount: number;
  pendingApprovalCount: number;
  paymentIssueCount: number;
  dueWithinSevenDaysCount: number;
  dueWithinSevenDaysAmount: number;
};

export type RentAttentionItem = {
  scheduleId: string;
  tenantLabel: string;
  status: Extract<RentScheduleStatus, "PENDING_APPROVAL" | "SETUP_FAILED" | "PAYMENT_FAILED" | "PAST_DUE"> | "OWNER_REVIEW";
  message: string;
};

export type RentActivityItem = {
  scheduleId: string;
  tenantLabel: string;
  dueDate: string;
  amount: number;
  status: RentPaymentStatus;
};

function isDueWithinSevenDays(dueDate: string, today: string): boolean {
  const daysUntilDue = (Date.parse(dueDate + "T00:00:00.000Z") - Date.parse(today + "T00:00:00.000Z"))
    / 86_400_000;
  return daysUntilDue >= 0 && daysUntilDue <= 7;
}

export function summarizeRentSchedules(
  schedules: RentSchedule[],
  today: string,
): RentDashboardSummary {
  const dueSoon = schedules.filter(schedule =>
    !schedule.scheduleEnded && schedule.status !== "SETUP_FAILED" && isDueWithinSevenDays(schedule.nextDueDate, today),
  );

  const dueSoonCents = dueSoon.reduce((total, schedule) => total + Math.round(schedule.amount * 100), 0);

  return {
    activeAutopayCount: schedules.filter(schedule => !schedule.scheduleEnded && schedule.status === "ACTIVE" && schedule.autopayEnabled).length,
    pendingApprovalCount: schedules.filter(schedule => !schedule.scheduleEnded && schedule.status === "PENDING_APPROVAL").length,
    paymentIssueCount: schedules.filter(schedule => schedule.status === "PAYMENT_FAILED" || schedule.status === "PAST_DUE" || (schedule.reviewRequiredCount ?? 0) > 0).length,
    dueWithinSevenDaysCount: dueSoon.length,
    dueWithinSevenDaysAmount: dueSoonCents / 100,
  };
}

export function buildRentAttentionItems(schedules: RentSchedule[]): RentAttentionItem[] {
  const attentionOrder: Record<RentAttentionItem["status"], number> = {
    OWNER_REVIEW: -1,
    PAST_DUE: 0,
    PAYMENT_FAILED: 1,
    SETUP_FAILED: 2,
    PENDING_APPROVAL: 3,
  };
  const messages: Record<RentAttentionItem["status"], string> = {
    OWNER_REVIEW: "A rent period needs owner review. Check the ledger before further collection.",
    PAST_DUE: "A rent date was missed. Automatic charges are paused for owner review.",
    PAYMENT_FAILED: "A rent payment needs attention.",
    SETUP_FAILED: "Autopay setup needs attention.",
    PENDING_APPROVAL: "Tenant approval is still pending.",
  };

  return schedules.flatMap(schedule => {
    const status: RentAttentionItem["status"] | null = (schedule.reviewRequiredCount ?? 0) > 0 ? "OWNER_REVIEW"
      : schedule.status === "PAST_DUE" || schedule.status === "PAYMENT_FAILED" ||
        !schedule.scheduleEnded && (schedule.status === "SETUP_FAILED" || schedule.status === "PENDING_APPROVAL") ? schedule.status as RentAttentionItem["status"] : null;
    return status ? [{ scheduleId: schedule.id, tenantLabel: schedule.tenantLabel, status, message: messages[status] }] : [];
  }).sort((left, right) => attentionOrder[left.status] - attentionOrder[right.status] ||
    left.tenantLabel.localeCompare(right.tenantLabel));
}

export function buildRecentRentPayments(schedules: RentSchedule[]): RentActivityItem[] {
  const statusOrder: Record<RentPaymentStatus, number> = {
    FAILED: 0,
    PROCESSING: 1,
    REFUNDED: 2,
    COMPLETED: 3,
  };

  return schedules.flatMap(schedule => schedule.payments.map(payment => ({
    scheduleId: schedule.id,
    tenantLabel: schedule.tenantLabel,
    dueDate: payment.dueDate,
    amount: payment.amount,
    status: payment.status,
  }))).sort((left, right) => right.dueDate.localeCompare(left.dueDate) ||
    statusOrder[left.status] - statusOrder[right.status] || left.tenantLabel.localeCompare(right.tenantLabel));
}

export function buildRentCalendarEvents(
  schedules: RentSchedule[],
  fromDate: string,
  toDate: string,
): RentCalendarEvent[] {
  const events: RentCalendarEvent[] = [];

  for (const schedule of schedules) {
    if (!schedule.scheduleEnded && schedule.status !== "SETUP_FAILED" &&
      (!schedule.endDate || schedule.nextDueDate <= schedule.endDate) &&
      schedule.nextDueDate >= fromDate && schedule.nextDueDate <= toDate) {
      events.push({
        id: schedule.id + ":due:" + schedule.nextDueDate,
        scheduleId: schedule.id,
        tenantLabel: schedule.tenantLabel,
        date: schedule.nextDueDate,
        amount: schedule.amount,
        kind: "RENT_DUE",
        status: schedule.status,
      });
    }

    for (const payment of schedule.payments) {
      if (payment.dueDate >= fromDate && payment.dueDate <= toDate) {
        events.push({
          id: schedule.id + ":payment:" + payment.dueDate + ":" + payment.status,
          scheduleId: schedule.id,
          tenantLabel: schedule.tenantLabel,
          date: payment.dueDate,
          amount: payment.amount,
          kind: "PAYMENT",
          status: payment.status,
        });
      }
    }
  }

  return events.sort((left, right) =>
    left.date.localeCompare(right.date) || left.tenantLabel.localeCompare(right.tenantLabel) ||
    left.kind.localeCompare(right.kind),
  );
}

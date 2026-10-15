export type RentFrequency = "WEEKLY" | "MONTHLY";
export type RentScheduleStatus =
  | "PENDING_APPROVAL"
  | "SETUP_FAILED"
  | "ACTIVE"
  | "PAUSED"
  | "PAYMENT_FAILED";
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
  approvalUrl: string | null;
  payments: RentPayment[];
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
  status: Extract<RentScheduleStatus, "PENDING_APPROVAL" | "SETUP_FAILED" | "PAYMENT_FAILED">;
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
    schedule.status !== "SETUP_FAILED" && isDueWithinSevenDays(schedule.nextDueDate, today),
  );

  const dueSoonCents = dueSoon.reduce((total, schedule) => total + Math.round(schedule.amount * 100), 0);

  return {
    activeAutopayCount: schedules.filter(schedule => schedule.status === "ACTIVE").length,
    pendingApprovalCount: schedules.filter(schedule => schedule.status === "PENDING_APPROVAL").length,
    paymentIssueCount: schedules.filter(schedule => schedule.status === "PAYMENT_FAILED").length,
    dueWithinSevenDaysCount: dueSoon.length,
    dueWithinSevenDaysAmount: dueSoonCents / 100,
  };
}

export function buildRentAttentionItems(schedules: RentSchedule[]): RentAttentionItem[] {
  const attentionOrder: Record<RentAttentionItem["status"], number> = {
    PAYMENT_FAILED: 0,
    SETUP_FAILED: 1,
    PENDING_APPROVAL: 2,
  };
  const messages: Record<RentAttentionItem["status"], string> = {
    PAYMENT_FAILED: "A rent payment needs attention.",
    SETUP_FAILED: "Autopay setup needs attention.",
    PENDING_APPROVAL: "Tenant approval is still pending.",
  };

  return schedules
    .filter((schedule): schedule is RentSchedule & { status: RentAttentionItem["status"] } =>
      schedule.status === "PAYMENT_FAILED" || schedule.status === "SETUP_FAILED" || schedule.status === "PENDING_APPROVAL",
    )
    .map(schedule => ({
      scheduleId: schedule.id,
      tenantLabel: schedule.tenantLabel,
      status: schedule.status,
      message: messages[schedule.status],
    }))
    .sort((left, right) => attentionOrder[left.status] - attentionOrder[right.status] ||
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
    if (schedule.status !== "SETUP_FAILED" &&
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

\n
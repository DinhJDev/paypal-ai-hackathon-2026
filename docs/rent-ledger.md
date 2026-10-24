# Rent ledger

Owners can create weekly or monthly USD rent schedules without setting up PayPal. **Set up autopay**
shows a future start date for owner confirmation, then starts the separate tenant approval flow.
Earlier unpaid periods remain held in the ledger. Automatic charges remain sandbox-only and disabled by default.

## Balances and outside payments

Each due date has a rent obligation. Several owner-recorded cash, Venmo, bank-transfer, or other payments
can settle that obligation. PayPal attempts remain separate; only completed captures count as received.
Processing, failed, and refunded attempts do not count as received. A negative balance means an excess
payment requiring reconciliation; it is not automatically refunded or applied to another period.

Choose a suite in **Rent ledger**, select **Record outside payment**, enter the amount, payment date,
method, and optional reference, then review and confirm. These records are labeled **owner recorded**;
Suitefolk does not verify outside payments or integrate with Venmo. Future payment dates are rejected.

A partial outside payment holds automatic collection for that period. Full settlement skips that period's
charge. **Confirm review** acknowledges the displayed balance while keeping collection on hold; it never
charges the remainder or resumes that period's autopay. Future periods remain tracked independently.
Missed dates are held for review instead of automatically charged as catch-up payments.

If a PayPal attempt is processing or uncertain, wait for provider reconciliation. Recording an outside
payment cannot cancel an already submitted capture. A late settlement can reveal an excess payment.
The demo does not automate refunds, uncertain-payment resolution, or collection of a reduced balance.

## Corrections and confirmation

**Correct record** requires a reason and explicit confirmation. It reverses the recorded receipt while
preserving its original amount, date, method, reference, recorder, and timestamps. It does not refund
money. Record a replacement payment separately. Reversals keep automatic collection on hold.

Receipt confirmation uses a stable request UUID to make retries idempotent. A changed balance returns
409 so the owner can refresh and review it again. Review confirmations also check the period revision.
All period, receipt, and provider-settlement writers lock the schedule before updating ledger state.

The additive V4 migration preserves existing schedules, PayPal attempts, and payment identifiers, and
backfills their rent periods. Existing PayPal approval states remain compatible. No credentials or new
services are required. Lease uploads, AI extraction, billing adjustments, and agreement changes follow
in separate work.

## Manual sandbox check

1. Create a schedule and confirm that no PayPal approval is needed to use its ledger.
2. Review and confirm a $400 cash payment against $1,200 rent. Expect $800 remaining and an owner-review hold.
3. Confirm balance review. Expect the collection hold to remain.
4. Record the remaining $800 via Venmo. Expect Paid and no further charge for that period.
5. Reverse an incorrect outside-payment record with a reason. Expect restored balance and preserved history.

Keep `PAYPAL_AUTOPAY_ENABLED=false` unless deliberately testing sandbox collection. Automated tests
mock PayPal and use disposable PostgreSQL; they do not prove live sandbox delivery or money movement.

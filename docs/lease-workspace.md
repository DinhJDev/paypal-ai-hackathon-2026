# Lease workspace

Open **Leases** on the owner dashboard to create or reopen a saved workspace. Start with manual
entry; document uploads and AI extraction follow separately. No additional services or credentials
are required.

## Working drafts and confirmation

Drafts may omit information. Edits save automatically after a short pause; **Save draft** saves
immediately. Wait for **Saved** before leaving. Failed saves retain the values on screen, and the
browser warns before leaving with unsaved changes. Concurrent edits return a conflict rather than
overwriting another saved version. Copy any unsaved text you want to retain before explicitly
reloading saved terms.

Enter the tenant, suite, lease dates, rent amount, weekly/monthly frequency, and first due date.
Optional late-fee terms and furnished-item notes are recorded for reference; they do not create fees.
Initial rent must begin today or later, within the lease term. Business dates use America/Chicago.

**Review lease and schedule** saves current edits and shows the exact saved revision, terms, and up
to five rent dates. **Back to editing** returns to the draft. **Confirm and create schedule** records
the owner and confirmation time and creates one rent schedule in the same transaction. Repeating
that confirmation returns the same schedule. Stale revisions cannot be confirmed.

Confirmed terms are read-only in this slice. Confirmation does not change a signed agreement or
enable PayPal. Corrections to extraction, billing adjustments, and actual agreement amendments need
separate workflows; this screen does not represent any of those actions.

## Lease and payment states

Working/Confirmed describes review status. A confirmed lease is independently Upcoming, Active, or
Ended according to its dates. Rent scheduling and tenant-approved PayPal autopay remain separate.

The lease end date is inclusive: a rent date on that day is included at the full agreed amount.
There is no prorating or automatic renewal. No new rent periods, PayPal setup, or automatic captures
are allowed beyond the term. Existing unpaid periods, outside payments, review holds, and late
provider reconciliation remain in the ledger. An authorization/capture already submitted to PayPal
is not canceled by the date changing; a late settlement is still reconciled.

V5 adds an optional end date to rent schedules, and V6 adds saved lease workspaces. Existing schedules
retain their identifiers and unbounded behavior. The owner workspace creates bounded schedules only
after confirmation. The original direct schedule form remains available for standalone schedules.

## Manual check

1. Create a lease, enter only a tenant and suite, wait for Saved, then reload and reopen it.
2. Complete the rent and dates, review them, return to editing, change a value, and review again.
3. Confirm once and verify the portfolio has one Scheduled rent plan with the reviewed amount.
4. Confirm PayPal remains unenrolled until separately requested in the portfolio.
5. Edit the same draft in two tabs; the stale tab must retain its text and report a conflict.

Automated tests use disposable PostgreSQL and mocked PayPal. They do not verify live PayPal delivery
or money movement. Keep the existing sandbox automatic-charge switch off during ordinary testing.

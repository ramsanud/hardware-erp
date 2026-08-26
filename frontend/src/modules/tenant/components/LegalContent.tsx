import type { ReactNode } from 'react';

/**
 * Terms & Conditions and Privacy Policy copy.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * LEGAL REVIEW REQUIRED
 *
 * This text was drafted to describe what this application actually does. It
 * has NOT been reviewed by a lawyer and must be before the product takes real
 * customers. In particular a qualified adviser must settle: governing law and
 * jurisdiction, limitation of liability, data-retention periods, whether
 * India's DPDP Act or the GDPR apply to your deployment, GST and tax
 * disclaimers, and the lawful basis relied on for each processing purpose.
 *
 * Sections below carrying an inline "LEGAL REVIEW REQUIRED" badge are the
 * ones where the wording is placeholder and the substance is genuinely
 * jurisdiction-dependent. Deliberately NOT claimed anywhere in this text:
 * compliance with any named statute.
 * ─────────────────────────────────────────────────────────────────────────
 *
 * Versions here must stay in step with LegalDocumentVersions.java, which the
 * server validates submitted versions against.
 */

export const TERMS_VERSION = '1.0';
export const PRIVACY_VERSION = '1.0';
export const LEGAL_LAST_UPDATED = '26 August 2026';

function Section({ n, title, children, review }: {
  n: number; title: string; children: ReactNode; review?: boolean;
}) {
  return (
    <section className="space-y-2">
      <h3 className="flex flex-wrap items-center gap-2 text-sm font-semibold">
        <span className="tabular text-muted-foreground">{n}.</span>
        {title}
        {review ? (
          <span className="rounded-sm border border-warning/40 bg-warning/10 px-1.5 py-0.5
                           text-[10px] font-medium uppercase tracking-wide text-warning">
            Legal review required
          </span>
        ) : null}
      </h3>
      <div className="space-y-3 text-sm text-muted-foreground">{children}</div>
    </section>
  );
}

export function TermsContent() {
  return (
    <>
      <Section n={1} title="Introduction">
        <p>
          Hardware ERP is software for running a hardware shop: products and stock,
          customers and suppliers, quotations, GST tax invoices, payments, projects and
          labour. These terms govern your use of it. By creating an account you agree to
          them.
        </p>
      </Section>

      <Section n={2} title="Definitions">
        <p>
          <strong>Shop</strong> means the business account created at registration.
          <strong> Owner</strong> means the person who registered it.
          <strong> User</strong> means anyone signing in under that shop, including staff
          accounts the Owner creates. <strong>Your Data</strong> means the records you
          enter — products, customers, suppliers, invoices, payments and the rest.
        </p>
      </Section>

      <Section n={3} title="Account registration">
        <p>
          Registering creates one shop and one Owner login. You must give accurate
          details. There is no public self-registration for staff: the Owner creates
          every other account, and is responsible for who they give access to.
        </p>
      </Section>

      <Section n={4} title="Eligibility">
        <p>
          You must be legally capable of entering into a contract and authorised to act
          for the business you are registering.
        </p>
      </Section>

      <Section n={5} title="Account security">
        <p>
          Keep your password private. We will never ask you for it. Passwords are stored
          only as one-way hashes and cannot be read back by anyone, including us. Tell us
          promptly if you believe an account has been compromised; you can end all active
          sessions yourself from your profile.
        </p>
      </Section>

      <Section n={6} title="Authorised use">
        <p>
          Use the service to run your own shop. You may create staff accounts and set
          their permissions. Each user is accountable for actions taken under their login.
        </p>
      </Section>

      <Section n={7} title="Prohibited use">
        <p>
          Do not attempt to access another shop's data, probe or disrupt the service,
          upload malicious files, or use the service to store unlawful content. We may
          suspend an account that does.
        </p>
      </Section>

      <Section n={8} title="The software">
        <p>
          We may change, add or remove features. Where a change materially reduces
          function you rely on, we will give reasonable notice.
        </p>
      </Section>

      <Section n={9} title="Intellectual property">
        <p>
          The software, its interface and its documentation remain ours. Your Data
          remains yours; nothing here transfers ownership of it to us.
        </p>
      </Section>

      <Section n={10} title="Your data and the people in it">
        <p>
          You will enter information about other people — customers, suppliers, workers.
          You decide what to collect and why. You are responsible for having a proper
          basis to hold it and for how it is used. We process it to provide the service
          to you and for no other purpose.
        </p>
      </Section>

      <Section n={11} title="Data accuracy">
        <p>
          The figures the software produces are only as good as what is entered. Prices,
          tax rates, HSN codes, stock counts and customer details are yours to keep
          correct.
        </p>
      </Section>

      <Section n={12} title="Financial and tax disclaimer" review>
        <p>
          The service helps you produce invoices and reports. It is not accounting, tax
          or legal advice. You remain responsible for your filings and for the accuracy
          of every document you issue.
        </p>
      </Section>

      <Section n={13} title="Invoices and tax information" review>
        <p>
          The software formats GST tax invoices from the details you configure and enter.
          Whether a given invoice meets the requirements applicable to your business is
          yours to verify. Issued invoices are never silently altered: they are amended
          before payment or cancelled, and both leave a record.
        </p>
      </Section>

      <Section n={14} title="Third-party services">
        <p>
          Some optional features rely on outside providers — for example an email server
          you configure to send invoices. Those providers have their own terms. We are
          not responsible for their availability.
        </p>
      </Section>

      <Section n={15} title="Availability">
        <p>
          We aim to keep the service running but do not guarantee uninterrupted access.
          Maintenance, provider outages and events outside our control can interrupt it.
        </p>
      </Section>

      <Section n={16} title="Security">
        <p>
          Each shop's records are separated by a tenant identifier enforced on the server
          rather than hidden in the browser. Bank account numbers are encrypted at rest.
          Sign-ins and changes to business records are logged. No system is perfectly
          secure, and we do not claim otherwise.
        </p>
      </Section>

      <Section n={17} title="Your responsibilities">
        <p>
          Keep credentials safe, keep your own backups of anything you cannot afford to
          lose, and make sure the people you give access to understand these terms.
        </p>
      </Section>

      <Section n={18} title="Plans and payment">
        <p>
          The plan chosen at registration turns features on and off.{' '}
          <strong className="text-foreground">No payment is collected and no card is required.</strong>{' '}
          If paid plans are introduced, the terms will be published and your agreement
          sought before anything is charged.
        </p>
      </Section>

      <Section n={19} title="Suspension">
        <p>
          We may suspend an account that breaches these terms or puts the service or
          other shops at risk. Where practical we will tell you why and what to fix.
        </p>
      </Section>

      <Section n={20} title="Termination">
        <p>
          You may stop using the service at any time. On termination your access ends;
          retention of remaining records is covered in the next section.
        </p>
      </Section>

      <Section n={21} title="Data retention" review>
        <p>
          Financial records — invoices, payments and the movements behind them — are kept
          as a permanent history rather than deleted, because the documents they support
          must remain reconcilable. The retention period applicable to your business is
          set by law and must be confirmed for your jurisdiction.
        </p>
      </Section>

      <Section n={22} title="Data export">
        <p>
          You can retrieve your records from within the application. If a full export is
          needed on closing an account, contact the platform administrator.
        </p>
      </Section>

      <Section n={23} title="Limitation of liability" review>
        <p>
          Placeholder pending legal review. Any cap on liability, and the categories of
          loss excluded, must be drafted for the governing jurisdiction and must not
          purport to exclude liability that cannot lawfully be excluded.
        </p>
      </Section>

      <Section n={24} title="Disclaimer of warranties" review>
        <p>
          Placeholder pending legal review. The service is provided as it stands; the
          precise warranty position and any statutory rights that survive it are
          jurisdiction-dependent.
        </p>
      </Section>

      <Section n={25} title="Changes to these terms">
        <p>
          If these terms change materially we will tell you before the change takes
          effect and ask you to accept the new version. Your acceptance is recorded
          against the version number shown at the top of this document, so an earlier
          agreement is never treated as agreement to a later one.
        </p>
      </Section>

      <Section n={26} title="Governing law" review>
        <p>
          Placeholder pending legal review. Governing law and the forum for disputes must
          be set to match where the business operates.
        </p>
      </Section>

      <Section n={27} title="Contact">
        <p>
          Questions about these terms should go to the shop owner or the administrator who
          operates this deployment.
        </p>
      </Section>
    </>
  );
}

export function PrivacyContent() {
  return (
    <>
      <Section n={1} title="Introduction">
        <p>
          This policy explains what personal information Hardware ERP handles and why. It
          covers both your own account details and the information you enter about other
          people while running your shop.
        </p>
      </Section>

      <Section n={2} title="Account information">
        <p>
          Your name, mobile number and email address, and a one-way hash of your password.
          For staff accounts, the same details as entered by the Owner.
        </p>
      </Section>

      <Section n={3} title="Information you enter about others">
        <p>
          Customer and supplier names, contact details, addresses and GST numbers; worker
          names and pay rates. You choose what to enter.{' '}
          <strong className="text-foreground">
            You are the one deciding to collect it, and you are responsible for having a
            proper basis to do so.
          </strong>{' '}
          We hold it to provide the service to you.
        </p>
      </Section>

      <Section n={4} title="Business transaction data">
        <p>
          Quotations, invoices, payments, stock movements, expenses, projects and
          attendance — the records the software exists to keep.
        </p>
      </Section>

      <Section n={5} title="Technical information">
        <p>
          Ordinary server logs generated when the application is used, and the timestamps
          on records you create.
        </p>
      </Section>

      <Section n={6} title="What we do not collect">
        <p>
          No payment card details — none are required. No advertising or behavioural
          profiles. No location data. No access to your camera, microphone or files
          except when you explicitly choose a file to upload.
        </p>
      </Section>

      <Section n={7} title="How information is used">
        <p>
          To sign you in, produce your documents, and send the emails you ask for such as
          a password reset or an invoice to your customer. Your business data is never
          sold and never used for advertising.
        </p>
      </Section>

      <Section n={8} title="Cookies and similar technologies">
        <p>
          This application sets{' '}
          <strong className="text-foreground">one cookie</strong>: an HttpOnly session
          cookie holding your sign-in token, which exists solely to keep you signed in.
          There are no analytics, advertising or tracking cookies and no third-party
          scripts. Your browser also stores small preferences locally, such as your chosen
          theme and whether the sidebar is collapsed. Because nothing here tracks you,
          there is no tracking to consent to.
        </p>
      </Section>

      <Section n={9} title="Data storage">
        <p>
          Records are held in a PostgreSQL database. Every shop's rows carry a tenant
          identifier and every query is filtered by it on the server, so one shop cannot
          read another's data.
        </p>
      </Section>

      <Section n={10} title="Security measures">
        <p>
          Passwords are hashed and never recoverable. Bank account numbers are encrypted
          at rest. Sign-in attempts are rate-limited and repeated failures lock an account
          temporarily. Session tokens are short-lived and rotate.
        </p>
      </Section>

      <Section n={11} title="Activity and audit records">
        <p>
          Sign-ins, permission changes and edits to business records are logged with the
          user and time, so you can see who changed what in your own shop. These logs
          exist for your audit purposes.
        </p>
      </Section>

      <Section n={12} title="Consent records">
        <p>
          When you accept these documents we record which version you accepted and when.
          We deliberately do not record your IP address or device for this purpose.
        </p>
      </Section>

      <Section n={13} title="Sharing">
        <p>
          Only where you direct it — such as emailing an invoice to your customer — or
          where the law requires it. Where an email provider is configured, only what is
          needed to deliver that message is passed to it.
        </p>
      </Section>

      <Section n={14} title="Retention" review>
        <p>
          Account details are kept while the account exists. Financial records are
          retained as history. Applicable statutory retention periods must be confirmed
          for your jurisdiction.
        </p>
      </Section>

      <Section n={15} title="Your rights" review>
        <p>
          You can view and correct your own profile at any time. The rights available to
          you and to the people whose data you enter — access, correction, erasure,
          portability, objection — depend on the law that applies to your deployment and
          must be confirmed.
        </p>
      </Section>

      <Section n={16} title="Deletion and account closure">
        <p>
          To close an account or request deletion, contact the shop Owner or the platform
          administrator. Records that must be retained for financial or legal reasons are
          kept even after closure.
        </p>
      </Section>

      <Section n={17} title="International transfers" review>
        <p>
          Where data is stored depends on how this deployment is hosted. If it is hosted
          outside your country, the safeguards for that transfer must be confirmed.
        </p>
      </Section>

      <Section n={18} title="Children">
        <p>
          The service is for businesses and is not directed at children.
        </p>
      </Section>

      <Section n={19} title="Changes to this policy">
        <p>
          Material changes will be published with a new version number and brought to your
          attention before they take effect.
        </p>
      </Section>

      <Section n={20} title="Contact">
        <p>
          Privacy questions should go to the shop Owner or the administrator who operates
          this deployment.
        </p>
      </Section>
    </>
  );
}

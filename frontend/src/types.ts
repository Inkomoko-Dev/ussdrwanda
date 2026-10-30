export type Privilege =
  | 'DASHBOARD_VIEW' | 'AUDIT_VIEW'
  | 'CUSTOMER_VIEW' | 'CUSTOMER_ONBOARD' | 'CUSTOMER_PIN_RESET' | 'CUSTOMER_PIN_UNLOCK' | 'CUSTOMER_DISABLE'
  | 'REPAYMENT_VIEW' | 'RECON_PROPOSE' | 'RECON_APPROVE'
  | 'FAQ_VIEW' | 'FAQ_MANAGE' | 'MESSAGE_VIEW' | 'MESSAGE_MANAGE'
  | 'USER_VIEW' | 'USER_MANAGE' | 'ROLE_VIEW' | 'ROLE_MANAGE'
  | 'COUNTRY_VIEW' | 'COUNTRY_MANAGE' | 'LANGUAGE_MANAGE' | 'POLICY_MANAGE';

/** ISO 639 language code (e.g. "rw", "en", "sw"); the list comes from /api/admin/languages. */
export type Language = string;
export type Charset = 'GSM7' | 'UCS2';
export interface LanguageView {
  code: Language; nativeName: string; englishName: string; charset: Charset; builtIn: boolean; active: boolean;
  translated: number; total: number; usedBy: string[]; createdBy?: string; createdAt?: string;
}

export interface Page<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface PasswordPolicy {
  minLength: number; requireUpper: boolean; requireLower: boolean; requireDigit: boolean; requireSpecial: boolean;
  expiryDays: number; historyCount: number; maxFailedAttempts: number; lockoutMinutes: number; sessionMinutes: number;
  updatedAt?: string; updatedBy?: string;
}

export interface UserView {
  id: number; username: string; fullName: string; email?: string; phone?: string; roleId: number; roleName: string;
  countryCode?: string; status: 'ACTIVE' | 'DISABLED'; locked: boolean; lockedUntil?: string; mustChangePassword: boolean;
  lastLoginAt?: string; createdAt: string; createdBy?: string; systemRole?: boolean;
}

export interface Me {
  user: UserView; privileges: Privilege[]; mustChangePassword: boolean; passwordExpired: boolean;
  ussdServiceCode: string; country?: { code: string; name: string; currency: string }; countrySetupRequired: boolean;
  passwordPolicy: PasswordPolicy;
}

export interface LoginResponse { token: string; mustChangePassword: boolean; passwordExpired: boolean }

export interface Country {
  code: string; name: string; dialCode: string; nationalNumberLength: number; currency: string; timezone: string;
  languages: Language[]; mobileNetworks: string[]; helpline?: string; ussdServiceCode?: string;
  minRepayment: number; maxRepayment: number;
  active: boolean;
}

export interface RoleView { id: number; name: string; description?: string; systemRole: boolean; privileges: Privilege[]; userCount: number }
export interface PrivilegeView { code: Privilege; category: string; description: string }

export type PinStatus = 'NOT_SET' | 'ACTIVE' | 'LOCKED' | 'DISABLED';
export interface Customer {
  msisdn: string; clientId?: number; clientName?: string; status: PinStatus; registrationChannel?: string;
  onboardedBy?: string; onboardedAt?: string; lastLoginAt?: string; lockedAt?: string; createdAt?: string;
  language?: Language; email?: string; termsAccepted?: boolean;
}
export interface Loan {
  loanId: number; clientId?: number; accountNo: string; productName?: string; status: string;
  principalOutstanding?: number; totalOutstanding?: number; totalOverdue?: number; nextDueDate?: string; nextInstallment?: number;
}
export interface CustomerDetails {
  customer: Customer; coreClientId?: number; coreName?: string; corePhone?: string; coreEmail?: string;
  coreActive?: boolean; inCoreBanking: boolean;
}
export interface CustomerLookup {
  msisdn: string; foundInCoreBanking: boolean; client?: { clientId: number; displayName: string; active: boolean };
  loans: Loan[]; registration?: Customer;
}

export interface Repayment {
  id: number; reference: string; msisdn: string; loanId: number; loanAccountNo?: string; amount: number; currency: string;
  telecom?: string; status: string; gatewayRef?: string; telecomRef?: string; fineractTxnId?: string; postingAttempts: number;
  failureReason?: string; balanceAfter?: number; createdAt: string; updatedAt?: string; paidAt?: string; postedAt?: string;
}
export interface ReconAction {
  id: number; transactionReference: string; action: string; status: string; maker: string; makerComment?: string;
  checker?: string; checkerComment?: string; fineractTxnId?: string; result?: string; createdAt: string; decidedAt?: string;
}

export interface ReportSummary {
  sessions: number; authenticatedSessions: number; completedSessions: number; abandonedSessions: number;
  consentDeclined: number; notRegistered: number; completionRatePct: number; abandonmentRatePct: number;
  requests: number; avgResponseMs: number; loginFailures: number; pinLocks: number;
  repaymentCountByStatus: Record<string, number>; repaymentAmountByStatus: Record<string, number>;
  pendingReconciliationActions: number;
}

export interface FaqItem { id: number; language: Language; title: string; body: string; displayOrder: number; active: boolean }
export interface AuditLog { id: number; actor: string; channel: string; action: string; resource?: string; success: boolean; detail?: string; ipAddress?: string; ipCountry?: string; loggedAt: string }

// ── USSD texts editor ──
export interface MessageText {
  defaultText?: string | null; override?: string | null; inherited?: string | null; effective?: string | null;
  translated: boolean; length: number; maxLength: number; updatedBy?: string | null; updatedAt?: string | null;
}
export interface MessageEntry {
  key: string; group: string; sms: boolean; placeholders: string[];
  texts: Partial<Record<Language, MessageText>>;
}
export interface MessageCatalog { country: string; languages: Language[]; sampleValues: Record<string, string>; messages: MessageEntry[] }
export interface MessageCheck { valid: boolean; errors: string[]; preview: string; length: number; maxLength: number }
export interface MessageHistory {
  language: Language; country: string; action: 'UPDATE' | 'REVERT'; oldText?: string | null; newText?: string | null;
  changedBy: string; changedAt: string;
}

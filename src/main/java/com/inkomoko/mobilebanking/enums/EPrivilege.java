package com.inkomoko.mobilebanking.enums;

/**
 * One privilege per back-office action. Roles are built from these in the portal
 * (Administration → Roles); endpoints check them with @PreAuthorize("hasAuthority('...')").
 * Adding a constant here makes it appear in the role editor automatically.
 */
public enum EPrivilege {
    DASHBOARD_VIEW("Dashboard", "View dashboard and reports"),
    AUDIT_VIEW("Dashboard", "View the audit log"),
    SESSION_VIEW("Dashboard", "View USSD sessions and visits per phone number (billing)"),

    CUSTOMER_VIEW("Customers", "View USSD customers"),
    CUSTOMER_ONBOARD("Customers", "Onboard customers to USSD"),
    CUSTOMER_PIN_RESET("Customers", "Reset a customer's PIN"),
    CUSTOMER_PIN_UNLOCK("Customers", "Unlock a customer's PIN"),
    CUSTOMER_DISABLE("Customers", "Disable / enable a customer's USSD access"),
    REPAYMENT_VIEW("Repayments", "View repayments"),
    RECON_PROPOSE("Repayments", "Propose a reconciliation action (maker)"),
    RECON_APPROVE("Repayments", "Approve or reject reconciliation actions (checker)"),
    REPAYMENT_MARK_PAID("Repayments", "Mark a pending repayment as paid without a telecom debit (pilot)"),

    FAQ_VIEW("Help content", "View help / FAQ content"),
    FAQ_MANAGE("Help content", "Create and edit help / FAQ content"),
    MESSAGE_VIEW("Help content", "View USSD and SMS texts"),
    MESSAGE_MANAGE("Help content", "Edit USSD and SMS texts (per language and country)"),

    USER_VIEW("Administration", "View portal users"),
    USER_MANAGE("Administration", "Create and edit users, reset passwords"),
    ROLE_VIEW("Administration", "View roles and their privileges"),
    ROLE_MANAGE("Administration", "Create and edit roles, assign privileges"),

    COUNTRY_VIEW("Settings", "View country settings"),
    COUNTRY_MANAGE("Settings", "Edit country settings"),
    LANGUAGE_MANAGE("Settings", "Add and edit USSD languages"),
    POLICY_MANAGE("Settings", "View and edit the password policy");

    private final String category;
    private final String description;

    EPrivilege(String category, String description) {
        this.category = category;
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }
}

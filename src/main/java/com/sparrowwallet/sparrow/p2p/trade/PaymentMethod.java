package com.sparrowwallet.sparrow.p2p.trade;

/**
 * Payment methods accepted for P2P trades
 */
public enum PaymentMethod {
    CASH_IN_PERSON("Cash in Person", "Meet face-to-face and exchange cash for BTC"),
    BANK_TRANSFER("Bank Transfer", "Direct bank transfer (SEPA, ACH, wire, etc.)"),
    CASH_DEPOSIT("Cash Deposit", "Deposit cash at bank or ATM"),
    PAYPAL("PayPal", "PayPal payment"),
    REVOLUT("Revolut", "Revolut payment"),
    WISE("Wise (TransferWise)", "Wise transfer"),
    ZELLE("Zelle", "Zelle payment (US)"),
    VENMO("Venmo", "Venmo payment (US)"),
    STRIKE("Strike", "Strike Lightning payment"),
    BIZUM("Bizum", "Bizum payment (Spain)"),
    CASH_BY_MAIL("Cash by Mail", "Physical cash sent by mail"),
    MONEY_ORDER("Money Order", "Postal or bank money order"),
    GIFT_CARD("Gift Card", "Amazon, Steam, or other gift cards"),
    MOBILE_MONEY("Mobile Money", "M-Pesa, bKash, or other mobile money"),
    CRYPTOCURRENCY("Cryptocurrency", "Payment in other cryptocurrency"),
    OTHER("Other", "Other payment method (specify in terms)");

    private final String displayName;
    private final String description;

    PaymentMethod(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this payment method requires in-person meeting
     */
    public boolean requiresInPerson() {
        return this == CASH_IN_PERSON;
    }

    /**
     * Check if this payment method is reversible (chargeback risk)
     */
    public boolean isReversible() {
        return this == PAYPAL || this == VENMO || this == GIFT_CARD;
    }

    /**
     * Check if this payment method is instant
     */
    public boolean isInstant() {
        return this == CASH_IN_PERSON ||
               this == REVOLUT ||
               this == STRIKE ||
               this == BIZUM ||
               this == ZELLE;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

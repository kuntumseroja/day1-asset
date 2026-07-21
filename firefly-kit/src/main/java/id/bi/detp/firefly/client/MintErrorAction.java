package id.bi.detp.firefly.client;

public enum MintErrorAction {
    TRANSIENT_RETRY,
    DETERMINISTIC_REJECT,
    DUPLICATE_ACCEPTED
}

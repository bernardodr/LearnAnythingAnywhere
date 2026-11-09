package com.laa.llm.provider.fake;

/** How a fake provider misbehaves. Set per provider through {@link FakeBehaviour}. */
public enum FailureMode {
    NONE,
    /** Hangs (well past any sensible timeout) so the gateway's own timeout fires. */
    TIMEOUT,
    /** HTTP 429. */
    RATE_LIMIT,
    /** HTTP 503. */
    SERVER_ERROR,
    /** HTTP 400-style provider error. */
    PROVIDER_ERROR,
    /** First structured reply fails schema validation; the corrective retry succeeds. */
    INVALID_OUTPUT_ONCE,
    /** Every structured reply fails schema validation. */
    INVALID_OUTPUT
}

package com.contentpipeline.common.model;

/**
 * Placeholder identity for the deferred-auth seam.
 *
 * Controllers read the {@code X-Dev-User-Id} header and fall back to {@link #ID}
 * when it is absent. When real auth lands, the resolved value comes from the JWT
 * subject instead — no controller signature changes required.
 */
public final class DevUser {

    public static final String ID = "dev-user-001";

    public static final String HEADER = "X-Dev-User-Id";

    private DevUser() {}
}

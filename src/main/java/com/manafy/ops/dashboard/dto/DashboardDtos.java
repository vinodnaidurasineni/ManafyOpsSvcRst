package com.manafy.ops.dashboard.dto;

/** Ops dashboard DTOs. */
public final class DashboardDtos {

    private DashboardDtos() {}

    /**
     * High-level Ops operational summary for the dashboard home.
     *
     * {@code totalCustomers} is sourced from Community (the system of record for
     * resident/customer identity) via a service-to-service call. When Community is
     * not reachable/configured it is null and {@code customerCountAvailable} is
     * false, so the client can render a graceful "unavailable" state rather than a
     * misleading zero. All other counts are Ops-owned.
     */
    public record DashboardSummary(
            Long totalCustomers,
            boolean customerCountAvailable,
            long activeHelpers,
            long totalHelpers,
            long activeEmployees,
            long openComplaints,
            long openRequests) {}
}

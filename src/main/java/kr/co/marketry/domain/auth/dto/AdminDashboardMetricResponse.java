package kr.co.marketry.domain.auth.dto;

public record AdminDashboardMetricResponse(
        long classificationEligibleMembers,
        long classifiedMembers,
        long revisitEligibleMembers,
        long returningMembers,
        java.util.List<AdminPageMetricResponse> pages,
        long mobileCaptures,
        long averageMobileCaptureMs) {}

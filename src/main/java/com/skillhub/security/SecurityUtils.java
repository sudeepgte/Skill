package com.skillhub.security;

public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Sanitizes a redirect URL to prevent Open Redirect vulnerabilities.
     * Only allows safe relative local paths starting with a single forward slash.
     */
    public static String sanitizeRedirect(String targetUrl, String defaultFallback) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            return defaultFallback;
        }
        String clean = targetUrl.trim();
        // Reject absolute URLs, scheme indicators, protocol-relative URLs, or backslashes
        if (clean.contains("://") || clean.startsWith("//") || clean.startsWith("\\")) {
            return defaultFallback;
        }
        // Must start with single slash
        if (!clean.startsWith("/")) {
            return defaultFallback;
        }
        // Reject CRLF header injection attempts
        if (clean.contains("\r") || clean.contains("\n")) {
            return defaultFallback;
        }
        return clean;
    }
}

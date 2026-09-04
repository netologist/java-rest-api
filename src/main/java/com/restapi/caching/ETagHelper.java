package com.restapi.caching;

import jakarta.servlet.http.HttpServletRequest;

/**
 * RFC 9110 / RFC 9111 HTTP Caching & Conditional Request Helper.
 */
public final class ETagHelper {

    private ETagHelper() {}

    /**
     * Checks If-None-Match header against the resource's current ETag.
     *
     * @return true if the client's cached version is still fresh (should respond with 304 Not Modified)
     */
    public static boolean isNotModified(HttpServletRequest request, String currentEtag) {
        String ifNoneMatch = request.getHeader("If-None-Match");
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }

        if (ifNoneMatch.equals("*")) {
            return true;
        }

        for (String candidate : ifNoneMatch.split(",")) {
            if (candidate.trim().equals(currentEtag) || candidate.trim().equals("W/" + currentEtag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks If-Match header for optimistic concurrency control (PUT / PATCH).
     *
     * @return true if the client's precondition is satisfied; false if mismatched (should respond with 412 Precondition Failed)
     */
    public static boolean isMatch(HttpServletRequest request, String currentEtag) {
        String ifMatch = request.getHeader("If-Match");
        if (ifMatch == null || ifMatch.isBlank()) {
            return true; // No precondition specified
        }

        if (ifMatch.equals("*")) {
            return true;
        }

        for (String candidate : ifMatch.split(",")) {
            if (candidate.trim().equals(currentEtag)) {
                return true;
            }
        }
        return false;
    }
}

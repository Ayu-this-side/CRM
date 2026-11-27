package com.crm.dto;

/**
 * Display/contact details for a polymorphic (type, id) link, resolved through
 * the owning service so the current user's visibility is enforced.
 */
public record LinkedRecord(String displayName, String email, String path) {
}

package com.grindandtrain.common.error;

/**
 * One error the services can report: a stable code for clients, a fixed title and a category.
 * <p>
 * Implemented by enums only: {@link PlatformErrorCode} for errors every service shares, and one enum per business area
 * in grind-core. Codes are part of the public contract (the problem {@code type} is {@code urn:grind:problem:<code>}):
 * add new ones, but never rename or reuse an existing one. Titles are fixed strings, so no user data can reach a
 * response or a log line through an error.
 *
 * @author Dheeraj_Edupuganti
 */
public interface ErrorCode {

    ErrorCategory category();

    /** Stable, lower snake case, unique across every enum. */
    String code();

    /** Short and fixed. Never built from request data. */
    String title();
}

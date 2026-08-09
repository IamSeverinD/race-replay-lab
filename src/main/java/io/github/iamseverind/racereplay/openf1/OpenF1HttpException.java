package io.github.iamseverind.racereplay.openf1;

import java.io.IOException;

/**
 * Reports an unsuccessful HTTP response from OpenF1.
 */
public final class OpenF1HttpException extends IOException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    /**
     * Creates an OpenF1 HTTP exception.
     *
     * @param statusCode HTTP response status
     * @param message human-readable error description
     */
    public OpenF1HttpException(
            final int statusCode,
            final String message) {

        super(message);

        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException(
                    "Invalid HTTP status code: "
                    + statusCode);
        }

        this.statusCode = statusCode;
    }

    /**
     * Returns the HTTP status code.
     *
     * @return HTTP response status
     */
    public int statusCode() {
        return statusCode;
    }
}

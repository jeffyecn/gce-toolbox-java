package com.langcode.gcetoolbox;

public class GceToolBoxError extends Exception {

    public GceToolBoxError(String message) {
        super(message);
    }

    public GceToolBoxError(String message, Throwable cause) {
        super(message, cause);
    }
}

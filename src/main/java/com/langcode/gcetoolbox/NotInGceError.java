package com.langcode.gcetoolbox;

public class NotInGceError extends Exception {

    public NotInGceError() {
        super("Not running in Google compute engine");
    }
}

package com.langcode.gcetoolbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class TestApp {
    private final static Logger LOG;

    static {
        LOG = LoggerFactory.getLogger(TestApp.class);
    }

    public static void main(String[] args) throws Exception {
        LOG.info("testing");

        EnvDetector detector = EnvDetector.getInstance();
        detector.detect();
        detector.enableAutoRefresh(1, TimeUnit.MINUTES);
    }
}

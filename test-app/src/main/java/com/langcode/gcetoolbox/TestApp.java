package com.langcode.gcetoolbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestApp {
    private final static Logger LOG;

    static {
        LOG = LoggerFactory.getLogger(TestApp.class);
    }

    public static void main(String[] args) throws Exception {
        LOG.info("testing");

        EnvDetector detector = EnvDetector.getInstance();
        detector.detect();

        LOG.info("project {}", detector.getProjectId());
        LOG.info("instance name {}", detector.getName());

        if ( detector.runningInGCE() ) {
            LOG.info("Running in GCE");

            LOG.info("Zone {}", detector.getZone());
            LOG.info("Private IP: {}", detector.getPrivateIP());
        } else {
            LOG.warn("Not running in GCE");
        }
    }
}

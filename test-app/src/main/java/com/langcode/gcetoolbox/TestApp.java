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

        Group group = detector.getGroupOfInstance(new Instance("https://www.googleapis.com/compute/beta/projects/test-14378/zones/us-east1-b/instances/rtb-bidder-east-5l46"));

        if ( group == null ) {
            LOG.info("group not found");
        } else {
            LOG.info("group {}", group.getName());
        }
    }
}

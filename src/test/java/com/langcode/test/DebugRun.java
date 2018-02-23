package com.langcode.test;

import com.langcode.gcetoolbox.EnvDetector;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public class DebugRun {

    private org.slf4j.Logger LOG;

    @Before
    public void before() {
        LOG = LoggerFactory.getLogger("DebugRun");
    }

    @Test
    public void testOne() {
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

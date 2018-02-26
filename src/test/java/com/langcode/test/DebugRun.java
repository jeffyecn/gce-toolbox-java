package com.langcode.test;

import com.langcode.gcetoolbox.EnvDetector;
import org.junit.Test;

public class DebugRun {

    @Test
    public void testOne() {

        EnvDetector detector = EnvDetector.getInstance();
        detector.detect();

        System.out.println("project " + detector.getProjectId());
        System.out.println("instance name " + detector.getName());

        if ( detector.runningInGCE() ) {
            System.out.println("Running in GCE");

            System.out.println("Zone " + detector.getZone());
            System.out.println("Private IP: " + detector.getPrivateIP());
        } else {
            System.out.println("Not running in GCE");
        }
    }
}

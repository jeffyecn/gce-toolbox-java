package com.langcode.test;

import com.langcode.gcetoolbox.EnvDetector;
import com.langcode.gcetoolbox.Group;
import com.langcode.gcetoolbox.Zone;
import org.junit.Test;

import java.util.List;
import java.util.Map;

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

        List<Zone> zones = detector.getAllZones();

        for(Zone zone : zones ) {
            System.out.println("zone " + zone.getName() + " region " + zone.getRegion());

            Map<String, Group> groups = detector.getGroupsOfZone(zone.getName());

            groups.forEach((k,v)->{
                System.out.println("Group "+k);
            });
        }
    }
}

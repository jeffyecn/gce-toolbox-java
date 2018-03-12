package com.langcode.test;

import com.langcode.gcetoolbox.EnvDetector;
import com.langcode.gcetoolbox.Group;
import com.langcode.gcetoolbox.Instance;
import com.langcode.gcetoolbox.InstanceDetail;
import org.junit.Test;

public class DecreaseGroupSize {

    @Test
    public void decreaseGroupSize() {
        String groupName = System.getProperty("group");

        if ( groupName == null || groupName.isEmpty() ) {
            System.err.println("Missing group property");
            return;
        }

        EnvDetector detector = EnvDetector.getInstance();
        detector.detect();

        Group group = detector.getAllGroups().get(groupName);
        if ( group == null ) {
            System.err.println("Group " + groupName + " can not be found");
            return;
        }

        Instance removing = null;
        InstanceDetail removingDetail = null;
        for(Instance instance: detector.getInstanceOfGroup(group)) {
            InstanceDetail detail = detector.getInstanceDetail(instance);
            if ( removing == null ) {
                removing = instance;
                removingDetail = detail;
            } else {
                if ( detail.getCreateTimestamp() < removingDetail.getCreateTimestamp() ) {
                    removing = instance;
                    removingDetail = detail;
                }
            }
        }

        if ( removing == null ) {
            System.err.println("No instance found from group");
            return;
        }

        System.out.println("removing instance " + removing.getName());

        detector.removeInstanceFromGroup(removing.getName(), group);
    }
}

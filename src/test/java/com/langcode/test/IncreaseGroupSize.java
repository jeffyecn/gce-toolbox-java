package com.langcode.test;

import com.langcode.gcetoolbox.EnvDetector;
import com.langcode.gcetoolbox.Group;
import org.junit.Test;

public class IncreaseGroupSize {

    @Test
    public void increaseGroupSize() {
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

        int currentSize = detector.getSizeOfGroup(group);

        System.out.println("Increase size from " + currentSize + " to " + (currentSize+1));

        detector.resizeGroup(group, currentSize + 1);
    }
}

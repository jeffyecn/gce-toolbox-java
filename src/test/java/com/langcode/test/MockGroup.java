package com.langcode.test;

import com.langcode.gcetoolbox.EnvDetector;
import org.junit.Test;

public class MockGroup {

    @Test
    public void testGroupFunction() {
        EnvDetector detector = EnvDetector.getInstance();
        detector.detect();

        detector.getAllGroups().forEach((name, group)->{
            System.out.println("Group " + name + ", size: " + detector.getSizeOfGroup(group));
        });
    }
}

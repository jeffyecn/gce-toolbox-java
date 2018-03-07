package com.langcode.test;

import com.langcode.gcetoolbox.EnvDetector;
import org.junit.Test;

public class MockGroup {

    @Test
    public void testGroupFunction() {
        EnvDetector detector = EnvDetector.getInstance();
        detector.detect();
    }
}

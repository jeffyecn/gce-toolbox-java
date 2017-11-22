package ca.jeffye.gcetoolbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestApp {
    private final static Logger LOG;

    static {
        LOG = LoggerFactory.getLogger(TestApp.class);
    }

    public static void main(String[] args) {
        LOG.info("testing");
    }
}

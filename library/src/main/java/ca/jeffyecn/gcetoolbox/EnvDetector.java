package ca.jeffyecn.gcetoolbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnvDetector {

    private final static Logger LOG = LoggerFactory.getLogger(EnvDetector.class);

    private final static EnvDetector instance = new EnvDetector();

    public static EnvDetector getInstance() {
        return instance;
    }

    private EnvDetector() {

    }

    public boolean runningInGCE() {
        return false;
    }
}

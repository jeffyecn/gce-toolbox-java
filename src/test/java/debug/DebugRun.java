package debug;

import com.langcode.gcetoolbox.EnvDetector;
import com.langcode.gcetoolbox.GceToolBoxError;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DebugRun {

    private final static Logger LOG = LoggerFactory.getLogger(DebugRun.class);

    @Test
    public void testOne() throws IOException, GceToolBoxError {

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

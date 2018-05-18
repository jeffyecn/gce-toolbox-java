package debug;

import com.langcode.gcetoolbox.EnvDetector;
import com.langcode.gcetoolbox.GceToolBoxError;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MockGroup {

    private final static Logger LOG = LoggerFactory.getLogger(MockGroup.class);

    @Test
    public void testGroupFunction() throws IOException, GceToolBoxError {
        EnvDetector detector = EnvDetector.getInstance();
        detector.detect();

        detector.getAllGroups().forEach((name, group)->{
            try {
                LOG.info("Group {} size: {}", name, detector.getSizeOfGroup(group));
            } catch (IOException ex) {
                LOG.error("Get group size with error", ex);
            }
        });
    }
}

package debug;

import com.langcode.gcetoolbox.EnvDetector;
import com.langcode.gcetoolbox.GceToolBoxError;
import com.langcode.gcetoolbox.Group;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class MockGroup {

    private final static Logger LOG = LoggerFactory.getLogger(MockGroup.class);

    @Test
    public void testGroupFunction() throws IOException, GceToolBoxError {
        EnvDetector detector = EnvDetector.getInstance();
        detector.detect();

        try {
            Map<String, Group> groups = detector.getAllGroups();

            groups.forEach((name, group) -> {
                try {
                    LOG.info("Group {} size: {}", name, detector.getSizeOfGroup(group));
                } catch (IOException ex) {
                    LOG.error("Get group size with error", ex);
                }
            });
        } catch(Exception ex) {
            LOG.error("get all group with error", ex);
            throw ex;
        }
    }
}

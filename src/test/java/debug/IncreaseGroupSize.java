package debug;

import com.langcode.gcetoolbox.EnvDetector;
import com.langcode.gcetoolbox.GceToolBoxError;
import com.langcode.gcetoolbox.Group;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class IncreaseGroupSize {

    private final static Logger LOG = LoggerFactory.getLogger(IncreaseGroupSize.class);

    @Test
    public void increaseGroupSize() throws IOException, GceToolBoxError {
        String groupName = System.getProperty("group");

        if (groupName == null || groupName.isEmpty()) {
            LOG.error("Missing group property");
            return;
        }

        EnvDetector detector = EnvDetector.getInstance();
        detector.detect();

        Group group = detector.getAllGroups().get(groupName);
        if (group == null) {
            LOG.error("Group {} can not be found", groupName);
            return;
        }

        int currentSize = detector.getSizeOfGroup(group);

        LOG.info("Increase size from {} to {}", currentSize, currentSize + 1);

        detector.resizeGroup(group, currentSize + 1);
    }
}

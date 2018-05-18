package debug;

import com.langcode.gcetoolbox.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DecreaseGroupSize {

    private final static Logger LOG = LoggerFactory.getLogger(DecreaseGroupSize.class);

    @Test
    public void decreaseGroupSize() throws IOException, GceToolBoxError {
        String groupName = System.getProperty("group");

        if ( groupName == null || groupName.isEmpty() ) {
            LOG.error("Missing group property");
            return;
        }

        EnvDetector detector = EnvDetector.getInstance();
        detector.detect();

        Group group = detector.getAllGroups().get(groupName);
        if ( group == null ) {
            LOG.error("Group {} can not be found", groupName);
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
            LOG.error("No instance found from group");
            return;
        }

        LOG.info("removing instance {}", removing.getName());

        detector.removeInstanceFromGroup(removing.getName(), group);
    }
}

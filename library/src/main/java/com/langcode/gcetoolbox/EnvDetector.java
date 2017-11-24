package com.langcode.gcetoolbox;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.*;
import com.google.cloud.ServiceOptions;
import com.google.common.base.Splitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class EnvDetector {

    private final static Logger LOG = LoggerFactory.getLogger(EnvDetector.class);

    private final static EnvDetector instance = new EnvDetector();

    public static EnvDetector getInstance() {
        return instance;
    }

    private Timer timer = null;

    private boolean inGCE = true;
    private String projectId = "";
    private String name = "";
    private String zone = "";
    private String privateIP = "";
    private Instance vmInstance = null;

    Compute compute = null;

    private EnvDetector() {

    }

    public void detect() {
        if ( ! hasDetect() ) {
            // the following data won't change
            projectId = ServiceOptions.getDefaultProjectId();
            name = fetchMeta("instance/name", "");
            if (name.isEmpty()) {
                inGCE = false;
                name = getServerHostname();
            }
            if (inGCE) {
                String fullZoneStr = fetchMeta("instance/zone", "");
                List<String> parts = Splitter.on('/').splitToList(fullZoneStr);
                zone = parts.get(parts.size()-1);
                privateIP = fetchMeta("instance/network-interfaces/0/ip", "");
                vmInstance = new Instance(projectId, zone, name);
            }

            compute = initGceApi();
        }
    }

    public boolean hasDetect() {
        return ! projectId.isEmpty();
    }

    public void enableAutoRefresh(long interval, TimeUnit timeUnit) {
        if ( ! hasDetect() ) {
            detect();
        }

        if ( ! inGCE ) {
            LOG.warn("not in GCE, can not enable auto refresh");
            return;
        }

        if ( timer != null ) {
            LOG.warn("auto refresh already enabled");
            return;
        }

        timer = new Timer("env refresh time", true);
        long period = timeUnit.toMillis(interval);
        LOG.info("period {}", period);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    detect();
                } catch (Exception ex) {
                    LOG.error("Refresh env failed.");
                }
            }
        }, period, period);
    }

    public boolean runningInGCE() {
        return inGCE;
    }

    String getServerHostname() {
        String hostname = "";
        try {
            Process p = Runtime.getRuntime().exec("hostname -s");
            p.waitFor();
            BufferedReader reader
                    = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            if ( line != null ) {
                hostname = line.trim();
            }
        } catch (IOException ex) {
            LOG.error("fail to run hostname command", ex);
        } catch (InterruptedException ex) {
            LOG.error("detect hostname aborted");
        }
        return hostname;
    }

    public ArrayList<String> fetchMeta(String metaPath) {
        try {
            URL url = new URL("http://metadata.google.internal/computeMetadata/v1/" + metaPath);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.addRequestProperty("Metadata-Flavor", "Google");
            conn.setConnectTimeout(500);
            int code = conn.getResponseCode();
            if ( code == 200 ) {
                ArrayList<String> result = new ArrayList<>();
                BufferedReader reader
                        = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ( (line = reader.readLine()) != null ) {
                    if ( ! line.trim().isEmpty() ) {
                        result.add(line);
                    }
                }
                return result;
            } else {
                LOG.error("fetch meta response code {}", code);
            }
        } catch (MalformedURLException ex) {
            LOG.error("invalid meta path {}", metaPath);
        } catch (UnknownHostException ex) {
            LOG.warn("metadata.google.internal unknown, not in GCE");
        } catch (IOException ex) {
            LOG.error("other exception on get meta {}", ex.getClass().getCanonicalName());
        }

        return null;
    }

    public String fetchMeta(String metaPath, String defaultValue) {
        ArrayList<String> lines = fetchMeta(metaPath);
        if ( lines == null || lines.isEmpty() ) {
            return defaultValue;
        }
        return lines.get(0);
    }

    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getZone() {
        return zone;
    }

    public String getPrivateIP() {
        return privateIP;
    }

    private Compute initGceApi() {
        try {
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            GoogleCredential credential = GoogleCredential.getApplicationDefault();

            if (credential.createScopedRequired()) {
                ArrayList<String> scopes = new ArrayList<>();
                scopes.add(ComputeScopes.COMPUTE_READONLY);
                credential = credential.createScoped(scopes);
            }

            return new Compute.Builder(httpTransport, JacksonFactory.getDefaultInstance(), credential)
                    .setApplicationName("gcetoolbox/1.0")
                    .build();
        } catch (Exception ex) {
            LOG.error("init GCE api failed", ex);
        }

        return null;
    }

    public ArrayList<Instance> getInstanceOfGroup(Group group) {
        ArrayList<Instance> result = new ArrayList<>();

        try {
            Compute.InstanceGroups.ListInstances request = compute.instanceGroups().listInstances(
                    group.project,
                    group.zone,
                    group.name,
                    new InstanceGroupsListInstancesRequest()
            );

            InstanceGroupsListInstances response;

            do {
                response = request.execute();

                if (response.getItems() == null) {
                    continue;
                }

                for (InstanceWithNamedPorts instance : response.getItems()) {
                    String vmURL = instance.getInstance();
                    result.add(new Instance(vmURL));
                }

                request.setPageToken(response.getNextPageToken());
            } while (response.getNextPageToken() != null);

        } catch (IOException ex) {
            LOG.warn("get instance of group failed");
        }

        return result;
    }

    public InstanceDetail getInstanceDetail(Instance instance) {
        try {
            Compute.Instances.Get req = compute.instances().get(instance.project, instance.zone, instance.name);
            com.google.api.services.compute.model.Instance instanceData = req.execute();
            return new InstanceDetail(instanceData);
        } catch (IOException ex) {
            LOG.warn("failed to get instance detail");
        }
        return null;
    }

    public Group getGroupOfInstance(Instance instance) {
        try {
            Compute.InstanceGroups.List req = compute.instanceGroups().list(instance.project, instance.zone);

            ArrayList<Group> groups = new ArrayList<>();
            InstanceGroupList response;
            do {
                response = req.execute();
                if ( response.getItems() == null ) {
                    continue;
                }

                for(InstanceGroup group : response.getItems()) {
                    groups.add(new Group(instance.project, instance.zone, group.getName()));
                }

                req.setPageToken(response.getNextPageToken());
            } while(response.getNextPageToken() != null);

            for(Group group : groups) {
                for (Instance groupInstance : getInstanceOfGroup(group) ) {
                    if ( groupInstance.equals(instance) ) {
                        return group;
                    }
                }
            }
        } catch (Exception ex) {
            LOG.warn("fet group of instance failed");
        }

        return null;
    }
}

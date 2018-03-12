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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.logging.Logger;

public class EnvDetector {

    private final static Logger LOG = Logger.getLogger(EnvDetector.class.getName());

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
    private InstanceDetail vmDetail = null;
    private Group group = null;
    private volatile ArrayList<Instance> peers = null;

    private final ConcurrentHashMap<String, IntConsumer> numPeerListeners = new ConcurrentHashMap<>();

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

            if ( inGCE ) {
                vmDetail = getInstanceDetail(vmInstance);
                group = getGroupOfInstance(vmInstance);
            }
        }

        if ( group != null ) {
            peers = getInstanceOfGroup(group);
        } else {
            if ( vmInstance != null ) {
                peers = new ArrayList<>();
                peers.add(vmInstance);
            }
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
            LOG.warning("not in GCE, can not enable auto refresh");
            return;
        }

        if ( group == null ) {
            LOG.warning("not in instance group, can not enable auto refresh");
            return;
        }

        if ( timer != null ) {
            LOG.warning("auto refresh already enabled");
            return;
        }

        timer = new Timer("env refresh time", true);
        long period = timeUnit.toMillis(interval);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    int prevNum = getNumberOfPeers();
                    detect();
                    int newNum = getNumberOfPeers();
                    if ( newNum != prevNum ) {
                        numPeerListeners.forEach((k,v)->{
                            v.accept(newNum);
                        });
                    }
                } catch (Exception ex) {
                    LOG.severe("Refresh env failed.");
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
            LOG.severe("fail to run hostname command");
        } catch (InterruptedException ex) {
            LOG.severe("detect hostname aborted");
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
                LOG.severe("fetch meta response code " + code);
            }
        } catch (MalformedURLException ex) {
            LOG.severe("invalid meta path " + metaPath);
        } catch (UnknownHostException ex) {
            LOG.warning("metadata.google.internal unknown, not in GCE");
        } catch (IOException ex) {
            LOG.severe("other exception on get meta " + ex.getClass().getCanonicalName());
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

    public String getPublicIP() {
        return vmDetail == null ? "" : vmDetail.publicIP;
    }

    public String getUsedByGroup() {
        return group == null ? "" : group.name;
    }

    public int getNumberOfPeers() {
        return peers == null ? 1 : peers.size();
    }

    public String onNumberOfPeersChanged(IntConsumer callback) {
        String uuid = UUID.randomUUID().toString();
        numPeerListeners.put(uuid, callback);
        int num = getNumberOfPeers();
        if ( num > 1 ) {
            callback.accept(num);
        }
        return uuid;
    }

    public void removeNumberOfPeersChangeListener(String listenerId) {
        numPeerListeners.remove(listenerId);
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
            LOG.severe("init GCE api failed " + ex.getMessage());
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
                    System.out.println(vmURL);
                    result.add(new Instance(vmURL));
                }

                request.setPageToken(response.getNextPageToken());
            } while (response.getNextPageToken() != null);

        } catch (IOException ex) {
            LOG.severe("get instance of group failed");
        }

        return result;
    }

    public InstanceDetail getInstanceDetail(Instance instance) {
        try {
            Compute.Instances.Get req = compute.instances().get(instance.project, instance.zone, instance.name);
            com.google.api.services.compute.model.Instance instanceData = req.execute();
            return new InstanceDetail(instanceData);
        } catch (IOException ex) {
            LOG.warning("failed to get instance detail");
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
            LOG.warning("fet group of instance failed");
        }

        return null;
    }

    public List<Zone> getAllZones() {
        ArrayList<Zone> result = new ArrayList<>();

        try {
            Compute.Zones.List req = compute.zones().list(projectId);
            ZoneList response;
            do {
                response = req.execute();
                if ( response.getItems() == null ) {
                    continue;
                }

                for(com.google.api.services.compute.model.Zone zone : response.getItems()) {
                    result.add(new Zone(zone.getName(), zone.getRegion()));
                }

                req.setPageToken(response.getNextPageToken());
            } while(response.getNextPageToken() != null);
        } catch (Exception ex) {
            LOG.warning("get all zones failed");
        }

        return result;
    }

    public Map<String, Group> getGroupsOfZone(String zone) {
        HashMap<String, Group> result = new HashMap<>();

        try {
            Compute.InstanceGroups.List req = compute.instanceGroups().list(projectId, zone);
            InstanceGroupList response;
            do {
                response = req.execute();
                if ( response.getItems() == null ) {
                    continue;
                }

                for(InstanceGroup group : response.getItems()) {
                    Group groupObj = new Group(projectId, zone, group.getName());
                    result.put(groupObj.getName(), groupObj);
                }

                req.setPageToken(response.getNextPageToken());
            } while(response.getNextPageToken() != null);

        } catch (Exception ex) {
            LOG.warning("get all group failed");
        }

        return result;
    }

    public Map<String, Group> getAllGroups() {
        HashMap<String, Group> result = new HashMap<>();

        for(Zone zone : getAllZones()) {
            getGroupsOfZone(zone.getName()).forEach((name, group)->result.put(name, group));
        }

        return result;
    }

    public int getSizeOfGroup(Group group) {
        try {
            InstanceGroup groupInfo = compute.instanceGroups().get(group.project, group.zone, group.name).execute();
            return groupInfo.getSize();
        } catch (Exception ex) {
            LOG.warning("get group info failed");
        }

        return 0;
    }

    public boolean resizeGroup(Group group, int newSize) {
        try {
            compute.instanceGroupManagers().resize(group.project, group.zone, group.name, newSize).execute();
            return true;
        } catch (Exception ex) {
            LOG.warning("resize group failed");
        }
        return false;
    }

    public boolean removeInstanceFromGroup(String instanceName, Group group) {
        ArrayList<String> deleting = new ArrayList<>();
        deleting.add(Instance.makeVmURL(group.project, group.zone, instanceName));
        InstanceGroupManagersDeleteInstancesRequest request = new InstanceGroupManagersDeleteInstancesRequest();
        request.setInstances(deleting);
        try {
            compute.instanceGroupManagers().deleteInstances(group.project, group.zone, group.name, request).execute();
            return true;
        } catch(Exception ex) {
            LOG.warning("remove instance from group failed");
        }
        return false;
    }
}

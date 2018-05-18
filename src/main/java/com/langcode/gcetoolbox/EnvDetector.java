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

import javax.annotation.Nullable;
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
            LOG.warn("not in GCE, can not enable auto refresh");
            return;
        }

        if ( group == null ) {
            LOG.warn("not in instance group, can not enable auto refresh");
            return;
        }

        if ( timer != null ) {
            LOG.warn("auto refresh already enabled");
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
            LOG.error("fail to run hostname command");
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
                LOG.error("fetch meta response code " + code);
            }
        } catch (MalformedURLException ex) {
            LOG.error("invalid meta path {}", metaPath);
        } catch (UnknownHostException ex) {
            LOG.warn("metadata.google.internal unknown, not in GCE");
        } catch (IOException ex) {
            LOG.error("other exception on get meta", ex);
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

    public String fetchMetaAttribute(String attrName, String defaultValue) {
        return fetchMeta("instance/attributes/" + attrName, defaultValue);
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
            LOG.error("init GCE api failed {}", ex.getMessage());
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
            LOG.error("get instance of group failed");
        }

        return result;
    }

    public InstanceDetail getInstanceDetail(Instance instance) {
        try {
            Compute.Instances.Get req = compute.instances().get(instance.project, instance.zone, instance.name);
            com.google.api.services.compute.model.Instance instanceData = req.execute();
            if ( instanceData != null ) {
                return new InstanceDetail(instanceData);
            }
        } catch (IOException ex) {
            LOG.error("failed to get instance detail");
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
            LOG.error("get group of instance failed");
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
            LOG.warn("get all zones failed");
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
            LOG.warn("get all group failed");
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
            LOG.warn("get group info failed");
        }

        return 0;
    }

    public boolean resizeGroup(Group group, int newSize) {
        try {
            compute.instanceGroupManagers().resize(group.project, group.zone, group.name, newSize).execute();
            return true;
        } catch (Exception ex) {
            LOG.warn("resize group failed");
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
            LOG.warn("remove instance from group failed");
        }
        return false;
    }

    public boolean stopSelf() {
        if ( vmInstance == null ) {
            LOG.warn("Stop self is not possible while not running in GCE");
            return false;
        }
        return stopInstance(vmInstance);
    }

    public boolean stopInstance(Instance instance) {

        try {
            Compute.Instances.Stop request = compute.instances().stop(instance.project, instance.zone, instance.name);
            request.execute();

            return true;
        } catch (IOException ex) {
            LOG.error("Stop instance failed with error ", ex);
        }

        return false;
    }

    public boolean startInstance(Instance instance) {

        try {
            Compute.Instances.Start request = compute.instances().start(instance.project, instance.zone, instance.name);
            request.execute();

            return true;
        } catch (IOException ex) {
            LOG.error("Start instance failed with error ", ex);
        }

        return false;
    }

    InstanceTemplate getInstanceTemplate(String project, String template) {
        try {
            Compute.InstanceTemplates.Get req = compute.instanceTemplates().get(project, template);
            return req.execute();
        } catch (IOException ex) {
            LOG.error("Failed to get template ", ex);
        }

        return null;
    }

    public boolean createInstance(Instance instance, String template, @Nullable Map<String, String> extraMeta) {
        InstanceTemplate instanceTemplate = getInstanceTemplate(instance.project, template);
        if ( instanceTemplate == null ) {
            LOG.warn("Can not create instance because template not found");
            return false;
        }

        InstanceProperties conf = instanceTemplate.getProperties();

        com.google.api.services.compute.model.Instance data = new com.google.api.services.compute.model.Instance();
        data.setName(instance.name);
        if ( conf.getDescription() != null ) {
            data.setDescription(conf.getDescription());
        }

        data.setMachineType("zones/" + instance.zone + "/machineTypes/" + conf.getMachineType());
        data.setNetworkInterfaces(conf.getNetworkInterfaces());

        List<AttachedDisk> disks = conf.getDisks();
        disks.forEach(disk->{
            String diskType = disk.getInitializeParams().getDiskType();
            if ( diskType != null ) {
                disk.getInitializeParams().setDiskType("zones/" + instance.zone + "/diskTypes/" + diskType);
            }
        });
        data.setDisks(conf.getDisks());

        data.setServiceAccounts(conf.getServiceAccounts());
        data.setTags(conf.getTags());
        data.setLabels(conf.getLabels());
        data.setCanIpForward(conf.getCanIpForward());
        data.setScheduling(conf.getScheduling());

        Metadata meta = conf.getMetadata();
        if ( extraMeta != null ) {
            List<Metadata.Items> items = meta.getItems();
            extraMeta.forEach((k,v)->{
                Metadata.Items item = new Metadata.Items();
                item.setKey(k);
                item.setValue(v);
                items.add(item);
            });
            meta.setItems(items);
        }
        data.setMetadata(meta);

        try {
            Compute.Instances.Insert insert = compute.instances().insert(instance.project, instance.zone, data);
            insert.execute();
            return true;
        } catch ( IOException ex ) {
            LOG.error("Can not create instance from template: {}", ex.getMessage());
        }
        return false;
    }
}

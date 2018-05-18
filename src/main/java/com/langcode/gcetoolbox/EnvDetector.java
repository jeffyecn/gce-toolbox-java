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

    public void detect() throws IOException, GceToolBoxError {
        if (!hasDetect()) {
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
                zone = parts.get(parts.size() - 1);
                privateIP = fetchMeta("instance/network-interfaces/0/ip", "");
                vmInstance = new Instance(projectId, zone, name);
            }

            compute = initGceApi();

            if (inGCE) {
                vmDetail = getInstanceDetail(vmInstance);
                group = getGroupOfInstance(vmInstance);
            }
        }

        if (group != null) {
            peers = getInstanceOfGroup(group);
        } else {
            if (vmInstance != null) {
                peers = new ArrayList<>();
                peers.add(vmInstance);
            }
        }
    }

    public boolean hasDetect() {
        return !projectId.isEmpty();
    }

    public void enableAutoRefresh(long interval, TimeUnit timeUnit) throws IOException, GceToolBoxError {
        if (!hasDetect()) {
            detect();
        }

        if (!inGCE) {
            LOG.warn("not in GCE, can not enable auto refresh");
            return;
        }

        if (group == null) {
            LOG.warn("not in instance group, can not enable auto refresh");
            return;
        }

        if (timer != null) {
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
                    if (newNum != prevNum) {
                        numPeerListeners.forEach((k, v) -> {
                            try {
                                v.accept(newNum);
                            } catch (Exception ex) {
                                LOG.error("refresh listener got exception", ex);
                            }
                        });
                    }
                } catch (Exception ex) {
                    LOG.error("Refresh env failed.", ex);
                }
            }
        }, period, period);
    }

    public boolean runningInGCE() {
        return inGCE;
    }

    String getServerHostname() throws IOException {
        String hostname = "";
        try {
            Process p = Runtime.getRuntime().exec("hostname -s");
            p.waitFor();
            BufferedReader reader
                    = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            if (line != null) {
                hostname = line.trim();
            }
        } catch (InterruptedException ex) {
            LOG.warn("detect hostname aborted");
        }
        return hostname;
    }

    public ArrayList<String> fetchMeta(String metaPath) throws IOException, GceToolBoxError {
        try {
            URL url = new URL("http://metadata.google.internal/computeMetadata/v1/" + metaPath);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.addRequestProperty("Metadata-Flavor", "Google");
            conn.setConnectTimeout(500);
            int code = conn.getResponseCode();
            if (code == 200) {
                ArrayList<String> result = new ArrayList<>();
                BufferedReader reader
                        = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        result.add(line);
                    }
                }
                return result;
            } else {
                throw new GceToolBoxError("fetch meta response code " + code);
            }
        } catch (MalformedURLException ex) {
            throw new GceToolBoxError("invalid meta path", ex);
        } catch (UnknownHostException ex) {
            throw new GceToolBoxError("Not in GCE", ex);
        }
    }

    public String fetchMeta(String metaPath, String defaultValue) throws IOException, GceToolBoxError {
        ArrayList<String> lines = fetchMeta(metaPath);
        if (lines == null || lines.isEmpty()) {
            return defaultValue;
        }
        return lines.get(0);
    }

    public String fetchMetaAttribute(String attrName, String defaultValue) throws IOException, GceToolBoxError {
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
        if (num > 1) {
            callback.accept(num);
        }
        return uuid;
    }

    public void removeNumberOfPeersChangeListener(String listenerId) {
        numPeerListeners.remove(listenerId);
    }

    private Compute initGceApi() throws GceToolBoxError {
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
            throw new GceToolBoxError("init GCE api failed", ex);
        }
    }

    public ArrayList<Instance> getInstanceOfGroup(Group group) throws IOException {
        ArrayList<Instance> result = new ArrayList<>();

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

        return result;
    }

    public InstanceDetail getInstanceDetail(Instance instance) throws IOException, GceToolBoxError {
        Compute.Instances.Get req = compute.instances().get(instance.project, instance.zone, instance.name);
        com.google.api.services.compute.model.Instance instanceData = req.execute();
        if (instanceData != null) {
            return new InstanceDetail(instanceData);
        }

        throw new GceToolBoxError("Instance not exists");
    }


    @Nullable
    public Group getGroupOfInstance(Instance instance) throws IOException {
        Compute.InstanceGroups.List req = compute.instanceGroups().list(instance.project, instance.zone);

        ArrayList<Group> groups = new ArrayList<>();
        InstanceGroupList response;
        do {
            response = req.execute();
            if (response.getItems() == null) {
                continue;
            }

            for (InstanceGroup group : response.getItems()) {
                groups.add(new Group(instance.project, instance.zone, group.getName()));
            }

            req.setPageToken(response.getNextPageToken());
        } while (response.getNextPageToken() != null);

        for (Group group : groups) {
            for (Instance groupInstance : getInstanceOfGroup(group)) {
                if (groupInstance.equals(instance)) {
                    return group;
                }
            }
        }

        return null;
    }

    public List<Zone> getAllZones() throws IOException {
        ArrayList<Zone> result = new ArrayList<>();

        Compute.Zones.List req = compute.zones().list(projectId);
        ZoneList response;
        do {
            response = req.execute();
            if (response.getItems() == null) {
                continue;
            }

            for (com.google.api.services.compute.model.Zone zone : response.getItems()) {
                result.add(new Zone(zone.getName(), zone.getRegion()));
            }

            req.setPageToken(response.getNextPageToken());
        } while (response.getNextPageToken() != null);

        return result;
    }

    public Map<String, Group> getGroupsOfZone(String zone) throws IOException {
        HashMap<String, Group> result = new HashMap<>();

        Compute.InstanceGroups.List req = compute.instanceGroups().list(projectId, zone);
        InstanceGroupList response;
        do {
            response = req.execute();
            if (response.getItems() == null) {
                continue;
            }

            for (InstanceGroup group : response.getItems()) {
                Group groupObj = new Group(projectId, zone, group.getName());
                result.put(groupObj.getName(), groupObj);
            }

            req.setPageToken(response.getNextPageToken());
        } while (response.getNextPageToken() != null);


        return result;
    }

    public Map<String, Group> getAllGroups() throws IOException {
        HashMap<String, Group> result = new HashMap<>();

        for (Zone zone : getAllZones()) {
            getGroupsOfZone(zone.getName()).forEach((name, group) -> result.put(name, group));
        }

        return result;
    }

    public int getSizeOfGroup(Group group) throws IOException {
        InstanceGroup groupInfo = compute.instanceGroups().get(group.project, group.zone, group.name).execute();
        return groupInfo.getSize();
    }

    public void resizeGroup(Group group, int newSize) throws IOException {
        compute.instanceGroupManagers().resize(group.project, group.zone, group.name, newSize).execute();
    }

    public void removeInstanceFromGroup(String instanceName, Group group) throws IOException {
        ArrayList<String> deleting = new ArrayList<>();
        deleting.add(Instance.makeVmURL(group.project, group.zone, instanceName));
        InstanceGroupManagersDeleteInstancesRequest request = new InstanceGroupManagersDeleteInstancesRequest();
        request.setInstances(deleting);
        compute.instanceGroupManagers().deleteInstances(group.project, group.zone, group.name, request).execute();

    }

    public void stopSelf() throws GceToolBoxError, IOException {
        if (vmInstance == null) {
            throw new GceToolBoxError("Stop self is not possible while not running in GCE");
        }
        stopInstance(vmInstance);
    }

    public void stopInstance(Instance instance) throws IOException {
        Compute.Instances.Stop request = compute.instances().stop(instance.project, instance.zone, instance.name);
        request.execute();
    }

    public void startInstance(Instance instance) throws IOException {
        Compute.Instances.Start request = compute.instances().start(instance.project, instance.zone, instance.name);
        request.execute();
    }

    @Nullable
    InstanceTemplate getInstanceTemplate(String project, String template) throws IOException {
        Compute.InstanceTemplates.Get req = compute.instanceTemplates().get(project, template);
        return req.execute();
    }

    public void createInstance(Instance instance, String template, @Nullable Map<String, String> extraMeta) throws IOException, GceToolBoxError {
        InstanceTemplate instanceTemplate = getInstanceTemplate(instance.project, template);
        if (instanceTemplate == null) {
            throw new GceToolBoxError("Can not create instance because template not found");
        }

        InstanceProperties conf = instanceTemplate.getProperties();

        com.google.api.services.compute.model.Instance data = new com.google.api.services.compute.model.Instance();
        data.setName(instance.name);
        if (conf.getDescription() != null) {
            data.setDescription(conf.getDescription());
        }

        data.setMachineType("zones/" + instance.zone + "/machineTypes/" + conf.getMachineType());
        data.setNetworkInterfaces(conf.getNetworkInterfaces());

        List<AttachedDisk> disks = conf.getDisks();
        disks.forEach(disk -> {
            String diskType = disk.getInitializeParams().getDiskType();
            if (diskType != null) {
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
        if (extraMeta != null) {
            List<Metadata.Items> items = meta.getItems();
            extraMeta.forEach((k, v) -> {
                Metadata.Items item = new Metadata.Items();
                item.setKey(k);
                item.setValue(v);
                items.add(item);
            });
            meta.setItems(items);
        }
        data.setMetadata(meta);

        Compute.Instances.Insert insert = compute.instances().insert(instance.project, instance.zone, data);
        insert.execute();
    }
}

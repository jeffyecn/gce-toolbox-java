package com.langcode.gcetoolbox;

import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.NetworkInterface;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

public class InstanceDetail {

    final long id;
    final String privateIP;
    final String publicIP;
    final long createTime;
    final String status;

    InstanceDetail(Instance data) {
        id = data.getId().longValue();
        createTime = parseTimestamp(data.getCreationTimestamp());
        NetworkInterface networkInterface = data.getNetworkInterfaces().get(0);
        privateIP = networkInterface.getNetworkIP();
        List<AccessConfig> accessConfigList = networkInterface.getAccessConfigs();
        if ( accessConfigList != null && ! accessConfigList.isEmpty() ) {
            publicIP = accessConfigList.get(0).getNatIP();
        } else {
            publicIP = "";
        }
        status = data.getStatus();

        data.getMetadata().forEach((k, v)->{
            System.out.println(k + ":" + v);
        });
    }

    long parseTimestamp(String ts) {
        // 2017-09-05T23:01:07.989-07:00
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        try {
            return format.parse(ts).getTime();
        } catch (ParseException ex) {
            return 0;
        }
    }

    public long getId() {
        return id;
    }

    public String getPrivateIP() {
        return privateIP;
    }

    public String getPublicIP() {
        return publicIP;
    }

    public boolean isRunning() {
        return status.equals("RUNNING");
    }

    public long getCreateTimestamp() {
        return createTime;
    }
}


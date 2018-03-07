package com.langcode.gcetoolbox;

import com.google.common.base.Splitter;

import java.util.List;

public class Zone {

    private final String name;
    private final String region;

    public Zone(String name, String regionURL) {
        this.name = name;

        List<String> parts = Splitter.on("/").splitToList(regionURL);

        int num = parts.size();

        region = parts.get(num - 1);
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }
}

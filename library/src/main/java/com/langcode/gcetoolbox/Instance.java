package com.langcode.gcetoolbox;

import com.google.common.base.Splitter;

import java.util.List;

public class Instance {

    final String project;
    final String zone;
    final String name;

    public Instance(String project, String zone, String name) {
        this.project = project;
        this.zone = zone;
        this.name = name;
    }

    public Instance(String vmURL) {
        List<String> parts = Splitter.on("/").splitToList(vmURL);

        int num = parts.size();

        project = parts.get(num-5);
        zone = parts.get(num-3);
        name = parts.get(num-1);
    }

    @Override
    public boolean equals(Object obj) {
        if ( obj instanceof Instance ) {
            Instance intObj = (Instance)obj;
            return intObj.project.equals(project) &&
                    intObj.zone.equals(zone) &&
                    intObj.name.equals(name);
        }
        return false;
    }

    public String getProject() {
        return project;
    }

    public String getZone() {
        return zone;
    }

    public String getName() {
        return name;
    }
}

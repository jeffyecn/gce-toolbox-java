package com.langcode.gcetoolbox;

public class Group {

    final String project;
    final String zone;
    final String name;

    public Group(String project, String zone, String name) {
        this.project = project;
        this.zone = zone;
        this.name = name;
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

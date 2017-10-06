package com.github.adiesner.jenkins.bitbucketprcoveragestatus;

public class PullRequest {
    private final String id;
    private final String name;

    public PullRequest(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

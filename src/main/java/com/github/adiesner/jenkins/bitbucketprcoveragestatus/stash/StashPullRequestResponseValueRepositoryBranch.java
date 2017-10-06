package com.github.adiesner.jenkins.bitbucketprcoveragestatus.stash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestResponseValueRepositoryBranch {
    private String Name;

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }
}
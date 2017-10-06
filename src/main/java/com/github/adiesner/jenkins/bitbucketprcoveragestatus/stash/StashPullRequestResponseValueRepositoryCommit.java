package com.github.adiesner.jenkins.bitbucketprcoveragestatus.stash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestResponseValueRepositoryCommit {
    private String latestChangeset;

    public String getHash() {
        return latestChangeset;
    }

    public void setHash(String hash) {
        this.latestChangeset = hash;
    }
}
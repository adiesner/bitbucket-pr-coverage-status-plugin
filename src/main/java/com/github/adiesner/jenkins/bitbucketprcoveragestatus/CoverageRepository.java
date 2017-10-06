package com.github.adiesner.jenkins.bitbucketprcoveragestatus;

import hudson.FilePath;

import java.io.IOException;

interface CoverageRepository {

    float get(FilePath workspace) throws IOException, InterruptedException;

}

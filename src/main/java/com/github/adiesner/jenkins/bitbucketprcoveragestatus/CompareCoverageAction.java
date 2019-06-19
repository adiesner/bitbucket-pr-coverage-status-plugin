/*

    Copyright 2015-2016 Artem Stasiuk

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/
package com.github.adiesner.jenkins.bitbucketprcoveragestatus;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.github.adiesner.jenkins.bitbucketprcoveragestatus.bitbucket.ApiClient;
import com.github.adiesner.jenkins.bitbucketprcoveragestatus.bitbucket.Pullrequest;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Build step to publish pull request coverage status message to GitHub pull request.
 * <p>
 * Workflow:
 * <ul>
 * <li>find coverage of current build and assume it as pull request coverage</li>
 * <li>find master coverage for repository URL could be taken by {@link MasterCoverageAction} or Sonar {@link Configuration}</li>
 * <li>Publish nice status message to GitHub PR page</li>
 * </ul>
 *
 * @see MasterCoverageAction
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class CompareCoverageAction extends Recorder implements SimpleBuildStep {

    public static final String BUILD_LOG_PREFIX = "[Bitbucket PR Status] ";

    private static final long serialVersionUID = 1L;
    private Map<String, String> scmVars;

    @Getter
    @Setter
    @DataBoundSetter
    private String bitbucketHost;
    @Getter
    @Setter
    @DataBoundSetter
    private String credentialsId;
    @Getter
    @Setter
    @DataBoundSetter
    private String projectCode;
    @Getter
    @Setter
    @DataBoundSetter
    private String repositoryName;
    @Getter
    @Setter
    @DataBoundSetter
    private String jenkinsUrl;
    @Getter
    @Setter
    @DataBoundSetter
    private boolean privateJenkins;
    @Getter
    @Setter
    @DataBoundSetter
    private String yellowThreshold;
    @Getter
    @Setter
    @DataBoundSetter
    private String greenThreshold;
    @Getter
    @Setter
    @DataBoundSetter
    private boolean useSonarForMasterCoverage;
    @Getter
    @Setter
    @DataBoundSetter
    private String sonarUrl;
    @Getter
    @Setter
    @DataBoundSetter
    private String sonarToken;
    @Getter
    @Setter
    @DataBoundSetter
    private String sonarLogin;
    @Getter
    @Setter
    @DataBoundSetter
    private String sonarPassword;
    @Getter
    @Setter
    @DataBoundSetter
    private boolean disableSimpleCov;
    @Getter
    @Setter
    @DataBoundSetter
    private boolean negativeCoverageIsRed;

    @DataBoundConstructor
    public CompareCoverageAction() {
    }

    private static StandardUsernamePasswordCredentials getCredentials(String host, String credentialsId) {
        Authentication defaultAuth = null;
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardUsernamePasswordCredentials.class,
                        Jenkins.getInstance(),
                        defaultAuth,
                        URIRequirementBuilder.fromUri(host).build()
                ),
                CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));
    }

    public String getUsername() {
        return this.getCredentials(getBitbucketHost(), getCredentialsId()).getUsername();
    }

    public String getPassword() {
        return this.getCredentials(getBitbucketHost(), getCredentialsId()).getPassword().getPlainText();
    }

    // TODO why is this needed for no public field ‘scmVars’ (or getter method) found in class ....
    public Map<String, String> getScmVars() {
        return scmVars;
    }

    @DataBoundSetter
    public void setScmVars(Map<String, String> scmVars) {
        this.scmVars = scmVars;
    }

    // todo show message that addition comment in progress as it could take a while
    @SuppressWarnings("NullableProblems")
    @Override
    public void perform(
            final Run build, final FilePath workspace, final Launcher launcher,
            final TaskListener listener) throws InterruptedException, IOException {
        final PrintStream buildLog = listener.getLogger();

        if (build.getResult() != Result.SUCCESS) {
            buildLog.println(BUILD_LOG_PREFIX + "skip, build is red");
            return;
        }
        buildLog.println(BUILD_LOG_PREFIX + "start");

        ServiceRegistry.setPullRequestRepository(
                new BitbucketPullRequestRepository(getBitbucketApiClient(bitbucketHost, credentialsId, projectCode, repositoryName))
        );

        final String targetBranch = PrIdAndUrlUtils.getTargetBranch(build.getEnvironment(listener));
        final int prId = PrIdAndUrlUtils.getPrId(scmVars, build, listener);
        final String gitMasterUrl = useSonarForMasterCoverage ? PrIdAndUrlUtils.getGitUrlForTargetBranch(build, listener) : PrIdAndUrlUtils.getGitUrlForTargetWithBranch(build, listener);

        buildLog.println(BUILD_LOG_PREFIX + "Getting reference coverage for " + gitMasterUrl);
        MasterCoverageRepository masterCoverageRepository = ServiceRegistry
                .getMasterCoverageRepository(buildLog, isUseSonarForMasterCoverage(), getSonarUrl(), getSonarLogin(), getSonarPassword(), getSonarToken());
        final float masterCoverage = masterCoverageRepository.get(gitMasterUrl);
        buildLog.println(BUILD_LOG_PREFIX + "Master coverage: " + masterCoverage);

        buildLog.println(BUILD_LOG_PREFIX + "Collecting coverage...");
        EnvVars envVars = new EnvVars();
        envVars = build.getEnvironment(listener);
        final float coverage = Float.parseFloat(envVars.get("lineCoverage")) / 100; // ServiceRegistry.getCoverageRepository(isDisableSimpleCov()).get(workspace);
        buildLog.println(BUILD_LOG_PREFIX + "Build coverage: " + coverage);

        buildLog.println(BUILD_LOG_PREFIX + "Target branch: " + targetBranch);
        final Message message = new Message(coverage, masterCoverage, targetBranch);
        buildLog.println(BUILD_LOG_PREFIX + message.forConsole());

        final String buildUrl = Utils.getBuildUrl(build, listener);

        String jenkinsUrl = getJenkinsUrl();
        if (StringUtils.isBlank(jenkinsUrl))
            jenkinsUrl = Utils.getJenkinsUrlFromBuildUrl(buildUrl);

        try {
            final String comment = message.forComment(
                    buildUrl,
                    jenkinsUrl,
                    NumberUtils.toInt(getYellowThreshold(), 70),
                    NumberUtils.toInt(getGreenThreshold(), 90),
                    isNegativeCoverageIsRed(),
                    isPrivateJenkins());
            buildLog.println(BUILD_LOG_PREFIX + "Pushing comment to bitbucket: " + comment);
            ServiceRegistry.getPullRequestRepository().comment(Integer.toString(prId), comment);
        } catch (Exception ex) {
            listener.error(ex.getMessage());
            PrintWriter pw = listener.error("Couldn't add comment to pull request #" + prId + "!");
            ex.printStackTrace(pw);
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Publish coverage to Bitbucket";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            save();
            return super.configure(req, json);
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context, @QueryParameter String source) {
            if (context == null || !context.hasPermission(Item.CONFIGURE)) {
                return new ListBoxModel();
            }
            return new StandardUsernameListBoxModel()
                    .includeEmptyValue()
                    .includeAs(context instanceof Queue.Task
                                    ? Tasks.getDefaultAuthenticationOf((Queue.Task) context)
                                    : ACL.SYSTEM, context, StandardUsernamePasswordCredentials.class,
                            URIRequirementBuilder.fromUri(source).build()
                    );
        }

        public FormValidation doTestConnection(
                @QueryParameter("bitbucketHost") final String bitbucketHost,
                @QueryParameter("credentialsId") final String credentialsId,
                @QueryParameter("projectCode") final String projectCode,
                @QueryParameter("repositoryName") final String repositoryName
        ) throws IOException, ServletException {

            ApiClient client = getBitbucketApiClient(bitbucketHost, credentialsId, projectCode, repositoryName);

            final List<Pullrequest> pullRequests = client.getPullRequests();

            StringBuilder projectList = new StringBuilder();
            for (Pullrequest p : pullRequests) {
                projectList.append(p.getTitle()).append(" ");
            }

            return FormValidation.ok("Success " + bitbucketHost + " - " + credentialsId + " : " + projectList.toString());
        }
    }

    private static ApiClient getBitbucketApiClient(final String bitbucketHost,
                                                   final String credentialsId,
                                                   final String projectCode,
                                                   final String repositoryName) {
        final StandardUsernamePasswordCredentials credentials = getCredentials(bitbucketHost, credentialsId);
        if (credentials == null) {
            throw new RuntimeException("No credentials found for ID: " + credentialsId);
        }

        return new ApiClient(
                credentials.getUsername(),
                credentials.getPassword().getPlainText(),
                projectCode,
                repositoryName,
                "jenkins",
                "Jenkins",
                null
        );
    }

}

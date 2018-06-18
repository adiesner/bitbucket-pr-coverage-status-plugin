package com.github.adiesner.jenkins.bitbucketprcoveragestatus.bitbucket;

import com.github.adiesner.jenkins.bitbucketprcoveragestatus.BitbucketPullRequestRepository;
import org.junit.Test;

import java.io.IOException;

public class ApiClientTest {

    @Test
    public void test() throws IOException {
        ApiClient apiClient = new ApiClient(
                "yuliia-panchenko@idexx.com",
                "qwerty123456",
                "YuliiaPanchenko",
                "book-album-service",
                "jenkins",
                "Jenkins",
                null
        );

        BitbucketPullRequestRepository bitbucketPullRequestRepository = new BitbucketPullRequestRepository(apiClient);
        bitbucketPullRequestRepository.comment("1", "This is test!!!");

//        List<Pullrequest.Comment> pullRequestComments = apiClient.findOwnPullRequestComments("9");
    }

}
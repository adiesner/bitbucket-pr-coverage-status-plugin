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
package com.github.terma.jenkins.githubprcoveragestatus;

import hudson.model.Build;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

public class MasterCoverageActionTest {

    private Build build = Mockito.mock(Build.class);

    @Test
    public void skipStepIfResultOfBuildIsNotSuccess() throws IOException, InterruptedException {
        getMasterCoverageAction().perform(build, null, null, null);
    }

    private MasterCoverageAction getMasterCoverageAction() {
        return new MasterCoverageAction(false, "", "", "", "", false);
    }
}

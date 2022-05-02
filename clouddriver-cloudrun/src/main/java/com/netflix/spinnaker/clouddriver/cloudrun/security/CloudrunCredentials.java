/*
 * Copyright 2022 OpsMx Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudrun.security;

import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.googlecommon.security.GoogleCommonCredentials;
import java.util.List;

public class CloudrunCredentials extends GoogleCommonCredentials {

  private final String project;

  public CloudrunCredentials(String project) {
    this.project = project;
  }

  public void getCloudrun(
      String applicationName, CloudrunJobExecutor jobExecutor, String jsonPath) {

    jobExecutor.runCommand(List.of("gcloud", "auth", "login", "--cred-file", jsonPath));
  }
}

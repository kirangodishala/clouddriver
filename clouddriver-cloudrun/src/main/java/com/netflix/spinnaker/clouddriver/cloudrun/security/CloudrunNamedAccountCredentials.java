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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.security.AbstractAccountCredentials;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CloudrunNamedAccountCredentials
    extends AbstractAccountCredentials<CloudrunCredentials> {
  public static final String CREDENTIALS_TYPE = "cloudrun";
  final String name;
  final String environment;
  final String accountType;
  final String project;
  final String cloudProvider;
  final String region;
  final List<String> requiredGroupMembership;
  final Permissions permissions;

  @JsonIgnore final String jsonPath;

  @JsonIgnore final String serviceAccountEmail;

  @JsonIgnore final String localRepositoryDirectory;

  final CloudrunCredentials credentials;

  final String applicationName;

  @Data
  public static class Builder {
    String name;
    String environment;
    String accountType;
    String project;
    String cloudProvider;
    String region;
    List<String> requiredGroupMembership;
    Permissions permissions = Permissions.EMPTY;
    CloudrunCredentials credentials;
    String jsonKey;
    String jsonPath;
    String serviceAccountEmail;
    String localRepositoryDirectory;
    String applicationName;
    boolean sshTrustUnknownHosts;

    /*
     * If true, the builder will overwrite region with a value from the platform.
     * */
    Boolean liveLookupsEnabled = true;

    Builder name(String name) {
      this.name = name;
      return this;
    }

    Builder environment(String environment) {
      this.environment = environment;
      return this;
    }

    Builder accountType(String accountType) {
      this.accountType = accountType;
      return this;
    }

    Builder project(String project) {
      this.project = project;
      return this;
    }

    Builder cloudProvider(String cloudProvider) {
      this.cloudProvider = CloudrunCloudProvider.ID;
      return this;
    }

    Builder serviceAccountEmail(String serviceAccountEmail) {
      this.serviceAccountEmail = serviceAccountEmail;
      return this;
    }

    Builder localRepositoryDirectory(String localRepositoryDirectory) {
      this.localRepositoryDirectory = localRepositoryDirectory;
      return this;
    }

    Builder region(String region) {
      this.region = region;
      this.liveLookupsEnabled = false;
      return this;
    }

    Builder requiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership;
      return this;
    }

    Builder permissions(Permissions permissions) {
      if (permissions.isRestricted()) {
        this.requiredGroupMembership = new ArrayList<>();
        this.permissions = permissions;
      }
      return this;
    }

    Builder jsonPath(String jsonPath) {
      this.jsonPath = jsonPath;
      return this;
    }

    Builder jsonKey(String jsonKey) {
      this.jsonKey = jsonKey;
      return this;
    }

    Builder applicationName(String applicationName) {
      this.applicationName = applicationName;
      return this;
    }

    Builder credentials(CloudrunCredentials credentials) {
      this.credentials = credentials;
      return this;
    }

    Builder sshTrustUnknownHosts(boolean sshTrustUnknownHosts) {
      this.sshTrustUnknownHosts = sshTrustUnknownHosts;
      return this;
    }

    public CloudrunNamedAccountCredentials build(CloudrunJobExecutor jobExecutor) {

      if (credentials != null) {
        credentials = credentials;
      } else if (jsonKey != null) {
        credentials = new CloudrunJsonCredentials(project, jsonKey);
      } else {
        credentials = new CloudrunCredentials(project);
      }

      credentials.getCloudrun(applicationName, jobExecutor, jsonPath);
      return new CloudrunNamedAccountCredentials(
          name,
          environment,
          accountType,
          project,
          CloudrunCloudProvider.ID,
          region,
          requiredGroupMembership,
          permissions,
          jsonPath,
          serviceAccountEmail,
          localRepositoryDirectory,
          credentials,
          applicationName);
    }
  }
}

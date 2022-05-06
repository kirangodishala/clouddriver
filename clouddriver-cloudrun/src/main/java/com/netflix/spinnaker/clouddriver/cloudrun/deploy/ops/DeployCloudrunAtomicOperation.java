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

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DeployCloudrunDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class DeployCloudrunAtomicOperation implements AtomicOperation<DeploymentResult> {

  private static final String BASE_PHASE = "DEPLOY";

  private static final Logger log = LoggerFactory.getLogger(DeployCloudrunAtomicOperation.class);

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Autowired Registry registry;

  @Autowired CloudrunJobExecutor jobExecutor;

  DeployCloudrunDescription description;

  public DeployCloudrunAtomicOperation(DeployCloudrunDescription description) {
    this.description = description;
  }

  public String deploy(String repositoryPath) {
    String project = description.getCredentials().getProject();
    String region = description.getCredentials().getRegion();
    String applicationDirectoryRoot = description.getApplicationDirectoryRoot();
    List<String> configFiles = description.getConfigFiles();
    List<String> writtenFullConfigFilePaths =
        writeConfigFiles(configFiles, repositoryPath, applicationDirectoryRoot);

    // runCommand expects a List<String> and will fail if some of the arguments are GStrings (as
    // that is not a subclass
    // of String). It is thus important to only add Strings to deployCommand.  For example, adding a
    // flag "--test=$testvalue"
    // below will cause deployments to fail unless you explicitly convert it to a String via
    // "--test=$testvalue".toString()

    List<String> deployCommand = new ArrayList<>();
    deployCommand.add("gcloud");
    deployCommand.add("run");
    deployCommand.add("services");
    deployCommand.add("replace");
    deployCommand.add(writtenFullConfigFilePaths.stream().collect(Collectors.joining("")));
    deployCommand.add("--region=us-central1");

    String success = "false";
    try {
      jobExecutor.runCommand(deployCommand);
      success = "true";
    } catch (Exception e) {
      throw new CloudrunOperationException(
          "Failed to deploy to Cloud Run with command "
              + deployCommand
              + "exception "
              + e.getMessage());
    } finally {
      deleteFiles(writtenFullConfigFilePaths);
    }
    // task.updateStatus BASE_PHASE, "Done deploying version $versionName..."
    return success;
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application":
   * "myapp", "stack": "stack", "freeFormDetails": "details", "repositoryUrl":
   * "https://github.com/organization/project.git", "branch": "feature-branch", "credentials":
   * "my-appengine-account", "configFilepaths": ["app.yaml"] } } ]'
   * "http://localhost:7002/appengine/ops" curl -X POST -H "Content-Type: application/json" -d '[ {
   * "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details",
   * "repositoryUrl": "https://github.com/organization/project.git", "branch": "feature-branch",
   * "credentials": "my-appengine-account", "configFilepaths": ["app.yaml"], "promote": true,
   * "stopPreviousVersion": true } } ]' "http://localhost:7002/appengine/ops" curl -X POST -H
   * "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp",
   * "stack": "stack", "freeFormDetails": "details", "repositoryUrl":
   * "https://github.com/organization/project.git", "branch": "feature-branch", "credentials":
   * "my-appengine-account", "configFilepaths": ["runtime: python27\napi_version: 1\nthreadsafe:
   * true\nmanual_scaling:\n instances: 5\ninbound_services:\n - warmup\nhandlers:\n - url: /.*\n
   * script: main.app"],} } ]' "http://localhost:7002/appengine/ops" curl -X POST -H "Content-Type:
   * application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack",
   * "freeFormDetails": "details", "credentials": "my-appengine-account", "containerImageUrl":
   * "gcr.io/my-project/my-image:my-tag", "configFiles": ["env: flex\nruntime:
   * custom\nmanual_scaling:\n instances: 1\nresources:\n cpu: 1\n memory_gb: 0.5\n disk_size_gb:
   * 10"] } } ]' "http://localhost:7002/appengine/ops" curl -X POST -H "Content-Type:
   * application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack",
   * "freeFormDetails": "details", "credentials": "my-appengine-credential-name",
   * "containerImageUrl": "gcr.io/my-gcr-repo/image:tag", "configArtifacts": [{ "type":
   * "gcs/object", "name": "gs://path/to/app.yaml", "reference": "gs://path/to/app.yaml",
   * "artifactAccount": "my-gcs-artifact-account-name" }] } } ]'
   * "http://localhost:7002/appengine/ops"
   */
  @Override
  public DeploymentResult operate(List priorOutputs) {

    String baseDir = description.getCredentials().getLocalRepositoryDirectory();

    String directoryPath = getFullDirectoryPath(baseDir);
    String serviceAccount = description.getCredentials().getServiceAccountEmail();
    String region = description.getCredentials().getRegion();
    String deployPath = directoryPath;
    String newVersionName;

    try {
      newVersionName = deploy(deployPath);
      log.info("try");
    } finally {
      log.info("finally");
    }

    // jobExecutor.runCommand(List.of("gcloud", "run", "deploy", "helloworld",
    // "--region=us-central1",
    // "--image=us-central1-docker.pkg.dev/opsmx-ggproject-2022/cloud-run-source-deploy/helloworld:latest"));
    // jobExecutor.runCommand(List.of("gcloud", "run", "services" ,
    // "--region=us-central1","describe", "helloworld"))
    // log.info("here")
    return null;
  }

  public static void deleteFiles(List<String> paths) {
    paths.forEach(
        path -> {
          try {
            new File(path).delete();
          } catch (Exception e) {
            throw new CloudrunOperationException("Could not delete config file: ${e.getMessage()}");
          }
        });
  }

  public static List<String> writeConfigFiles(
      List<String> configFiles, String repositoryPath, String applicationDirectoryRoot) {
    if (configFiles == null) {
      return Collections.<String>emptyList();
    } else {
      return configFiles.stream()
          .map(
              (configFile) -> {
                Path path =
                    generateRandomRepositoryFilePath(repositoryPath, applicationDirectoryRoot);
                try {
                  File targetFile = new File(path.toString());
                  FileUtils.writeStringToFile(targetFile, configFile, StandardCharsets.UTF_8);
                } catch (Exception e) {
                  throw new CloudrunOperationException(
                      "Could not write config file: ${e.getMessage()}");
                }
                return path.toString();
              })
          .collect(Collectors.toList());
    }
  }

  public static Path generateRandomRepositoryFilePath(
      String repositoryPath, String applicationDirectoryRoot) {
    String name = UUID.randomUUID().toString();
    String filePath = applicationDirectoryRoot != null ? applicationDirectoryRoot : ".";
    StringBuilder sb = new StringBuilder(name).append(".yaml");
    return Paths.get(repositoryPath, filePath, sb.toString());
  }

  public static String getFullDirectoryPath(String localRepositoryDirectory) {
    return Paths.get(localRepositoryDirectory).toString();
  }
}

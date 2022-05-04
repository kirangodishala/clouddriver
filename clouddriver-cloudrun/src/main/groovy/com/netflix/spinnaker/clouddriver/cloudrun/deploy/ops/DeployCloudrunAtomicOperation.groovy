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

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DeployCloudrunDescription
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

import java.nio.file.Path
import java.nio.file.Paths

class DeployCloudrunAtomicOperation implements AtomicOperation<DeploymentResult> {

  private static final String BASE_PHASE = "DEPLOY"

  private static final Logger log =
    LoggerFactory.getLogger(DeployCloudrunAtomicOperation.class);

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  Registry registry

  @Autowired
  CloudrunJobExecutor jobExecutor

  DeployCloudrunDescription description

  DeployCloudrunAtomicOperation(DeployCloudrunDescription description) {
    this.description = description
  }

  String deploy(String repositoryPath) {
    def project = description.credentials.project
    def region = description.credentials.region
    def applicationDirectoryRoot = description.applicationDirectoryRoot
    def configFiles = description.configFiles
    def writtenFullConfigFilePaths = writeConfigFiles(configFiles, repositoryPath, applicationDirectoryRoot)

    // runCommand expects a List<String> and will fail if some of the arguments are GStrings (as that is not a subclass
    // of String). It is thus important to only add Strings to deployCommand.  For example, adding a flag "--test=$testvalue"
    // below will cause deployments to fail unless you explicitly convert it to a String via "--test=$testvalue".toString()
    def deployCommand = []

    deployCommand += ["gcloud", "run", "services", "replace", *(writtenFullConfigFilePaths), "--region=us-central1"]

    def success = "false"
    try {
      jobExecutor.runCommand(deployCommand)
      success = "true"
    } catch (e) {
      throw new CloudrunOperationException("Failed to deploy to Cloud Run with command ${deployCommand.join(' ')}: ${e.getMessage()}")
    } finally {
      deleteFiles(writtenFullConfigFilePaths)
    }
    //task.updateStatus BASE_PHASE, "Done deploying version $versionName..."
    return success
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "repositoryUrl": "https://github.com/organization/project.git", "branch": "feature-branch", "credentials": "my-appengine-account", "configFilepaths": ["app.yaml"] } } ]' "http://localhost:7002/appengine/ops"
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "repositoryUrl": "https://github.com/organization/project.git", "branch": "feature-branch", "credentials": "my-appengine-account", "configFilepaths": ["app.yaml"], "promote": true, "stopPreviousVersion": true } } ]' "http://localhost:7002/appengine/ops"
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "repositoryUrl": "https://github.com/organization/project.git", "branch": "feature-branch", "credentials": "my-appengine-account", "configFilepaths": ["runtime: python27\napi_version: 1\nthreadsafe: true\nmanual_scaling:\n  instances: 5\ninbound_services:\n - warmup\nhandlers:\n - url: /.*\n   script: main.app"],} } ]' "http://localhost:7002/appengine/ops"
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "credentials": "my-appengine-account", "containerImageUrl": "gcr.io/my-project/my-image:my-tag", "configFiles": ["env: flex\nruntime: custom\nmanual_scaling:\n  instances: 1\nresources:\n  cpu: 1\n  memory_gb: 0.5\n  disk_size_gb: 10"] } } ]' "http://localhost:7002/appengine/ops"
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "credentials": "my-appengine-credential-name", "containerImageUrl": "gcr.io/my-gcr-repo/image:tag", "configArtifacts": [{ "type": "gcs/object", "name": "gs://path/to/app.yaml", "reference": "gs://path/to/app.yaml", "artifactAccount": "my-gcs-artifact-account-name" }] } } ]' "http://localhost:7002/appengine/ops"
   */
  @Override
  DeploymentResult operate(List priorOutputs) {

    def baseDir = description.credentials.localRepositoryDirectory

    def directoryPath = getFullDirectoryPath(baseDir)
    def serviceAccount = description.credentials.serviceAccountEmail
    def region = description.credentials.region
    String deployPath = directoryPath
    String newVersionName

    try {
      newVersionName = deploy(deployPath)
      log.info("try")
    } finally {
      log.info("finally")
    }

    //jobExecutor.runCommand(List.of("gcloud", "run", "deploy", "helloworld", "--region=us-central1", "--image=us-central1-docker.pkg.dev/opsmx-ggproject-2022/cloud-run-source-deploy/helloworld:latest"));
    //jobExecutor.runCommand(List.of("gcloud", "run", "services" , "--region=us-central1","describe", "helloworld"))
    //log.info("here")
  }

  static void deleteFiles(List<String> paths) {
    paths.each { path ->
      try {
        new File(path).delete()
      } catch (e) {
        throw new CloudrunOperationException("Could not delete config file: ${e.getMessage()}")
      }
    }
  }

  static List<String> writeConfigFiles(List<String> configFiles, String repositoryPath, String applicationDirectoryRoot) {
    if (!configFiles) {
      return []
    } else {
      return configFiles.collect { configFile ->
        def path = generateRandomRepositoryFilePath(repositoryPath, applicationDirectoryRoot)
        try {
          path.toFile() << configFile
        } catch (e) {
          throw new CloudrunOperationException("Could not write config file: ${e.getMessage()}")
        }
        return path.toString()
      }
    }
  }

  static Path generateRandomRepositoryFilePath(String repositoryPath, String applicationDirectoryRoot) {
    def name = UUID.randomUUID().toString()
    return Paths.get(repositoryPath, applicationDirectoryRoot ?: ".", "${name}.yaml")
  }

  static String getFullDirectoryPath(String localRepositoryDirectory) {
    return Paths.get(localRepositoryDirectory).toString()
  }
}

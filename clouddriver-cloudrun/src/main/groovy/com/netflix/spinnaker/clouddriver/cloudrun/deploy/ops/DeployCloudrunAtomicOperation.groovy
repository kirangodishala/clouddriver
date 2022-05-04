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
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import org.springframework.beans.factory.annotation.Autowired

class DeployCloudrunAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "DEPLOY"

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
    if (description.artifact) {
      switch (description.artifact.type) {
        case 'gcs/object':
          String ref = description.artifact.reference
          if (!ref) {
            throw new CloudrunOperationException("Missing artifact reference for GCS deploy")
          }
          description.repositoryUrl = ref.startsWith("gs://") ? ref : "gs://${ref}"
          usesGcs = true
          break
        case 'docker/image':
          if (!description.artifact.name) {
            throw new CloudrunOperationException("Missing artifact name for Flex Custom deploy")
          }
          containerDeployment = description.artifact.name
          break
        default:
          throw new CloudrunOperationException("Unhandled artifact type in description")
          break
      }
    }
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
    jobExecutor.runCommand(List.of("gcloud", "run", "deploy", "helloworld", "--region=us-central1", "--image=us-central1-docker.pkg.dev/opsmx-ggproject-2022/cloud-run-source-deploy/helloworld:latest"));
    //jobExecutor.runCommand(List.of("gcloud", "run", "services" , "--region=us-central1","describe", "helloworld"))
    //log.info("here")
  }


  String cloneOrUpdateLocalRepository(String directoryPath, Integer retryCount) {

  }

  String deploy(String repositoryPath) {

  }

  List<String> fetchConfigArtifacts(List<Artifact> configArtifacts, String repositoryPath, String applicationDirectoryRoot) {

  }

  static List<String> writeConfigFiles(List<String> configFiles, String repositoryPath, String applicationDirectoryRoot) {

  }
}

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

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunOperation
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DeployCloudrunDescription
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunDescriptionConversionException
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops.DeployCloudrunAtomicOperation
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsConverter
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CloudrunOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component
@Slf4j
class DeployCloudrunAtomicOperationConverter extends AbstractAtomicOperationsCredentialsConverter<CloudrunNamedAccountCredentials> {
  @Autowired
  ObjectMapper objectMapper

  AtomicOperation convertOperation(Map input) {
    new DeployCloudrunAtomicOperation(convertDescription(input))
  }

  DeployCloudrunDescription convertDescription(Map input) {
    DeployCloudrunDescription description = CloudrunAtomicOperationConverterHelper.convertDescription(input, this, DeployCloudrunDescription)

    if (input.artifact) {
      description.artifact = objectMapper.convertValue(input.artifact, Artifact)
      switch (description.artifact.type) {
        case 'gcs/object':
          description.repositoryUrl = description.artifact.reference
          if (!description.repositoryUrl.startsWith('gs://')) {
            description.repositoryUrl = "gs://${description.repositoryUrl}"
          }
          break
        case 'docker/image':
          if (description.artifact.reference) {
            description.containerImageUrl = description.artifact.reference
          } else if (description.artifact.name) {
            description.containerImageUrl = description.artifact.name
          }
          break
        default:
          throw new CloudrunDescriptionConversionException("Invalid artifact type for Cloudrun deploy: ${description.artifact.type}")
      }
    }
    if (input.configArtifacts) {
      def configArtifacts = input.configArtifacts
      description.configArtifacts = configArtifacts.collect({ objectMapper.convertValue(it, Artifact) })
    }

    return description
  }
}

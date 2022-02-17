/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.converters

import com.netflix.spinnaker.clouddriver.google.GoogleOperation
import com.netflix.spinnaker.clouddriver.google.deploy.description.RegisterInstancesWithGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.RegisterInstancesWithGoogleLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsConverter
import org.springframework.stereotype.Component

@GoogleOperation(AtomicOperations.REGISTER_INSTANCES_WITH_LOAD_BALANCER)
@Component("registerInstancesWithGoogleLoadBalancerDescription")
class RegisterInstancesWithGoogleLoadBalancerAtomicOperationConverter
  extends AbstractAtomicOperationsCredentialsConverter<GoogleNamedAccountCredentials> {
  @Override
  AtomicOperation convertOperation(Map input) {
    new RegisterInstancesWithGoogleLoadBalancerAtomicOperation(
      convertDescription(input))
  }

  @Override
  RegisterInstancesWithGoogleLoadBalancerDescription convertDescription(Map input) {
    GoogleAtomicOperationConverterHelper.convertDescription(
      input, this, RegisterInstancesWithGoogleLoadBalancerDescription)
  }
}

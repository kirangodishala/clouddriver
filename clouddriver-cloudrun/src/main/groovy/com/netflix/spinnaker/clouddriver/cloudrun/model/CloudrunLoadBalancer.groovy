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

package com.netflix.spinnaker.clouddriver.cloudrun.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.appengine.v1.model.Service
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.moniker.Moniker
import groovy.transform.AutoClone
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode(includes = ["name", "account"])
@JsonInclude(JsonInclude.Include.NON_NULL)
class CloudrunLoadBalancer implements LoadBalancer, Serializable {
  String name
  String selfLink
  String region
  final String type = CloudrunCloudProvider.ID
  final String cloudProvider = CloudrunCloudProvider.ID
  String account
  Set<LoadBalancerServerGroup> serverGroups = new HashSet<>()
  CloudrunTrafficSplit split
  String httpUrl
  String httpsUrl
  String project
  List<CloudrunPlatformApplication.CloudrunDispatchRule> dispatchRules

  void setMoniker(Moniker _ignored) {}

  CloudrunLoadBalancer() { }

  CloudrunLoadBalancer(Service service, String account, String region) {
    this.name = service.getId()
    this.selfLink = service.getName()
    this.account = account
    this.region = region
    this.split = new ObjectMapper().convertValue(service.getSplit(), CloudrunTrafficSplit)
    this.httpUrl = CloudrunModelUtil.getHttpUrl(service.getName())
    this.httpsUrl = CloudrunModelUtil.getHttpsUrl(service.getName())
    // Self link has the form apps/{project}/services/{service}.
    this.project = this.selfLink.split('/')[1]
  }

  Void setLoadBalancerServerGroups(Set<CloudrunServerGroup> serverGroups) {
    this.serverGroups = serverGroups?.collect { serverGroup ->
      def instances = serverGroup.isDisabled() ? [] : serverGroup.instances?.collect { instance ->
          new LoadBalancerInstance(id: instance.name, health: [state: instance.healthState.toString() as Object])
        } ?: []

      def detachedInstances = serverGroup.isDisabled() ? serverGroup.instances?.collect { it.name } ?: [] : []

      new CloudrunLoadBalancerServerGroup(
        name: serverGroup.name,
        region: serverGroup.region,
        isDisabled: serverGroup.isDisabled(),
        allowsGradualTrafficMigration: serverGroup.allowsGradualTrafficMigration,
        instances: instances as Set,
        detachedInstances: detachedInstances as Set,
        cloudProvider: CloudrunCloudProvider.ID
      )
    } as Set<LoadBalancerServerGroup>
    null
  }

  static class CloudrunLoadBalancerServerGroup extends LoadBalancerServerGroup {
    Boolean allowsGradualTrafficMigration
  }
}

@AutoClone
@EqualsAndHashCode(includes = ["allocations", "shardBy"])
class CloudrunTrafficSplit {
  Map<String, Double> allocations
  ShardBy shardBy
}

enum ShardBy {
  /*
  * See https://cloud.google.com/appengine/docs/admin-api/reference/rest/v1/apps.services#ShardBy
  * */
  UNSPECIFIED,
  COOKIE,
  IP,
  RANDOM,
}

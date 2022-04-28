/*
 * Copyright 2022 OpsMx Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudrun.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider
import com.netflix.spinnaker.clouddriver.cloudrun.cache.Keys
import com.netflix.spinnaker.clouddriver.cloudrun.model.*
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CloudrunClusterProvider implements ClusterProvider<CloudrunCluster> {
  @Autowired
  Cache cacheView

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  CloudrunApplicationProvider cloudrunApplicationProvider

  @Override
  Set<CloudrunCluster> getClusters(String applicationName, String account) {
    CacheData application = cacheView.get(Keys.Namespace.APPLICATIONS.ns,
                                          Keys.getApplicationKey(applicationName),
                                          RelationshipCacheFilter.include(Keys.Namespace.CLUSTERS.ns))
    if (!application) {
      return []
    }

    Collection<String> clusterKeys = application.relationships[Keys.Namespace.CLUSTERS.ns]
      .findAll { Keys.parse(it).account == account }
    Collection<CacheData> clusterData = cacheView.getAll(Keys.Namespace.CLUSTERS.ns, clusterKeys)

    translateClusters(clusterData, true)
  }

  @Override
  Map<String, Set<CloudrunCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(Keys.Namespace.CLUSTERS.ns)
    translateClusters(clusterData, true).groupBy { it.accountName } as Map<String, Set<CloudrunCluster>>
  }

  @Override
  CloudrunCluster getCluster(String application, String account, String name, boolean includeDetails) {
    List<CacheData> clusterData =
      [cacheView.get(Keys.Namespace.CLUSTERS.ns, Keys.getClusterKey(account, application, name))] - null

    clusterData ? translateClusters(clusterData, includeDetails).head() : null
  }

  @Override
  CloudrunCluster getCluster(String applicationName, String account, String clusterName) {
    return getCluster(applicationName, account, clusterName, true)
  }

  @Override
  CloudrunServerGroup getServerGroup(String account, String region, String serverGroupName, boolean includeDetails) {
    String serverGroupKey = Keys.getServerGroupKey(account, serverGroupName, region)
    CacheData serverGroupData = cacheView.get(Keys.Namespace.SERVER_GROUPS.ns, serverGroupKey)

    if (!serverGroupData) {
      return null
    }

    Set<CloudrunInstance> instances = cacheView.getAll(Keys.Namespace.INSTANCES.ns, serverGroupData.relationships[Keys.Namespace.INSTANCES.ns])
      .findResults { CloudrunProviderUtils.instanceFromCacheData(objectMapper, it) }

    CloudrunProviderUtils.serverGroupFromCacheData(objectMapper, serverGroupData, instances)
  }

  @Override
  CloudrunServerGroup getServerGroup(String account, String region, String serverGroupName) {
    return getServerGroup(account, region, serverGroupName, true);
  }

  @Override
  Map<String, Set<CloudrunCluster>> getClusterSummaries(String applicationName) {
    translateClusters(getClusterData(applicationName), false)?.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet<>(v)] }
  }

  @Override
  Map<String, Set<CloudrunCluster>> getClusterDetails(String applicationName) {
    translateClusters(getClusterData(applicationName), true)?.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet<>(v)] }
  }

  Set<CacheData> getClusterData(String applicationName) {
    CloudrunApplication application = cloudrunApplicationProvider.getApplication(applicationName)

    def clusterKeys = []
    application?.clusterNames?.each { String accountName, Set<String> clusterNames ->
      clusterKeys.addAll(clusterNames.collect { clusterName ->
        Keys.getClusterKey(accountName, applicationName, clusterName)
      })
    }

    cacheView.getAll(Keys.Namespace.CLUSTERS.ns,
                     clusterKeys,
                     RelationshipCacheFilter.include(Keys.Namespace.SERVER_GROUPS.ns, Keys.Namespace.LOAD_BALANCERS.ns))
  }

  @Override
  String getCloudProviderId() {
    CloudrunCloudProvider.ID
  }

  @Override
  boolean supportsMinimalClusters() {
    return false
  }

  Collection<CloudrunServerGroup> translateClusters(Collection<CacheData> clusterData, boolean includeDetails) {
    if (!clusterData) {
      return []
    }

    Map<String, CloudrunLoadBalancer> loadBalancers = includeDetails ?
      translateLoadBalancers(CloudrunProviderUtils.resolveRelationshipDataForCollection(
        cacheView,
        clusterData,
        Keys.Namespace.LOAD_BALANCERS.ns)) : null

    Map<String, Set<CloudrunServerGroup>> serverGroups = includeDetails ?
      translateServerGroups(CloudrunProviderUtils.resolveRelationshipDataForCollection(
        cacheView,
        clusterData,
        Keys.Namespace.SERVER_GROUPS.ns,
        RelationshipCacheFilter.include(Keys.Namespace.INSTANCES.ns, Keys.Namespace.LOAD_BALANCERS.ns))) : null

    return clusterData.collect { CacheData clusterDataEntry ->
      Map<String, String> clusterKey = Keys.parse(clusterDataEntry.id)
      CloudrunCluster cluster = new CloudrunCluster(accountName: clusterKey.account, name: clusterKey.name)

      if (includeDetails) {
        cluster.loadBalancers = clusterDataEntry.relationships[Keys.Namespace.LOAD_BALANCERS.ns]?.findResults { id ->
          loadBalancers.get(id)
        }
        cluster.serverGroups = serverGroups[cluster.name]?.findAll { it.account == cluster.accountName } ?: []
      } else {
        cluster.loadBalancers = clusterDataEntry.relationships[Keys.Namespace.LOAD_BALANCERS.ns].collect { loadBalancerKey ->
          def parts = Keys.parse(loadBalancerKey)
          new CloudrunLoadBalancer(name: parts.name, account: parts.account)
        }
        cluster.serverGroups = clusterDataEntry.relationships[Keys.Namespace.SERVER_GROUPS.ns].collect { serverGroupKey ->
          def parts = Keys.parse(serverGroupKey)
          new CloudrunServerGroup(name: parts.name, account: parts.account, region: parts.region)
        }
      }
      cluster
    }
  }

  Map<String, Set<CloudrunServerGroup>> translateServerGroups(Collection<CacheData> serverGroupData) {
    def instanceCacheDataMap = CloudrunProviderUtils
      .preserveRelationshipDataForCollection(cacheView,
                                             serverGroupData,
                                             Keys.Namespace.INSTANCES.ns,
                                             RelationshipCacheFilter.none())

    def instances = instanceCacheDataMap.collectEntries { String key, Collection<CacheData> cacheData ->
        [(key): cacheData.findResults { CloudrunProviderUtils.instanceFromCacheData(objectMapper, it) } as Set ]
    }

    return serverGroupData
      .inject([:].withDefault { [] }, { Map<String, Set<CloudrunServerGroup>> acc, CacheData cacheData ->
        def serverGroup = CloudrunProviderUtils.serverGroupFromCacheData(objectMapper,
                                                                          cacheData,
                                                                          instances[cacheData.id])
        acc[Names.parseName(serverGroup.name).cluster].add(serverGroup)
        acc
      })
  }

  static Map<String, CloudrunLoadBalancer> translateLoadBalancers(Collection<CacheData> loadBalancerData) {
    loadBalancerData.collectEntries { loadBalancerEntry ->
      def parts = Keys.parse(loadBalancerEntry.id)
      [(loadBalancerEntry.id): new CloudrunLoadBalancer(name: parts.name, account: parts.account)]
    }
  }
}

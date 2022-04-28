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

package com.netflix.spinnaker.clouddriver.cloudrun.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider
import com.netflix.spinnaker.clouddriver.cloudrun.cache.Keys
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunApplication
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CloudrunApplicationProvider implements ApplicationProvider {
  @Autowired
  Cache cacheView

  @Autowired
  ObjectMapper objectMapper

  @Override
  Set<CloudrunApplication> getApplications(boolean expand) {
    def filter = expand ? RelationshipCacheFilter.include(Keys.Namespace.CLUSTERS.ns) : RelationshipCacheFilter.none()
    cacheView.getAll(Keys.Namespace.APPLICATIONS.ns,
                     cacheView.filterIdentifiers(Keys.Namespace.APPLICATIONS.ns, "${CloudrunCloudProvider.ID}:*"),
                     filter).collect { applicationFromCacheData(it) } as Set
  }

  @Override
  CloudrunApplication getApplication(String name) {
    CacheData cacheData = cacheView.get(Keys.Namespace.APPLICATIONS.ns,
                                        Keys.getApplicationKey(name),
                                        RelationshipCacheFilter.include(Keys.Namespace.CLUSTERS.ns))

    cacheData ? applicationFromCacheData(cacheData) : null
  }

  CloudrunApplication applicationFromCacheData (CacheData cacheData) {
    CloudrunApplication application = objectMapper.convertValue(cacheData.attributes, CloudrunApplication)

    cacheData.relationships[Keys.Namespace.CLUSTERS.ns].each { String clusterKey ->
      def parsedClusterKey = Keys.parse(clusterKey)
      application.clusterNames[parsedClusterKey.account] << parsedClusterKey.name
    }
    application
  }
}

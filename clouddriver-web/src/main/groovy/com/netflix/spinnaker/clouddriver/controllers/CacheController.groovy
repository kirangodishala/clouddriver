/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.cats.cache.AgentIntrospection
import com.netflix.spinnaker.cats.cache.CacheIntrospectionStore
import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheStatus
import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheUpdater
import com.netflix.spinnaker.clouddriver.cache.OnDemandType
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import com.netflix.spinnaker.clouddriver.helpers.WriteToFile;
import java.time.LocalDateTime;
@RestController
@RequestMapping("/cache")
class CacheController {

  @Autowired
  List<OnDemandCacheUpdater> onDemandCacheUpdaters

  @RequestMapping(method = RequestMethod.POST, value = "/{cloudProvider}/{type}")
  ResponseEntity handleOnDemand(@PathVariable String cloudProvider,
                                @PathVariable String type,
                                @RequestBody Map<String, ? extends Object> data) {
    StringBuilder builder= new StringBuilder("\n" + LocalDateTime.now() + "  /cache/"+ cloudProvider + "/" + type +" -> \n CacheController.handleOnDemand() \n");

    OnDemandType onDemandType = getOnDemandType(type);

    def onDemandCacheResult = onDemandCacheUpdaters.find {
      it.handles(onDemandType, cloudProvider)
    }?.handle(onDemandType, cloudProvider, data)

    def cacheStatus = onDemandCacheResult?.status
    def httpStatus = (cacheStatus == OnDemandCacheStatus.PENDING) ? HttpStatus.ACCEPTED : HttpStatus.OK
    builder.append("\n")
    builder.append(cachedIdentifiersByType: onDemandCacheResult?.cachedIdentifiersByType ?: [:])
    builder.append("\n")
    builder.append(httpStatus)
    builder.append("  /cache/" + cloudProvider + "/" + type + " <<-- \n ");
    WriteToFile.createTempFile(builder.toString().getBytes());
    return new ResponseEntity(
      [
        cachedIdentifiersByType: onDemandCacheResult?.cachedIdentifiersByType ?: [:]
      ],
      httpStatus
    )
  }


  @RequestMapping(method = RequestMethod.GET, value = "/introspection")
  Collection <AgentIntrospection> getAgentIntrospections() {
    WriteToFile.createTempFile("\n" + LocalDateTime.now() + "  /introspection \n\n");

    return CacheIntrospectionStore.getStore().listAgentIntrospections()
        // sort by descending start time, so newest executions are first
        .toSorted { a, b -> b.getLastExecutionStartMs() <=> a.getLastExecutionStartMs() }
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{cloudProvider}/{type}")
  Collection<Map> pendingOnDemands(@PathVariable String cloudProvider,
                                   @PathVariable String type,
                                   @RequestParam(value = "id", required = false) String id) {
    WriteToFile.createTempFile("\n" + LocalDateTime.now() + "  /{cloudProvider}/{type} \n\n");
    OnDemandType onDemandType = getOnDemandType(type)
    onDemandCacheUpdaters.findAll {
      it.handles(onDemandType, cloudProvider)
    }?.collect {
      if (id) {
        def pendingOnDemandRequest = it.pendingOnDemandRequest(onDemandType, cloudProvider, id)
        return pendingOnDemandRequest ? [ pendingOnDemandRequest ] : []
      }
      return it.pendingOnDemandRequests(onDemandType, cloudProvider)
    }.flatten()
  }

  static OnDemandType getOnDemandType(String type) {
    WriteToFile.createTempFile("\n" + LocalDateTime.now() + "  CacheController.getOnDemandType() \n\n");

    try {
      return OnDemandType.fromString(type)
    } catch (IllegalArgumentException e) {
      throw new NotFoundException(e.message)
    }
  }
}

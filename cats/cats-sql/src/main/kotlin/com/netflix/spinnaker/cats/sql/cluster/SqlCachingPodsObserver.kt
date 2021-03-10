/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.cats.sql.cluster

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.cluster.NodeIdentity
import com.netflix.spinnaker.cats.sql.SqlUtil
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.kork.sql.routing.withPool
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class SqlCachingPodsObserver (
  private val jooq: DSLContext,
  private val nodeIdentity: NodeIdentity,
  private val tableNamespace: String? = null,
  private val liveReplicasRecheckIntervalSeconds : Long? = null,
  private val liveReplicasScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
    ThreadFactoryBuilder().setNameFormat(SqlCachingPodsObserver::class.java.simpleName + "-%d").build()
  )
) : ShardingFilter, Runnable{
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private val POOL_NAME = ConnectionPools.CACHE_WRITER.value
  }
  private val replicasReferenceTable = "caching_replicas"
  private val replicasTable = if (tableNamespace.isNullOrBlank()) {
    replicasReferenceTable
  } else {
    "${replicasReferenceTable}_$tableNamespace"
  }

  init {
    if (!tableNamespace.isNullOrBlank()) {
      withPool(POOL_NAME) {
        SqlUtil.createTableLike(jooq, replicasTable, replicasReferenceTable)
      }
    }
    refreshHeartbeat(TimeUnit.SECONDS.toMillis(60))
    val recheckInterval = liveReplicasRecheckIntervalSeconds ?: 30L
    liveReplicasScheduler.scheduleAtFixedRate(this, 0, recheckInterval, TimeUnit.SECONDS)
    log.info("Account sharding across caching pods is enabled.")
  }

  override fun run() {
    try {
      refreshHeartbeat(TimeUnit.SECONDS.toMillis(60))
    } catch (t: Throwable) {
      log.error("Failed to manage replicas heartbeat", t)
    }

  }

  private fun refreshHeartbeat(newTtl: Long){
    recordHeartbeat(newTtl)
    deleteExpiredReplicas()
  }

  private fun recordHeartbeat( newTtl: Long) {
    try {
      withPool(POOL_NAME) {
        val currentPodRecord = jooq.select()
          .from(table(replicasTable))
          .where(
            DSL.field("pod_name").eq(nodeIdentity.nodeIdentity))
          .fetch()
          .intoResultSet()
        // insert heartbeat
        if (!currentPodRecord.next()) {
          jooq.insertInto(table(replicasTable))
            .columns(
              DSL.field("pod_name"),
              DSL.field("last_heartbeat_time")
            )
            .values(
              nodeIdentity.nodeIdentity,
              System.currentTimeMillis() + newTtl
            )
            .execute()
          log.info("Heartbeat record created for {}", nodeIdentity.nodeIdentity)
        } else {
          //update heartbeat
          jooq.update(table(replicasTable))
            .set(DSL.field("last_heartbeat_time"), System.currentTimeMillis() + newTtl)
            .where(DSL.field("pod_name").eq(nodeIdentity.nodeIdentity))
            .execute()
          log.info("Heartbeat updated for {}", nodeIdentity.nodeIdentity)
        }

      }
    } catch (e: DataIntegrityViolationException) {
      log.error("Unexpected DataIntegrityViolationException", e)
    } catch (e: SQLException) {
      log.error("Unexpected sql exception while trying to acquire agent lock", e)
    }
  }

  private fun deleteExpiredReplicas(){
    try {
      withPool(POOL_NAME) {
        val existingReplicas = jooq.select()
          .from(table(replicasTable))
          .fetch()
          .intoResultSet()
        val now = System.currentTimeMillis()
        while (existingReplicas.next()) {
          val expiry = existingReplicas.getLong("last_heartbeat_time")
          if (now > expiry) {
            try {
              jooq.deleteFrom(table(replicasTable))
                .where(
                  DSL.field("pod_name").eq(existingReplicas.getString("pod_name"))
                    .and(DSL.field("last_heartbeat_time").eq(expiry))
                )
                .execute()
            } catch (e: SQLException) {
              log.warn(
                "Failed deleting replica entry ${existingReplicas.getString("pod_name")} with expiry " +
                  expiry, e )
            }
          }
        }
      }
    } catch (e: SQLException) {
      log.error("Unexpected sql exception while trying to get replica records : ", e)
    }
  }

  private fun getAccountName(agentType: String): String{
    return if(agentType.contains("/")) agentType.substring(0,agentType.indexOf('/')) else agentType
  }

  override fun filter(agent: Agent) : Boolean{
    if(agent.providerName.equals(CoreProvider.PROVIDER_NAME)){
      return true
    }
    var counter = 0
    var index = -1
    try {
      withPool(POOL_NAME) {
        val cachingPods = jooq.select()
          .from(table(replicasTable))
          .orderBy(DSL.field("pod_name"))
          .fetch()
          .intoResultSet()

        while (cachingPods.next()) {
          if (cachingPods.getString("pod_name").equals(nodeIdentity.nodeIdentity)) {
            index = counter;
          }
          counter++
        }
        log.info("Caching replicas count : {}",counter )
      }
    }catch (e: SQLException){
      log.error( "Failed to fetch live pods count ${e.message}")
      return true
    }

    if(counter == 0 || index == -1){
      throw RuntimeException("No caching pod heartbeat records detected. Sharding logic can't be applied!!!!")
    }

    if (counter == 1 || abs(getAccountName(agent.agentType).hashCode() % counter) == index) {
      log.info("pod-count : $counter, index: $index, abs : " +
        "${abs(getAccountName(agent.agentType).hashCode() % counter)}")
      return true
    }
    log.info("${agent.agentType} is skipped. index: $index, replica count: $counter")
    return false
  }


}

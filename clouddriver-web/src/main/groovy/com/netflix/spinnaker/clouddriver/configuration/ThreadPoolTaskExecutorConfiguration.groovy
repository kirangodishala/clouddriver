package com.netflix.spinnaker.clouddriver.configuration

import groovy.transform.Canonical
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@Canonical
@ConfigurationProperties('thread-pool-executor')
class ThreadPoolTaskExecutorConfiguration {
  Integer corePoolSize = 10
  Integer maxPoolSize = 50
  Integer queueCapacity = 300
}

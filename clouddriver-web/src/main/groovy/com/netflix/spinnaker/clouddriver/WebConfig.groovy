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

package com.netflix.spinnaker.clouddriver

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.configuration.CredentialsConfiguration
import com.netflix.spinnaker.clouddriver.configuration.ThreadPoolTaskExecutorConfiguration
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueueConfiguration
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.filter.ShallowEtagHeaderFilter
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter

import javax.servlet.Filter
import javax.servlet.http.HttpServletResponse

@Slf4j
@Configuration
@ComponentScan([
  'com.netflix.spinnaker.clouddriver.controllers',
  'com.netflix.spinnaker.clouddriver.filters',
  'com.netflix.spinnaker.clouddriver.listeners',
  'com.netflix.spinnaker.clouddriver.security',
])
@EnableConfigurationProperties([CredentialsConfiguration, RequestQueueConfiguration, ThreadPoolTaskExecutorConfiguration])
public class WebConfig extends WebMvcConfigurerAdapter {
  @Autowired
  Registry registry

  @Autowired
  ThreadPoolTaskExecutorConfiguration threadPoolTaskExecutorConfiguration

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
      new MetricsInterceptor(
        this.registry, "controller.invocations", ["account", "region"], ["BasicErrorController"]
      )
    )
  }

  @Bean
  Filter eTagFilter() {
    new ShallowEtagHeaderFilter()
  }

  @Bean
  RequestQueue requestQueue(DynamicConfigService dynamicConfigService,
                            RequestQueueConfiguration requestQueueConfiguration,
                            Registry registry) {
    return RequestQueue.forConfig(dynamicConfigService, registry, requestQueueConfiguration)
  }

  @Bean
  FilterRegistrationBean authenticatedRequestFilter() {
    def frb = new FilterRegistrationBean(new AuthenticatedRequestFilter(true))
    frb.order = Ordered.HIGHEST_PRECEDENCE
    return frb
  }

  @Override
  void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
    configurer
      .defaultContentType(MediaType.APPLICATION_JSON_UTF8)
      .favorPathExtension(false)
      .ignoreAcceptHeader(true)
  }

  @Override
  void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    log.info("corePoolSize: {}", threadPoolTaskExecutorConfiguration.corePoolSize)
    log.info("maxPoolSize: {}", threadPoolTaskExecutorConfiguration.maxPoolSize)
    log.info("queueCapacity: {}", threadPoolTaskExecutorConfiguration.queueCapacity)
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(threadPoolTaskExecutorConfiguration.corePoolSize);
    executor.setMaxPoolSize(threadPoolTaskExecutorConfiguration.maxPoolSize);
    executor.setQueueCapacity(threadPoolTaskExecutorConfiguration.queueCapacity)
    executor.setThreadNamePrefix("thread-pool-task-executor-");
    executor.initialize();
    configurer.setTaskExecutor(executor);
  }
}

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

import com.google.api.services.appengine.v1.model.Application
import groovy.transform.TupleConstructor

class CloudrunPlatformApplication {
  List<CloudrunDispatchRule> dispatchRules
  String authDomain
  String codeBucket
  String defaultBucket
  String defaultCookieExpiration
  String defaultHostname
  String id
  String locationId

    CloudrunPlatformApplication() {}

    CloudrunPlatformApplication(Application application) {
    this.dispatchRules = application.getDispatchRules()?.collect { rule ->
      new CloudrunDispatchRule(rule.getDomain(), rule.getPath(), rule.getService())
    } ?: []
    this.authDomain = application.getAuthDomain()
    this.codeBucket = application.getCodeBucket()
    this.defaultBucket = application.getDefaultBucket()
    this.defaultCookieExpiration = application.getDefaultCookieExpiration()
    this.defaultHostname = application.getDefaultHostname()
    this.id = application.getId()
    this.locationId = application.getLocationId()
  }

  @TupleConstructor
  static class CloudrunDispatchRule {
    String domain
    String path
    String service
  }
}

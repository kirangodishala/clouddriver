/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.validators


import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.credentials.CredentialsRepository

class StandardCloudrunAttributeValidator {
  static final namePattern = /^[a-z0-9]+([-a-z0-9]*[a-z0-9])?$/
  static final prefixPattern = /^[a-z0-9]+$/

  String context
  ValidationErrors errors

    StandardCloudrunAttributeValidator(String context, ValidationErrors errors) {
    this.context = context
    this.errors = errors
  }

  def validateCredentials(String credentials, CredentialsRepository<CloudrunNamedAccountCredentials> credentialsRepository) {
    def result = validateNotEmpty(credentials, "account")
    if (result) {
      def cloudrunCredentials = credentialsRepository.getOne(credentials)
      if (!cloudrunCredentials) {
        errors.rejectValue("${context}.account",  "${context}.account.notFound")
        result = false
      }
    }
    result
  }

  def validateNotEmpty(Object value, String attribute) {
    if (value != "" && value != null && value != []) {
      return true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.empty")
      return false
    }
  }
}

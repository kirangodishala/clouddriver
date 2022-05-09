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

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DestroyCloudrunDescription;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import groovy.util.logging.Slf4j;
import java.util.List;

@Slf4j
public class DestroyCloudrunAtomicOperation extends CloudrunAtomicOperation<Void> {

  private static final String BASE_PHASE = "DESTROY_SERVER_GROUP";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private final DestroyCloudrunDescription description;

  public DestroyCloudrunAtomicOperation(DestroyCloudrunDescription description) {
    this.description = description;
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "destroyServerGroup": {
   * "serverGroupName": "app-stack-detail-v000", "credentials": "my-appengine-account" }} ]'
   * localhost:7002/appengine/ops
   */
  @Override
  public Void operate(List priorOutputs) {
    return null;
  }
}

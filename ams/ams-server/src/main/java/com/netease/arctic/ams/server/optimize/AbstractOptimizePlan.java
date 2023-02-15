/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netease.arctic.ams.server.optimize;

import com.netease.arctic.ams.api.OptimizeType;
import com.netease.arctic.ams.server.model.BasicOptimizeTask;
import com.netease.arctic.ams.server.model.TableOptimizeRuntime;
import com.netease.arctic.table.ArcticTable;
import com.netease.arctic.table.TableIdentifier;
import com.netease.arctic.table.TableProperties;
import org.apache.iceberg.util.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractOptimizePlan {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractOptimizePlan.class);

  protected final ArcticTable arcticTable;
  protected final TableOptimizeRuntime tableOptimizeRuntime;
  protected final int queueId;
  protected final long currentTime;
  protected final String planGroup;

  // We store current partitions, for the next plans to decide if any partition reach the max plan interval,
  // if not, the new added partitions will be ignored by mistake.
  // After plan files, current partitions of table will be set.
  protected final Set<String> currentPartitions = new HashSet<>();

  public AbstractOptimizePlan(ArcticTable arcticTable, TableOptimizeRuntime tableOptimizeRuntime,
                              int queueId, long currentTime) {
    this.arcticTable = arcticTable;
    this.tableOptimizeRuntime = tableOptimizeRuntime;
    this.queueId = queueId;
    this.currentTime = currentTime;
    this.planGroup = UUID.randomUUID().toString();
  }

  public TableIdentifier tableId() {
    return arcticTable.id();
  }

  public OptimizePlanResult plan() {
    long startTime = System.nanoTime();

    addOptimizeFiles();

    if (!hasFileToOptimize()) {
      return buildOptimizePlanResult(Collections.emptyList());
    }

    List<BasicOptimizeTask> tasks = collectTasks(currentPartitions);

    long endTime = System.nanoTime();
    LOG.debug("{} ==== {} plan tasks cost {} ns, {} ms", tableId(), getOptimizeType(), endTime - startTime,
        (endTime - startTime) / 1_000_000);
    LOG.debug("{} {} plan get {} tasks", tableId(), getOptimizeType(), tasks.size());
    return buildOptimizePlanResult(tasks);
  }

  private OptimizePlanResult buildOptimizePlanResult(List<BasicOptimizeTask> optimizeTasks) {
    return new OptimizePlanResult(this.currentPartitions, optimizeTasks, getOptimizeType(), getCurrentSnapshotId(),
        getCurrentChangeSnapshotId(), this.planGroup);
  }

  protected List<BasicOptimizeTask> collectTasks(Set<String> partitions) {
    List<BasicOptimizeTask> results = new ArrayList<>();

    List<String> skippedPartitions = new ArrayList<>();
    for (String partition : partitions) {

      // partition don't need to plan
      if (!partitionNeedPlan(partition)) {
        skippedPartitions.add(partition);
        continue;
      }

      List<BasicOptimizeTask> optimizeTasks = collectTask(partition);
      LOG.debug("{} partition {} ==== collect {} {} tasks", tableId(), partition, optimizeTasks.size(),
          getOptimizeType());
      results.addAll(optimizeTasks);
    }

    LOG.debug("{} ==== after collect {} task, skip partitions {}/{}", tableId(), getOptimizeType(),
        skippedPartitions.size(), partitions.size());
    return results;
  }

  protected long getSmallFileSize(Map<String, String> properties) {
    if (!properties.containsKey(TableProperties.SELF_OPTIMIZING_FRAGMENT_RATIO) &&
        properties.containsKey(TableProperties.OPTIMIZE_SMALL_FILE_SIZE_BYTES_THRESHOLD)) {
      return Long.parseLong(properties.get(TableProperties.OPTIMIZE_SMALL_FILE_SIZE_BYTES_THRESHOLD));
    } else {
      long targetSize = PropertyUtil.propertyAsLong(properties, TableProperties.SELF_OPTIMIZING_TARGET_SIZE,
          TableProperties.SELF_OPTIMIZING_TARGET_SIZE_DEFAULT);
      int fragmentRatio = PropertyUtil.propertyAsInt(properties, TableProperties.SELF_OPTIMIZING_FRAGMENT_RATIO,
          TableProperties.SELF_OPTIMIZING_FRAGMENT_RATIO_DEFAULT);
      return targetSize / fragmentRatio;
    }
  }

  protected abstract long getCurrentSnapshotId();

  protected long getCurrentChangeSnapshotId() {
    return TableOptimizeRuntime.INVALID_SNAPSHOT_ID;
  }

  /**
   * check whether partition need to plan
   *
   * @param partitionToPath target partition
   * @return whether partition need to plan. if true, partition try to plan, otherwise skip.
   */
  protected abstract boolean partitionNeedPlan(String partitionToPath);

  /**
   * init optimize files structure, such as construct NodeTree for ArcticTable
   */
  protected abstract void addOptimizeFiles();

  /**
   * check whether table has files need to optimize after addOptimizeFiles
   *
   * @return whether table has files need to optimize, if true, table try to plan, otherwise skip.
   */
  protected abstract boolean hasFileToOptimize();

  /**
   * collect tasks of given partition
   *
   * @param partition target partition
   * @return tasks of given partition
   */
  protected abstract List<BasicOptimizeTask> collectTask(String partition);

  protected abstract OptimizeType getOptimizeType();
}

package com.netease.arctic.ams.server.optimize;

import com.netease.arctic.ams.server.model.BasicOptimizeTask;
import com.netease.arctic.ams.server.model.TableOptimizeRuntime;
import com.netease.arctic.table.TableProperties;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestIcebergFullOptimizePlan extends TestIcebergBase {
  @Test
  public void testNoPartitionFullOptimize() throws Exception {
    icebergNoPartitionTable.asUnkeyedTable().updateProperties()
        .set(com.netease.arctic.table.TableProperties.SELF_OPTIMIZING_FRAGMENT_RATIO,
            com.netease.arctic.table.TableProperties.SELF_OPTIMIZING_TARGET_SIZE_DEFAULT / 1000 + "")
        .set(com.netease.arctic.table.TableProperties.SELF_OPTIMIZING_MAJOR_TRIGGER_DUPLICATE_RATIO, "0")
        .commit();
    List<DataFile> dataFiles = insertDataFiles(icebergNoPartitionTable.asUnkeyedTable(), 10);
    insertEqDeleteFiles(icebergNoPartitionTable.asUnkeyedTable(), 5);
    insertPosDeleteFiles(icebergNoPartitionTable.asUnkeyedTable(), dataFiles);
    List<FileScanTask> fileScanTasks;
    try (CloseableIterable<FileScanTask> fileIterable = icebergNoPartitionTable.asUnkeyedTable().newScan()
        .planFiles()) {
      fileScanTasks = Lists.newArrayList(fileIterable);
    }
    IcebergFullOptimizePlan optimizePlan = new IcebergFullOptimizePlan(icebergNoPartitionTable,
        new TableOptimizeRuntime(icebergNoPartitionTable.id()),
        fileScanTasks, 1, System.currentTimeMillis(),
        icebergNoPartitionTable.asUnkeyedTable().currentSnapshot().snapshotId());
    List<BasicOptimizeTask> tasks = optimizePlan.plan().getOptimizeTasks();
    Assert.assertEquals(1, tasks.size());
  }

  @Test
  public void testPartitionFullOptimize() throws Exception {
    icebergPartitionTable.asUnkeyedTable().updateProperties()
        .set(com.netease.arctic.table.TableProperties.SELF_OPTIMIZING_FRAGMENT_RATIO,
            com.netease.arctic.table.TableProperties.SELF_OPTIMIZING_TARGET_SIZE_DEFAULT / 1000 + "")
        .set(com.netease.arctic.table.TableProperties.SELF_OPTIMIZING_MAJOR_TRIGGER_DUPLICATE_RATIO, "0")
        .commit();
    List<DataFile> dataFiles = insertDataFiles(icebergPartitionTable.asUnkeyedTable(), 10);
    insertEqDeleteFiles(icebergPartitionTable.asUnkeyedTable(), 5);
    insertPosDeleteFiles(icebergPartitionTable.asUnkeyedTable(), dataFiles);
    List<FileScanTask> fileScanTasks;
    try (CloseableIterable<FileScanTask> fileIterable = icebergPartitionTable.asUnkeyedTable().newScan().planFiles()) {
      fileScanTasks = Lists.newArrayList(fileIterable);
    }
    IcebergFullOptimizePlan optimizePlan = new IcebergFullOptimizePlan(icebergPartitionTable,
        new TableOptimizeRuntime(icebergPartitionTable.id()),
        fileScanTasks, 1, System.currentTimeMillis(),
        icebergPartitionTable.asUnkeyedTable().currentSnapshot().snapshotId());
    List<BasicOptimizeTask> tasks = optimizePlan.plan().getOptimizeTasks();
    Assert.assertEquals(1, tasks.size());
  }

  @Test
  public void testBinPackPlan() throws Exception {
    // small file size 1000, target size 3000
    int fragmentRatio = 3;
    icebergNoPartitionTable.asUnkeyedTable().updateProperties()
        .set(TableProperties.SELF_OPTIMIZING_FRAGMENT_RATIO, fragmentRatio + "")
        .set(TableProperties.SELF_OPTIMIZING_MAJOR_TRIGGER_DUPLICATE_RATIO, "0")
        .set(TableProperties.SELF_OPTIMIZING_TARGET_SIZE, "3000")
        .commit();
    // write 50 data files with size =~ 1000
    List<DataFile> dataFiles = insertDataFiles(icebergNoPartitionTable.asUnkeyedTable(), 10);
    insertEqDeleteFiles(icebergNoPartitionTable.asUnkeyedTable(), 5);
    insertPosDeleteFiles(icebergNoPartitionTable.asUnkeyedTable(), dataFiles);
    List<FileScanTask> fileScanTasks;
    try (CloseableIterable<FileScanTask> fileIterable = icebergNoPartitionTable.asUnkeyedTable().newScan()
        .planFiles()) {
      fileScanTasks = Lists.newArrayList(fileIterable);
    }
    IcebergFullOptimizePlan optimizePlan = new IcebergFullOptimizePlan(icebergNoPartitionTable,
        new TableOptimizeRuntime(icebergNoPartitionTable.id()),
        fileScanTasks, 1, System.currentTimeMillis(),
        icebergNoPartitionTable.asUnkeyedTable().currentSnapshot().snapshotId());
    List<BasicOptimizeTask> tasks = optimizePlan.plan().getOptimizeTasks();
    Assert.assertEquals((int) Math.ceil(1.0 * dataFiles.size() / fragmentRatio), tasks.size());
  }
}

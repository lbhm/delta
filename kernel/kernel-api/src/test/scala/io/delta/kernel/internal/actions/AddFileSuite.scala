/*
 * Copyright (2024) The Delta Lake Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.delta.kernel.internal.actions

import java.lang.{Boolean => JBoolean, Long => JLong}
import java.util.Optional

import scala.collection.JavaConverters._

import io.delta.kernel.data.Row
import io.delta.kernel.internal.util.{StatsUtils, VectorUtils}
import io.delta.kernel.internal.util.VectorUtils.stringStringMapValue

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class AddFileSuite extends AnyFunSuite with Matchers {

  /**
   * Generate a Row representing an AddFile action with provided fields.
   */
  private def generateTestAddFileRow(
      path: String = "path",
      partitionValues: Map[String, String] = Map.empty,
      size: Long = 10L,
      modificationTime: Long = 20L,
      dataChange: Boolean = true,
      deletionVector: Option[DeletionVectorDescriptor] = Option.empty,
      tags: Option[Map[String, String]] = Option.empty,
      baseRowId: Option[Long] = Option.empty,
      defaultRowCommitVersion: Option[Long] = Option.empty,
      stats: Option[String] = Option.empty): Row = {
    def toJavaOptional[T](option: Option[T]): Optional[T] = option match {
      case Some(value) => Optional.of(value)
      case None => Optional.empty()
    }

    AddFile.createAddFileRow(
      null,
      path,
      stringStringMapValue(partitionValues.asJava),
      size.asInstanceOf[JLong],
      modificationTime.asInstanceOf[JLong],
      dataChange.asInstanceOf[JBoolean],
      toJavaOptional(deletionVector),
      toJavaOptional(tags.map(_.asJava).map(stringStringMapValue)),
      toJavaOptional(baseRowId.asInstanceOf[Option[JLong]]),
      toJavaOptional(defaultRowCommitVersion.asInstanceOf[Option[JLong]]),
      StatsUtils.deserializeFromJson(stats.getOrElse("")))
  }

  test("getters can read AddFile's fields from the backing row") {
    val addFileRow = generateTestAddFileRow(
      path = "test/path",
      partitionValues = Map("a" -> "1"),
      size = 1L,
      modificationTime = 10L,
      dataChange = false,
      deletionVector = Option.empty,
      tags = Option(Map("tag1" -> "value1")),
      baseRowId = Option(30L),
      defaultRowCommitVersion = Option(40L),
      stats = Option("{\"numRecords\":100}"))

    val addFile = new AddFile(addFileRow)
    assert(addFile.getPath === "test/path")
    assert(VectorUtils.toJavaMap(addFile.getPartitionValues).asScala.equals(Map("a" -> "1")))
    assert(addFile.getSize === 1L)
    assert(addFile.getModificationTime === 10L)
    assert(addFile.getDataChange === false)
    assert(addFile.getDeletionVector === Optional.empty())
    assert(VectorUtils.toJavaMap(addFile.getTags.get()).asScala.equals(Map("tag1" -> "value1")))
    assert(addFile.getBaseRowId === Optional.of(30L))
    assert(addFile.getDefaultRowCommitVersion === Optional.of(40L))
    // DataFileStatistics doesn't have an equals() override, so we need to compare the string
    assert(addFile.getStats.get().serializeAsJson(null) === "{\"numRecords\":100}")
    assert(addFile.getNumRecords === Optional.of(100L))
  }

  test("update a single field of an AddFile") {
    val addFileRow = generateTestAddFileRow(baseRowId = Option(1L))
    val addFileAction = new AddFile(addFileRow)

    val updatedAddFileAction = addFileAction.withNewBaseRowId(2L)
    assert(updatedAddFileAction.getBaseRowId === Optional.of(2L))

    val updatedAddFileRow = updatedAddFileAction.toRow
    assert(new AddFile(updatedAddFileRow).getBaseRowId === Optional.of(2L))
  }

  test("update multiple fields of an AddFile multiple times") {
    val baseAddFileRow =
      generateTestAddFileRow(
        path = "test/path",
        baseRowId = Option(0L),
        defaultRowCommitVersion = Option(0L))
    var addFileAction = new AddFile(baseAddFileRow)

    (1L until 10L).foreach { i =>
      addFileAction = addFileAction
        .withNewBaseRowId(i)
        .withNewDefaultRowCommitVersion(i * 10)

      assert(addFileAction.getPath === "test/path")
      assert(addFileAction.getBaseRowId === Optional.of(i))
      assert(addFileAction.getDefaultRowCommitVersion === Optional.of(i * 10))
    }
  }

  test("toString() prints all fields of AddFile") {
    Seq(true, false).foreach { dvPresent =>
      val deletionVector = if (dvPresent) {
        Some(new DeletionVectorDescriptor(
          "storage",
          "s",
          Optional.of(1),
          25,
          35))
      } else {
        None
      }

      val addFileRow = generateTestAddFileRow(
        path = "test/path",
        partitionValues = Map("col1" -> "val1"),
        size = 100L,
        modificationTime = 1234L,
        dataChange = false,
        tags = Option(Map("tag1" -> "value1")),
        baseRowId = Option(12345L),
        defaultRowCommitVersion = Option(67890L),
        stats = Option("{\"numRecords\":10000}"),
        deletionVector = deletionVector)

      val addFile = new AddFile(addFileRow)

      val deletionVectorString = if (dvPresent) {
        "Optional[DeletionVectorDescriptor(storageType=storage," +
          " pathOrInlineDv=s, offset=Optional[1], sizeInBytes=25, cardinality=35)]"
      } else {
        "Optional.empty"
      }

      val expectedString = "AddFile{" +
        "path='test/path', " +
        "partitionValues={col1=val1}, " +
        "size=100, " +
        "modificationTime=1234, " +
        "dataChange=false, " +
        s"deletionVector=$deletionVectorString, " +
        "tags=Optional[{tag1=value1}], " +
        "baseRowId=Optional[12345], " +
        "defaultRowCommitVersion=Optional[67890], " +
        "stats={\"numRecords\":10000}}"

      assert(addFile.toString == expectedString)
    }
  }

  test("equals() compares AddFile instances correctly") {
    val addFileRow1 = generateTestAddFileRow(
      path = "test/path",
      size = 100L,
      partitionValues = Map("a" -> "1"),
      baseRowId = Option(12345L),
      stats = Option("{\"numRecords\":100}"))

    // Create an identical AddFile
    val addFileRow2 = generateTestAddFileRow(
      path = "test/path",
      size = 100L,
      partitionValues = Map("a" -> "1"),
      baseRowId = Option(12345L),
      stats = Option("{\"numRecords\":100}"))

    // Create a AddFile with different path
    val addFileRowDiffPath = generateTestAddFileRow(
      path = "different/path",
      size = 100L,
      partitionValues = Map("a" -> "1"),
      baseRowId = Option(12345L),
      stats = Option("{\"numRecords\":100}"))

    // Create a AddFile with different partition values, which is handled specially in equals()
    val addFileRowDiffPartition = generateTestAddFileRow(
      path = "test/path",
      size = 100L,
      partitionValues = Map("x" -> "0"),
      baseRowId = Option(12345L),
      stats = Option("{\"numRecords\":100}"))

    // Create a AddFile with deletion vector value
    val addFileRowDeletionVector = generateTestAddFileRow(
      path = "test/path",
      size = 100L,
      partitionValues = Map("x" -> "0"),
      baseRowId = Option(12345L),
      deletionVector = Some(
        new DeletionVectorDescriptor(
          "storage",
          "s",
          Optional.of(1),
          25,
          35)),
      stats = Option("{\"numRecords\":100}"))

    val addFile1 = new AddFile(addFileRow1)
    val addFile2 = new AddFile(addFileRow2)
    val addFileDiffPath = new AddFile(addFileRowDiffPath)
    val addFileDiffPartition = new AddFile(addFileRowDiffPartition)
    val addFileDeletionVector = new AddFile(addFileRowDeletionVector)

    // Test equality
    assert(addFile1 === addFile2)
    assert(addFile1 != addFileDiffPath)
    assert(addFile1 != addFileDiffPartition)
    assert(addFile2 != addFileDiffPath)
    assert(addFile2 != addFileDiffPartition)
    assert(addFileDiffPath != addFileDiffPartition)
    assert(addFileDeletionVector != addFileDiffPartition)

    // Test null and different type
    assert(!addFile1.equals(null))
    assert(!addFile1.equals(new DomainMetadata("domain", "config", false)))
  }

  test("hashCode is consistent with equals") {
    val addFileRow1 = generateTestAddFileRow(
      path = "test/path",
      size = 100L,
      partitionValues = Map("a" -> "1"),
      baseRowId = Option(12345L),
      stats = Option("{\"numRecords\":100}"))

    val addFileRow2 = generateTestAddFileRow(
      path = "test/path",
      size = 100L,
      partitionValues = Map("a" -> "1"),
      baseRowId = Option(12345L),
      stats = Option("{\"numRecords\":100}"))

    val addFile1 = new AddFile(addFileRow1)
    val addFile2 = new AddFile(addFileRow2)

    // Equal objects should have equal hash codes
    assert(addFile1.hashCode === addFile2.hashCode)

    // Hash code should be consistent across multiple calls
    assert(addFile1.hashCode === addFile1.hashCode)
  }

  // Tests for toRemoveFileRow
  test("toRemoveFileRow: handles AddFile with all required fields") {
    val addFile = new AddFile(generateTestAddFileRow(
      path = "/path/to/file",
      dataChange = false))

    def verify(
        result: RemoveFile,
        expDataChange: Boolean,
        expDeletionTimestamp: Option[Long]): Unit = {
      assert(result.getPath === "/path/to/file")
      if (expDeletionTimestamp.isDefined) {
        assert(result.getDeletionTimestamp.get() === expDeletionTimestamp.get)
      }
      assert(result.getDataChange === expDataChange)
      assert(result.getExtendedFileMetadata === Optional.of(true))
      assert(VectorUtils.toJavaMap[String, String](result.getPartitionValues.get()).asScala ===
        Map.empty[String, String])
      assert(result.getSize === Optional.of(10L))
      assert(result.getStatsJson === Optional.empty())
      assert(result.getTags === Optional.empty())
      assert(result.getBaseRowId === Optional.empty())
      assert(result.getDefaultRowCommitVersion === Optional.empty())
    }

    val result1 = new RemoveFile(addFile.toRemoveFileRow(true, Optional.empty()))
    verify(result1, expDataChange = true, expDeletionTimestamp = None)

    val result2 = new RemoveFile(addFile.toRemoveFileRow(false, Optional.empty()))
    verify(result2, expDataChange = false, expDeletionTimestamp = None)

    val result3 = new RemoveFile(addFile.toRemoveFileRow(true, Optional.of(100L)))
    verify(result3, expDataChange = true, expDeletionTimestamp = Some(100L))
  }

  test("toRemoveFileRow: handles AddFile with optional fields present") {
    val addFile = new AddFile(generateTestAddFileRow(
      path = "/path/to/file",
      partitionValues = Map("a" -> "1"),
      size = 100L,
      modificationTime = 200L,
      dataChange = true,
      deletionVector = None,
      tags = Some(Map("tag1" -> "value1")),
      baseRowId = Some(67890L),
      defaultRowCommitVersion = Some(2823L),
      stats = Some("{\"numRecords\":100}")))

    val result = new RemoveFile(addFile.toRemoveFileRow(false, Optional.of(200L)))

    assert(result.getPath === "/path/to/file")
    assert(VectorUtils.toJavaMap[String, String](result.getPartitionValues.get()).asScala ===
      Map("a" -> "1"))
    assert(result.getSize === Optional.of(100L))
    assert(result.getDeletionTimestamp === Optional.of(200L))
    assert(result.getDataChange === false)
    assert(result.getDeletionVector === Optional.empty())
    assert(VectorUtils.toJavaMap[String, String](result.getTags.get()).asScala ===
      Map[String, String]("tag1" -> "value1"))
    assert(result.getBaseRowId === Optional.of(67890L))
    assert(result.getDefaultRowCommitVersion === Optional.of(2823L))
    assert(result.getStatsJson === Optional.of("{\"numRecords\":100}"))
  }

  test("toRemoveFileRow: DV is converted properly") {
    val addFile = new AddFile(generateTestAddFileRow(
      path = "/path/to/file",
      partitionValues = Map("a" -> "1"),
      size = 100L,
      modificationTime = 200L,
      dataChange = true,
      deletionVector = Some(
        new DeletionVectorDescriptor(
          "storage",
          "s",
          Optional.of(1),
          25,
          35)),
      tags = Some(Map("tag1" -> "value1")),
      baseRowId = Some(67890L),
      defaultRowCommitVersion = Some(2823L),
      stats = Some("{\"numRecords\":100}")))

    val result = new RemoveFile(addFile.toRemoveFileRow(true, Optional.of(200L)))

    assert(result.getPath === "/path/to/file")
    assert(VectorUtils.toJavaMap[String, String](result.getPartitionValues.get()).asScala ===
      Map[String, String]("a" -> "1"))
    assert(result.getSize.get() === 100L)
    assert(result.getDeletionTimestamp.get() === 200L)
    assert(result.getDataChange === true)
    assert(result.getDeletionVector === Optional.of(
      new DeletionVectorDescriptor("storage", "s", Optional.of(1), 25, 35)))
    assert(VectorUtils.toJavaMap[String, String](result.getTags.get()).asScala ===
      Map[String, String]("tag1" -> "value1"))
    assert(result.getBaseRowId === Optional.of(67890L))
    assert(result.getDefaultRowCommitVersion === Optional.of(2823L))
    assert(result.getStatsJson === Optional.of("{\"numRecords\":100}"))

  }
}

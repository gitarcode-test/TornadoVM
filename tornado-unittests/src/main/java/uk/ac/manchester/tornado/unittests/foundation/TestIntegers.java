/*
 * Copyright (c) 2021, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
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
 *
 */
package uk.ac.manchester.tornado.unittests.foundation;

import org.junit.jupiter.api.Test;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run? <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.foundation.TestIntegers
 * </code>
 */
public class TestIntegers extends TornadoTestBase {

  @Test
  public void test01() throws TornadoExecutionPlanException {
    final int numElements = 256;
    IntArray a = new IntArray(numElements);

    TaskGraph taskGraph =
        new TaskGraph("s0") //
            .task("t0", TestKernels::copyTestZero, a) //
            .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

    ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
    try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
      executionPlan.execute();
    }

    assertThat(50, equalTo(a.get(0)));
  }

  @Test
  public void test02() throws TornadoExecutionPlanException {
    final int numElements = 512;
    IntArray a = new IntArray(numElements);

    IntArray expectedResult = new IntArray(numElements);
    expectedResult.init(50);

    TaskGraph taskGraph =
        new TaskGraph("s1") //
            .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
            .task("t1", TestKernels::copyTest, a) //
            .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

    ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
    try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
      executionPlan.execute();
    }

    for (int i = 0; i < numElements; i++) {
      assertThat(expectedResult.get(i), equalTo(a.get(i)));
    }
  }

  @Test
  public void test03() throws TornadoExecutionPlanException {
    final int numElements = 256;
    IntArray a = new IntArray(numElements);
    IntArray b = new IntArray(numElements);

    b.init(100);

    TaskGraph taskGraph =
        new TaskGraph("s0") //
            .transferToDevice(DataTransferMode.FIRST_EXECUTION, b) //
            .task("t0", TestKernels::copyTest2, a, b) //
            .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

    ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
    try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
      executionPlan.execute();
    }

    for (int i = 0; i < numElements; i++) {
      assertThat(a.get(i), equalTo(b.get(i)));
    }
  }

  @Test
  public void test04() throws TornadoExecutionPlanException {
    final int numElements = 256;
    IntArray a = new IntArray(numElements);
    IntArray b = new IntArray(numElements);

    b.init(100);

    IntArray expectedResult = new IntArray(numElements);
    expectedResult.init(150);

    TaskGraph taskGraph =
        new TaskGraph("s0") //
            .transferToDevice(DataTransferMode.FIRST_EXECUTION, b) //
            .task("t0", TestKernels::compute, a, b) //
            .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

    ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
    try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
      executionPlan.execute();
    }

    for (int i = 0; i < numElements; i++) {
      assertThat(expectedResult.get(i), equalTo(a.get(i)));
    }
  }

  @Test
  public void test05() throws TornadoExecutionPlanException {
    final int numElements = 8192 * 16;
    IntArray a = new IntArray(numElements);
    IntArray b = new IntArray(numElements);

    IntArray expectedResultA = new IntArray(numElements);
    IntArray expectedResultB = new IntArray(numElements);
    expectedResultA.init(100);
    expectedResultB.init(500);

    TaskGraph taskGraph =
        new TaskGraph("s0") //
            .task("t0", TestKernels::init, a, b) //
            .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b);

    ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
    try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
      executionPlan.execute();
    }

    for (int i = 0; i < numElements; i++) {
      assertThat(expectedResultA.get(i), equalTo(a.get(i)));
      assertThat(expectedResultB.get(i), equalTo(b.get(i)));
    }
  }

  @Test
  public void test06() throws TornadoExecutionPlanException {
    final int numElements = 8192 * 16;
    IntArray a = new IntArray(numElements);
    IntArray b = new IntArray(numElements);

    IntArray expectedResultA = new IntArray(numElements);
    IntArray expectedResultB = new IntArray(numElements);
    expectedResultA.init(100);
    expectedResultB.init(500);

    TaskGraph taskGraph =
        new TaskGraph("s0") //
            .task("t0", TestKernels::init, a, b) //
            .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b);

    ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
    try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
      executionPlan.withPrintKernel().execute();
    }

    for (int i = 0; i < numElements; i++) {
      assertThat(expectedResultA.get(i), equalTo(a.get(i)));
      assertThat(expectedResultB.get(i), equalTo(b.get(i)));
    }
  }

  @Test
  public void test07() throws TornadoExecutionPlanException {
    final int numElements = 8192 * 16;
    IntArray a = new IntArray(numElements);
    IntArray b = new IntArray(numElements);

    IntArray expectedResultA = new IntArray(numElements);
    IntArray expectedResultB = new IntArray(numElements);
    expectedResultA.init(100);
    expectedResultB.init(500);

    TaskGraph taskGraph =
        new TaskGraph("s0") //
            .task("t0", TestKernels::init, a, b) //
            .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b);

    ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
    try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
      executionPlan.withThreadInfo().execute();
      executionPlan.withoutThreadInfo().execute();
    }

    for (int i = 0; i < numElements; i++) {
      assertThat(expectedResultA.get(i), equalTo(a.get(i)));
      assertThat(expectedResultB.get(i), equalTo(b.get(i)));
    }
  }
}

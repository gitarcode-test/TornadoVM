/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.arrays;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;

/**
 * How to run? <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.arrays.ArrayAddDouble
 * </code>
 */
public class ArrayAddDouble {

  public static void add(DoubleArray a, DoubleArray b, DoubleArray c) {
    for (@Parallel int i = 0; i < c.getSize(); i++) {
      c.set(i, a.get(i) + b.get(i));
    }
  }

  public static void main(String[] args) {

    final int numElements = 8;
    DoubleArray a = new DoubleArray(numElements);
    DoubleArray b = new DoubleArray(numElements);
    DoubleArray c = new DoubleArray(numElements);

    a.init(1);
    b.init(2);
    c.init(0);

    TaskGraph taskGraph =
        new TaskGraph("s0") //
            .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
            .task("t0", ArrayAddDouble::add, a, b, c) //
            .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

    ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
    TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
    executor.execute();

    System.out.println("a: " + a);
    System.out.println("b: " + b);
    System.out.println("c: " + c);
  }
}

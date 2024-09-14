/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.api;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoDeviceMap;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBackendNotFound;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceNotFound;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run?
 *
 * <p><code>
 * $ tornado-test -V uk.ac.manchester.tornado.unittests.api.TestDevices
 * </code>
 */
public class TestDevices extends TornadoTestBase {

  /**
   * We ask, on purpose, for a backend index that does not exist to check that the exception {@link
   * TornadoBackendNotFound} in thrown.
   */
  @Test
  public void test01() {
    assertThrows(
        TornadoBackendNotFound.class,
        () -> {
          TornadoDevice device = TornadoExecutionPlan.getDevice(100, 0);
        });
  }

  /**
   * We ask, on purpose, for a device index that does not exist to check that the exception {@link
   * TornadoDeviceNotFound} in thrown.
   */
  @Test
  public void test02() {
    assertThrows(
        TornadoDeviceNotFound.class,
        () -> {
          TornadoDevice device = TornadoExecutionPlan.getDevice(0, 100);
        });
  }

  /**
   * Test the {@link TornadoDeviceMap} API to obtain the devices without requiring the developer to
   * access through the runtime instance.
   *
   * <p>The goal with this API is to allow developers to apply filters and query the backend and
   * device properties of the desired ones.
   */
  @Test
  public void test03() {

    // Obtains an instance of a class that contains a map with all backends and Devices
    // that the develop can query.
    TornadoDeviceMap tornadoDeviceMap = TornadoExecutionPlan.getTornadoDeviceMap();

    // Query the number of backends
    int numBackends = tornadoDeviceMap.getNumBackends();

    assertThat(numBackends >= 1, is(true));

    // Query all backends
    List<TornadoBackend> backends = tornadoDeviceMap.getAllBackends();

    assertThat(backends.isEmpty(), is(false));

    // Query the number of devices that are accessible per backend
    int numDevicesBackendZero = backends.getFirst().getNumDevices();

    assertThat(numDevicesBackendZero >= 1, is(true));

    // Obtain a reference to a device within the selected backend
    TornadoDevice device = backends.getFirst().getDevice(0);

    assertThat(device, not(nullValue()));
  }

  /**
   * Test to check different examples of how can we apply filters to obtain the desired backends and
   * devices depending on input filters.
   */
  @Test
  public void test04() {

    TornadoDeviceMap tornadoDeviceMap = TornadoExecutionPlan.getTornadoDeviceMap();

    // Query the number of backends
    int numBackends = tornadoDeviceMap.getNumBackends();

    assertThat(numBackends >= 1, is(true));

    List<TornadoBackend> openCLBackend =
        tornadoDeviceMap.getBackendsWithPredicate(
            backend -> backend.getBackendType() == TornadoVMBackendType.OPENCL);

    assertThat(openCLBackend, not(nullValue()));

    // Obtain all backends with at least two devices associated to it
    List<TornadoBackend> multiDeviceBackends =
        tornadoDeviceMap.getBackendsWithPredicate(backend -> backend.getNumDevices() > 1);

    // Obtain the backend that can support SPIR-V as default device
    List<TornadoBackend> spirvSupported =
        tornadoDeviceMap.getBackendsWithPredicate(
            backend -> backend.getDefaultDevice().isSPIRVSupported());

    // Return all backends that can access an NVIDIA GPU
    List<TornadoBackend> backendsWithNVIDIAAccess =
        tornadoDeviceMap.getBackendsWithDevicePredicate(
            device ->
                device //
                    .getDeviceName() //
                    .toLowerCase() //
                    .contains("nvidia"));

    // Another way to perform the previous query
    List<TornadoBackend> backendsWithNVIDIAAccess2 =
        tornadoDeviceMap //
            .getBackendsWithPredicate(
            backend ->
                backend //
                    .getAllDevices() //
                    .stream() //
                    .allMatch(
                        device ->
                            device //
                                .getDeviceName() //
                                .toLowerCase() //
                                .contains("nvidia")));
  }
}

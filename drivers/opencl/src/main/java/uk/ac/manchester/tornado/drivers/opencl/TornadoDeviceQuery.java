/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: Michalis Papadimitriou
 *
 */

package uk.ac.manchester.tornado.drivers.opencl;

import java.util.Arrays;

import uk.ac.manchester.tornado.runtime.TornadoAcceleratorDriver;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;

/**
 * Program to query all devices reachable from TornadoVM.
 * 
 * Run as follows:
 * <p>
 * <code>
 * $ tornado uk.ac.manchester.tornado.drivers.opencl.TornadoDeviceQuery
 * </code>
 * </p>
 *
 */
public class TornadoDeviceQuery {

    private static String formatSize(long v) {
        if (v < 1024) {
            return v + " B";
        }
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double) v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    public static void main(String[] args) {

        String verboseFlag = "";

        if (args.length != 0) {
            verboseFlag = args[0];
        }

        StringBuilder deviceInfoBuffer = new StringBuilder().append("\n");
        final int numDrivers = TornadoCoreRuntime.getTornadoRuntime().getNumDrivers();
        deviceInfoBuffer.append("Number of Tornado drivers: " + numDrivers + "\n");

        for (int driverIndex = 0; driverIndex < numDrivers; driverIndex++) {
            final TornadoAcceleratorDriver driver = TornadoCoreRuntime.getTornadoRuntime().getDriver(driverIndex);
            final int numDevices = driver.getDeviceCount();
            deviceInfoBuffer.append("Total number of devices  : " + numDevices + "\n");
            for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
                deviceInfoBuffer.append("Tornado device=" + driverIndex + ":" + deviceIndex + "\n");
                deviceInfoBuffer.append("\t" + driver.getDevice(deviceIndex)).append("\n");
                if (verboseFlag.equals("verbose")) {
                    deviceInfoBuffer.append("\t\t" + "Global Memory Size: " + formatSize(driver.getDevice(deviceIndex).getMaxGlobalMemory()) + "\n");
                    deviceInfoBuffer.append("\t\t" + "Local Memory Size: " + formatSize(driver.getDevice(deviceIndex).getDeviceLocalMemorySize()) + "\n");
                    deviceInfoBuffer.append("\t\t" + "Workgroup Dimensions: " + driver.getDevice(deviceIndex).getDeviceMaxWorkgroupDimensions().length + "\n");
                    deviceInfoBuffer.append("\t\t" + "Max WorkGroup Configuration: " + Arrays.toString(driver.getDevice(deviceIndex).getDeviceMaxWorkgroupDimensions()) + "\n");
                    deviceInfoBuffer.append("\t\t" + "Device OpenCL C version: " + driver.getDevice(deviceIndex).getDeviceOpenCLCVersion() + "\n");
                }
                deviceInfoBuffer.append("\n");
            }
        }
        System.out.println(deviceInfoBuffer.toString());
    }
}

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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.graph;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.KernelArgs;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.profiler.TimeProfiler;
import uk.ac.manchester.tornado.runtime.tasks.LocalObjectState;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static uk.ac.manchester.tornado.runtime.common.Tornado.info;

public class TornadoExecutionContext {

    private final int MAX_TASKS = 256;
    private final int INITIAL_DEVICE_CAPACITY = 16;
    private int invalidatedCoxtext;
    private final String name;
    private final ScheduleMetaData meta;
    private List<SchedulableTask> tasks;
    private List<Object> constants;
    private Map<Integer, Integer> objectMap;
    private List<Object> objects;
    private List<LocalObjectState> objectState;
    private List<TornadoAcceleratorDevice> devices;
    private final KernelArgs[] callWrappers;
    private int[] taskToDevice;
    private int nextTask;

    private Set<TornadoAcceleratorDevice> lastDevices;

    private boolean redeployOnDevice;
    private boolean defaultScheduler;

    private boolean independentTasks;

    private TornadoProfiler profiler;

    public TornadoExecutionContext(String id, TornadoProfiler profiler) {
        this.invalidatedCoxtext = -1;
        name = id;
        meta = new ScheduleMetaData(name);
        tasks = new ArrayList<>();
        constants = new ArrayList<>();
        objectMap = new HashMap<>();
        objects = new ArrayList<>();
        objectState = new ArrayList<>();
        devices = new ArrayList<>(INITIAL_DEVICE_CAPACITY);
        callWrappers = new KernelArgs[MAX_TASKS];
        taskToDevice = new int[MAX_TASKS];
        Arrays.fill(taskToDevice, -1);
        nextTask = 0;
        lastDevices = new HashSet<>();
        this.profiler = profiler;
        this.independentTasks = areTasksIndependent();
    }

    public KernelArgs[] getCallWrappers() {
        return callWrappers;
    }

    public int insertVariable(Object var) {
        int index = -1;
        if (var.getClass().isPrimitive() || RuntimeUtilities.isBoxedPrimitiveClass(var.getClass())) {
            index = constants.indexOf(var);
            if (index == -1) {
                index = constants.size();
                constants.add(var);
            }
        } else if (objectMap.containsKey(var.hashCode())) {
            index = objectMap.get(var.hashCode());
        } else {
            index = objects.size();
            objects.add(var);
            objectMap.put(var.hashCode(), index);
            objectState.add(index, new LocalObjectState(var));
        }
        return index;
    }

    public int replaceVariable(Object oldObj, Object newObj) {
        /*
         * Use the same index the oldObj was assigned. The argument indices are
         * hardcoded in the TornadoVM bytecodes during bytecode generation and must
         * match the indices in the {@link objectState} and {@link objects} arrays.
         */
        int index;
        if (oldObj.getClass().isPrimitive() || RuntimeUtilities.isBoxedPrimitiveClass(oldObj.getClass())) {
            index = constants.indexOf(oldObj);
            constants.set(index, newObj);
        } else {
            int oldIndex = objectMap.get(oldObj.hashCode());
            LocalObjectState oldLocalObjectState = objectState.remove(oldIndex);
            objectMap.remove(oldObj.hashCode());
            objects.remove(oldIndex);

            /* Copy stream-in/out information to the new local object state */
            LocalObjectState newLocalObjectState = new LocalObjectState(newObj);
            newLocalObjectState.setStreamIn(oldLocalObjectState.isStreamIn());
            newLocalObjectState.setForceStreamIn(oldLocalObjectState.isForcedStreamIn());
            newLocalObjectState.setStreamOut(oldLocalObjectState.isStreamOut());

            index = oldIndex;
            objects.add(index, newObj);
            objectMap.put(newObj.hashCode(), index);
            objectState.add(index, newLocalObjectState);
        }
        return index;
    }

    public int getTaskCount() {
        return nextTask;
    }

    public int getTaskCountAndIncrement() {
        int taskID = nextTask;
        nextTask++;
        return taskID;
    }

    public int addTask(SchedulableTask task) {
        int index = tasks.indexOf(task);
        if (index == -1) {
            index = tasks.size();
            tasks.add(task);
        }
        return index;
    }

    public void setTask(int index, SchedulableTask task) {
        tasks.set(index, task);
    }

    public List<Object> getConstants() {
        return constants;
    }

    public List<Object> getObjects() {
        return objects;
    }

    public int getDeviceIndexForTask(int index) {
        return taskToDevice[index];
    }

    public TornadoAcceleratorDevice getDeviceForTask(int index) {
        return getDevice(taskToDevice[index]);
    }

    public TornadoAcceleratorDevice getDevice(int index) {
        return devices.get(index);
    }

    public SchedulableTask getTask(int index) {
        return tasks.get(index);
    }

    public void apply(Consumer<SchedulableTask> consumer) {
        for (SchedulableTask task : tasks) {
            consumer.accept(task);
        }
    }

    public void mapAllTo(TornadoDevice mapping) {
        if (mapping instanceof TornadoAcceleratorDevice) {
            devices.clear();
            devices.add(0, (TornadoAcceleratorDevice) mapping);
            apply(task -> task.mapTo(mapping));
            Arrays.fill(taskToDevice, 0);
        } else {
            throw new RuntimeException("Device " + mapping.getClass() + " not supported yet");
        }
    }

    private void checkDeviceListSize(int deviceIndex) {
        if (deviceIndex >= devices.size()) {
            for (int i = devices.size(); i <= deviceIndex; i++) {
                devices.add(null);
            }
        }
    }

    // Active table
    // CodeChange
    public void setDevice(int index, TornadoAcceleratorDevice device) {
        checkDeviceListSize(index);
        devices.set(index, device);
    }

    private void assignTask(int index, SchedulableTask task) {
        String id = task.getId();
        TornadoDevice target = task.getDevice();
        TornadoAcceleratorDevice accelerator;

        if (target instanceof TornadoAcceleratorDevice) {
            accelerator = (TornadoAcceleratorDevice) target;
        } else {
            throw new TornadoRuntimeException("Device " + target.getClass() + " not supported yet");
        }

        int deviceIndex = devices.indexOf(target);
        info("assigning %s to %s", id, target.getDeviceName());

        if (deviceIndex == -1) {
            deviceIndex = task.meta().getDeviceIndex();
            setDevice(deviceIndex, accelerator);
        }

        taskToDevice[index] = deviceIndex;
    }

    public void nullContexts(int deviceIndex) {
        for (int i = 0; i < devices.size(); i++) {
            if (i != deviceIndex) {
                devices.set(i, null);
            }

        }
    }

    public void assignToDevices() {
        if (independentTasks) {
            for (int i = 0; i < tasks.size(); i++) {
                assignTask(i, tasks.get(i));
            }
        } else {
            mapAllTo(getDeviceFirstTask());
        }
    }

    public TornadoDevice getDeviceFirstTask() {
        return tasks.get(0).getDevice();
    }

    public LocalObjectState getObjectState(Object object) {
        return objectState.get(insertVariable(object));
    }

    public LocalObjectState replaceObjectState(Object oldObj, Object newObj) {
        return objectState.get(replaceVariable(oldObj, newObj));
    }

    private boolean areTasksIndependent() {
        for (int i = 0; i < tasks.size(); i++) {
            SchedulableTask task = tasks.get(i);
            for (int j = i + 1; j < tasks.size(); j++) {
                SchedulableTask otherTask = tasks.get(j);

                if (!isSameTask(task, otherTask) && haveCommonArguments(task, otherTask)) {
                    if (hasWriteAccess(task, otherTask)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isSameTask(SchedulableTask task1, SchedulableTask task2) {
        return task1.getTaskName().equals(task2.getTaskName()) && task1.getId().equals(task2.getId());
    }

    private boolean haveCommonArguments(SchedulableTask task1, SchedulableTask task2) {
        for (Object arg1 : task1.getArguments()) {
            for (Object arg2 : task2.getArguments()) {
                if (arg1.equals(arg2)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasWriteAccess(SchedulableTask task, SchedulableTask otherTask) {
        for (int i = 0; i < task.getArguments().length; i++) {
            if (task.getArguments()[i].equals(otherTask.getArguments()[i])) {
                Access access = task.getArgumentsAccess()[i];
                if (access == Access.WRITE_ONLY || access == Access.READ_WRITE) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<LocalObjectState> getObjectStates() {
        return objectState;
    }

    public List<SchedulableTask> getTasks() {
        return tasks;
    }

    public List<TornadoAcceleratorDevice> getDevices() {
        return devices;
    }

    public List<SchedulableTask> getTasksForDevice(int deviceIndex) {
        List<SchedulableTask> tasksForDevice = new ArrayList<>();
        for (SchedulableTask task : tasks) {
            if (task.getDevice().getDeviceContext().getDeviceIndex() == deviceIndex) {
                tasksForDevice.add(task);
            }
        }
        return tasksForDevice;
    }

    /**
     * Default device inspects the driver 0 and device 0 of the internal OpenCL list.
     *
     * @return {@link TornadoAcceleratorDevice}
     */
    @Deprecated
    public TornadoAcceleratorDevice getDefaultDevice() {
        return meta.getLogicDevice();
    }

    public SchedulableTask getTask(String id) {
        for (SchedulableTask task : tasks) {
            final String canonicalisedId;
            if (id.startsWith(getId())) {
                canonicalisedId = id;
            } else {
                canonicalisedId = getId() + "." + id;
            }
            if (task.getId().equalsIgnoreCase(canonicalisedId)) {
                return task;
            }
        }
        return null;
    }

    public TornadoAcceleratorDevice getDeviceForTask(String id) {
        TornadoDevice device = getTask(id).getDevice();
        TornadoAcceleratorDevice tornadoDevice = null;
        if (device instanceof TornadoAcceleratorDevice) {
            tornadoDevice = (TornadoAcceleratorDevice) device;
        } else {
            throw new RuntimeException("Device " + device.getClass() + " not supported yet");
        }
        return getTask(id) == null ? null : tornadoDevice;
    }

    public String getId() {
        return name;
    }

    public ScheduleMetaData meta() {
        return meta;
    }

    public void sync() {
        for (int i = 0; i < objects.size(); i++) {
            Object object = objects.get(i);
            if (object != null) {
                final LocalObjectState localState = objectState.get(i);
                Event event = localState.sync(object, meta().getLogicDevice());

                if (TornadoOptions.isProfilerEnabled() && event != null) {
                    long value = profiler.getTimer(ProfilerType.COPY_OUT_TIME_SYNC);
                    value += event.getElapsedTime();
                    profiler.setTimer(ProfilerType.COPY_OUT_TIME_SYNC, value);
                    DeviceObjectState deviceObjectState = localState.getGlobalState().getDeviceState(meta().getLogicDevice());
                    profiler.addValueToMetric(ProfilerType.COPY_OUT_SIZE_BYTES_SYNC, TimeProfiler.NO_TASK_NAME, deviceObjectState.getObjectBuffer().size());
                }
            }
        }
    }

    public void addLastDevice(TornadoAcceleratorDevice device) {
        lastDevices.add(device);
    }

    public Set<TornadoAcceleratorDevice> getLastDevices() {
        return lastDevices;
    }

    public void newCallWrapper(boolean newCallWrapper) {
        this.redeployOnDevice = newCallWrapper;
    }

    public boolean redeployOnDevice() {
        return this.redeployOnDevice;
    }

    public void setDefaultThreadScheduler(boolean use) {
        defaultScheduler = use;
    }

    public boolean useDefaultThreadScheduler() {
        return defaultScheduler;
    }

    public int invalidatedContxtId() {
        return invalidatedCoxtext;
    }

    public void setInvalidContext(int idx) {
        invalidatedCoxtext = idx;
    }

    public void createImmutableExecutionContext(TornadoExecutionContext executionContext) {

        List<SchedulableTask> schedulableTasksCopy = new ArrayList<>(tasks);
        executionContext.tasks = schedulableTasksCopy;

        List<Object> constantCopy = new ArrayList<>(constants);
        executionContext.constants = constantCopy;

        Map<Integer, Integer> objectsMapCopy = new HashMap<>(objectMap);
        executionContext.objectMap = objectsMapCopy;

        List<Object> objectsCopy = new ArrayList<>(objects);
        executionContext.objects = objectsCopy;

        List<LocalObjectState> objectStateCopy = new ArrayList<>(objectState);
        executionContext.objectState = objectStateCopy;

        List<TornadoAcceleratorDevice> devicesCopy = new ArrayList<>(devices);
        executionContext.devices = devicesCopy;

        executionContext.taskToDevice = this.taskToDevice.clone();

        Set<TornadoAcceleratorDevice> lastDeviceCopy = new HashSet<>(lastDevices);
        executionContext.lastDevices = lastDeviceCopy;

        executionContext.profiler = this.profiler;
        executionContext.nextTask = this.nextTask;
    }

    public void dumpExecutionContextMeta() {
        final String ANSI_RESET = "\u001B[0m";
        final String ANSI_CYAN = "\u001B[36m";
        final String ANSI_YELLOW = "\u001B[33m";
        final String ANSI_PURPLE = "\u001B[35m";
        final String ANSI_GREEN = "\u001B[32m";
        System.out.println("-----------------------------------");
        System.out.println(ANSI_CYAN + "Device Table:" + ANSI_RESET);
        for (int i = 0; i < devices.size(); i++) {
            System.out.printf("[%d]: %s\n", i, devices.get(i));
        }

        System.out.println(ANSI_YELLOW + "Constant Table:" + ANSI_RESET);
        for (int i = 0; i < constants.size(); i++) {
            System.out.printf("[%d]: %s\n", i, constants.get(i));
        }

        System.out.println(ANSI_PURPLE + "Object Table:" + ANSI_RESET);
        for (int i = 0; i < objects.size(); i++) {
            final Object obj = objects.get(i);
            System.out.printf("[%d]: 0x%x %s\n", i, obj.hashCode(), obj);
        }

        System.out.println(ANSI_GREEN + "Task Table:" + ANSI_RESET);
        for (int i = 0; i < tasks.size(); i++) {
            final SchedulableTask task = tasks.get(i);
            System.out.printf("[%d]: %s\n", i, task.getFullName());
        }
        System.out.println("-----------------------------------");
    }
}

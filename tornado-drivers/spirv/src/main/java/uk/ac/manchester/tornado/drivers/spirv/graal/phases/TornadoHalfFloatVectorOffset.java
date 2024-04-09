/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.RawConstant;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.Phase;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.SPIRVVectorValueNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.VectorHalfRead;

import java.util.Optional;

/**
 * This compiler phase ensures that the elements of half-float vectors are being
 * accessed using the correct offsets. This is essential because the JavaKind of
 * the half type is declared as Object in order to avoid issues with the Stamp during
 * the sketching.
 */
public class TornadoHalfFloatVectorOffset extends Phase {

    private static int HALF_SIZE = 2;
    private static long HEADER_SIZE = TornadoNativeArray.ARRAY_HEADER;

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    protected void run(StructuredGraph graph) {
        for (ReadNode readNode : graph.getNodes().filter(ReadNode.class)) {
            if (readNode.successors().filter(VectorHalfRead.class).isNotEmpty()) {
                VectorHalfRead vectorHalfRead = readNode.successors().filter(VectorHalfRead.class).first();
                replaceReadOffset(readNode, vectorHalfRead, graph);
                deleteFixed(vectorHalfRead);
            }
        }

        for (WriteNode writeNode : graph.getNodes().filter(WriteNode.class)) {
            if (writeNode.value() instanceof SPIRVVectorValueNode && ((SPIRVVectorValueNode) writeNode.value()).getSPIRVKind().isHalf()) {
                replaceWriteOffset(writeNode, graph);
            }
        }

    }

    private static void replaceReadOffset(ReadNode readNode, VectorHalfRead vectorHalfRead, StructuredGraph graph) {
        OffsetAddressNode ptxAddressNode = (OffsetAddressNode) readNode.getAddress();
        ValueNode index = ptxAddressNode.getOffset();
        if (index instanceof ConstantNode) {
            int vectorReadIndex = vectorHalfRead.getIndex();
            // if the index value has been initialized
            if (vectorReadIndex != -1) {
                Constant shortOffset = new RawConstant(vectorReadIndex * HALF_SIZE + HEADER_SIZE);
                ConstantNode shortOffsetNode = new ConstantNode(shortOffset, StampFactory.forKind(JavaKind.Int));
                graph.addWithoutUnique(shortOffsetNode);
                ptxAddressNode.replaceFirstInput(index, shortOffsetNode);
                if (index.usages().isEmpty()) {
                    index.safeDelete();
                }
            }
        } else if (index.inputs().filter(LeftShiftNode.class).isNotEmpty()) {
            LeftShiftNode leftShiftNode = index.inputs().filter(LeftShiftNode.class).first();
            ConstantNode currentOffset = leftShiftNode.inputs().filter(ConstantNode.class).first();
            // if the shifting is by 3 (for float values)
            if (currentOffset.getValue().toValueString().equals("3")) {
                Constant shortOffset = new RawConstant(1);
                ConstantNode shortOffsetNode = new ConstantNode(shortOffset, StampFactory.forKind(JavaKind.Int));
                graph.addWithoutUnique(shortOffsetNode);
                leftShiftNode.replaceFirstInput(currentOffset, shortOffsetNode);
                if (currentOffset.usages().isEmpty()) {
                    currentOffset.safeDelete();
                }
            }
        }
    }

    private static void replaceWriteOffset(WriteNode writeNode, StructuredGraph graph) {
        OffsetAddressNode ptxAddressNode = (OffsetAddressNode) writeNode.getAddress();
        ValueNode index = ptxAddressNode.getOffset();
        if (index.inputs().filter(LeftShiftNode.class).isNotEmpty()) {
            LeftShiftNode leftShiftNode = index.inputs().filter(LeftShiftNode.class).first();
            ConstantNode currentOffset = leftShiftNode.inputs().filter(ConstantNode.class).first();
            // if the shifting is by 3 (for float values)
            if (currentOffset.getValue().toValueString().equals("3")) {
                Constant shortOffset = new RawConstant(1);
                ConstantNode shortOffsetNode = new ConstantNode(shortOffset, StampFactory.forKind(JavaKind.Int));
                graph.addWithoutUnique(shortOffsetNode);
                leftShiftNode.replaceFirstInput(currentOffset, shortOffsetNode);
                if (currentOffset.usages().isEmpty()) {
                    currentOffset.safeDelete();
                }
            }
        }
    }

    private static void deleteFixed(Node n) {
        Node pred = n.predecessor();
        Node suc = n.successors().first();

        n.replaceFirstSuccessor(suc, null);
        n.replaceAtPredecessor(suc);
        pred.replaceFirstSuccessor(n, suc);

        for (Node us : n.usages()) {
            n.removeUsage(us);
        }
        n.clearInputs();

        n.safeDelete();
    }

}

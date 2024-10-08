/*
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * Authors: Michalis Papadimitriou
 * *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo
public class LocalWorkGroupDimensionsNode extends FloatingNode implements LIRLowerable {

  @Input ConstantNode x;
  @Input ConstantNode y;
  @Input ConstantNode z;

  public static final NodeClass<LocalWorkGroupDimensionsNode> TYPE =
      NodeClass.create(LocalWorkGroupDimensionsNode.class);

  public LocalWorkGroupDimensionsNode(ConstantNode x, ConstantNode y, ConstantNode z) {
    super(TYPE, StampFactory.forKind(JavaKind.Int));
    assert stamp != null;
    this.x = x;
    this.y = y;
    this.z = z;
  }

  @Override
  public void generate(NodeLIRBuilderTool nodeLIRBuilderTool) {}

  public String getXToString() {
    return x.asJavaConstant().toValueString();
  }

  public String getYToString() {
    return y.asJavaConstant().toValueString();
  }

  public String getZToString() {
    return z.asJavaConstant().toValueString();
  }
}

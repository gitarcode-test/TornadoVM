/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.ptx.graal;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.lir.framemap.FrameMap;

public class PTXFrameMap extends FrameMap {
  public PTXFrameMap(
      CodeCacheProvider codeCache,
      RegisterConfig registerConfig,
      ReferenceMapBuilderFactory mapBuilderFactory) {
    super(codeCache, registerConfig, mapBuilderFactory);
  }

  @Override
  public int totalFrameSize() {
    return frameSize() + returnAddressSize();
  }

  @Override
  public int currentFrameSize() {
    return alignFrameSize(outgoingSize + spillSize - returnAddressSize());
  }

  @Override
  protected int alignFrameSize(int size) {
    return NumUtil.roundUp(size + returnAddressSize(), getTarget().stackAlignment)
        - returnAddressSize();
  }
}

/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.api.collections.types;

import static uk.ac.manchester.tornado.api.collections.types.Float3.floor;
import static uk.ac.manchester.tornado.api.collections.types.Float3.fract;
import static uk.ac.manchester.tornado.api.collections.types.Float3.mult;
import static uk.ac.manchester.tornado.api.collections.types.Int3.add;
import static uk.ac.manchester.tornado.api.collections.types.Int3.max;
import static uk.ac.manchester.tornado.api.collections.types.Int3.min;
import static uk.ac.manchester.tornado.api.collections.types.Int3.sub;

public class VolumeOps {

    public static final Float3 grad(final VolumeShort2 volume, final Float3 dim, final Float3 point) {

        final Float3 scaledPos = new Float3(((point.getX() * volume.X()) / dim.getX()) - 0.5f, ((point.getY() * volume.Y()) / dim.getY()) - 0.5f, ((point.getZ() * volume.Z()) / dim.getZ()) - 0.5f);

        final Float3 tmp = floor(scaledPos);

        final Float3 factor = fract(scaledPos);

        final Int3 base = new Int3((int) tmp.getX(), (int) tmp.getY(), (int) tmp.getZ());

        // factor.frac();
        final Int3 zeros = new Int3();
        final Int3 limits = sub(new Int3(volume.X(), volume.Y(), volume.Z()), 1);

        final Int3 lowerLower = max(zeros, sub(base, 1));
        final Int3 lowerUpper = max(zeros, base);
        final Int3 upperLower = min(limits, add(base, 1));
        final Int3 upperUpper = min(limits, add(base, 2));

        final Int3 lower = lowerUpper;
        final Int3 upper = upperLower;

        // formatter:off
        final float gx = ((((((vs(volume, upperLower.getX(), lower.getY(), lower.getZ())) - vs(volume, lowerLower.getX(), lower.getY(), lower.getZ())) * (1 - factor.getX())
                + ((vs(volume, upperUpper.getX(), lower.getY(), lower.getZ())) - vs(volume, lowerUpper.getX(), lower.getY(), lower.getZ())) * factor.getX()) * (1 - factor.getY()))
                + ((((vs(volume, upperLower.getX(), upper.getY(), lower.getZ())) - vs(volume, lowerLower.getX(), upper.getY(), lower.getZ())) * (1 - factor.getX())
                        + ((vs(volume, upperUpper.getX(), upper.getY(), lower.getZ())) - vs(volume, lowerUpper.getX(), upper.getY(), lower.getZ())) * factor.getX()) * factor.getY()))
                * (1 - factor.getZ()))
                + ((((((vs(volume, upperLower.getX(), lower.getY(), upper.getZ()) - vs(volume, lowerLower.getX(), lower.getY(), upper.getZ())) * (1 - factor.getX()))
                        + ((vs(volume, upperUpper.getX(), lower.getY(), upper.getZ()) - vs(volume, lowerUpper.getX(), lower.getY(), upper.getZ())) * factor.getX())) * (1 - factor.getY()))
                        + ((((vs(volume, upperLower.getX(), upper.getY(), upper.getZ()) - vs(volume, lowerLower.getX(), upper.getY(), upper.getZ())) * (1 - factor.getX()))
                                + ((vs(volume, upperUpper.getX(), upper.getY(), upper.getZ()) - vs(volume, lowerUpper.getX(), upper.getY(), upper.getZ())) * factor.getX())) * factor.getY()))
                        * factor.getZ());

        final float gy = ((((((vs(volume, lower.getX(), upperLower.getY(), lower.getZ()) - vs(volume, lower.getX(), lowerLower.getY(), lower.getZ())) * (1 - factor.getX()))
                + ((vs(volume, upper.getX(), upperLower.getY(), lower.getZ()) - vs(volume, upper.getX(), lowerLower.getY(), lower.getZ())) * factor.getX())) * (1 - factor.getY()))
                + ((((vs(volume, lower.getX(), upperUpper.getY(), lower.getZ()) - vs(volume, lower.getX(), lowerUpper.getY(), lower.getZ())) * (1 - factor.getX()))
                        + ((vs(volume, upper.getX(), upperUpper.getY(), lower.getZ()) - vs(volume, upper.getX(), lowerUpper.getY(), lower.getZ())) * factor.getX())) * factor.getY()))
                * (1 - factor.getZ()))
                + ((((((vs(volume, lower.getX(), upperLower.getY(), upper.getZ()) - vs(volume, lower.getX(), lowerLower.getY(), upper.getZ())) * (1 - factor.getX()))
                        + ((vs(volume, upper.getX(), upperLower.getY(), upper.getZ()) - vs(volume, upper.getX(), lowerLower.getY(), upper.getZ())) * factor.getX())) * (1 - factor.getY()))
                        + ((((vs(volume, lower.getX(), upperUpper.getY(), upper.getZ()) - vs(volume, lower.getX(), lowerUpper.getY(), upper.getZ())) * (1 - factor.getX()))
                                + ((vs(volume, upper.getX(), upperUpper.getY(), upper.getZ()) - vs(volume, upper.getX(), lowerUpper.getY(), upper.getZ())) * factor.getX())) * factor.getY()))
                        * factor.getZ());

        final float gz = ((((((vs(volume, lower.getX(), lower.getY(), upperLower.getZ())) - vs(volume, lower.getX(), lower.getY(), lowerLower.getZ())) * (1 - factor.getX()))
                + ((vs(volume, upper.getX(), lower.getY(), upperLower.getZ()) - vs(volume, upper.getX(), lower.getY(), lowerLower.getZ())) * factor.getX())) * (1 - factor.getY()))
                + ((((vs(volume, lower.getX(), upper.getY(), upperLower.getZ()) - vs(volume, lower.getX(), upper.getY(), lowerLower.getZ())) * (1 - factor.getX()))
                        + ((vs(volume, upper.getX(), upper.getY(), upperLower.getZ()) - vs(volume, upper.getX(), upper.getY(), lowerLower.getZ())) * factor.getX())) * factor.getY()))
                * (1 - factor.getZ())
                + ((((((vs(volume, lower.getX(), lower.getY(), upperUpper.getZ()) - vs(volume, lower.getX(), lower.getY(), lowerUpper.getZ())) * (1 - factor.getX()))
                        + ((vs(volume, upper.getX(), lower.getY(), upperUpper.getZ()) - vs(volume, upper.getX(), lower.getY(), lowerUpper.getZ())) * factor.getX())) * (1 - factor.getY()))
                        + ((((vs(volume, lower.getX(), upper.getY(), upperUpper.getZ()) - vs(volume, lower.getX(), upper.getY(), lowerUpper.getZ())) * (1 - factor.getX()))
                                + ((vs(volume, upper.getX(), upper.getY(), upperUpper.getZ()) - vs(volume, upper.getX(), upper.getY(), lowerUpper.getZ())) * factor.getX())) * factor.getY()))
                        * factor.getZ());

        // formatter:on
        final Float3 tmp1 = mult(new Float3(dim.getX() / volume.X(), dim.getY() / volume.Y(), dim.getZ() / volume.Z()), (0.5f * 0.00003051944088f));

        final Float3 gradient = mult(new Float3(gx, gy, gz), tmp1);

        return gradient;

    }

    public static final float interp(final VolumeShort2 volume, final Float3 dim, final Float3 point) {

        final Float3 scaledPos = new Float3((point.getX() * volume.X() / dim.getX()) - 0.5f, (point.getY() * volume.Y() / dim.getY()) - 0.5f, (point.getZ() * volume.Z() / dim.getZ()) - 0.5f);

        final Float3 tmp = floor(scaledPos);
        final Float3 factor = fract(scaledPos);

        final Int3 base = new Int3((int) tmp.getX(), (int) tmp.getY(), (int) tmp.getZ());

        final Int3 zeros = new Int3(0, 0, 0);
        final Int3 limits = sub(new Int3(volume.X(), volume.Y(), volume.Z()), 1);

        final Int3 lower = max(base, zeros);
        final Int3 upper = min(limits, add(base, 1));

        final float factorX = (1 - factor.getX());
        final float factorY = (1 - factor.getY());
        final float factorZ = (1 - factor.getZ());

        final float c00 = (vs(volume, lower.getX(), lower.getY(), lower.getZ()) * factorX) + (vs(volume, upper.getX(), lower.getY(), lower.getZ()) * factor.getX());
        final float c10 = (vs(volume, lower.getX(), upper.getY(), lower.getZ()) * factorX) + (vs(volume, upper.getX(), upper.getY(), lower.getZ()) * factor.getX());

        final float c01 = (vs(volume, lower.getX(), lower.getY(), upper.getZ()) * factorX) + (vs(volume, upper.getX(), lower.getY(), upper.getZ()) * factor.getX());

        final float c11 = (vs(volume, lower.getX(), upper.getY(), upper.getZ()) * factorX) + (vs(volume, upper.getX(), upper.getY(), upper.getZ()) * factor.getX());

        final float c0 = (c00 * factorY) + (c10 * factor.getY());
        final float c1 = (c01 * factorY) + (c11 * factor.getY());

        final float c = (c0 * factorZ) + (c1 * factor.getZ());

        final float result = c * 0.00003051944088f;

        return result;
    }

    public static final float vs1(int x, int y, int z, VolumeShort2 v) {
        return vs(v, x, y, z);
    }

    public final static float vs(final VolumeShort2 cube, int x, int y, int z) {
        return cube.get(x, y, z).getX();
    }

}

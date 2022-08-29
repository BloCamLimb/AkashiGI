/*
 * The Arctic Graphics Engine.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Arctic is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arctic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arctic. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arcticgi.core;

import javax.annotation.Nonnull;

public abstract class MatrixProvider {

    final Matrix4 mLocalToDevice;

    /**
     * Create a matrix provider with an identity matrix.
     */
    public MatrixProvider() {
        this(Matrix4.identity());
    }

    /**
     * Create a matrix provider with the given matrix.
     *
     * @param localToDevice the backing matrix
     */
    public MatrixProvider(final Matrix4 localToDevice) {
        mLocalToDevice = localToDevice;
    }

    /**
     * {@code const Matrix4& localToDevice() const;}
     *
     * @return the backing local-to-device matrix
     */
    @Nonnull
    public final Matrix4 localToDevice() {
        return mLocalToDevice;
    }

    /**
     * {@code virtual bool getLocalToMarker(uint32_t id, Matrix4* localToMarker) const = 0;}
     */
    public abstract boolean getLocalToMarker(int id, Matrix4 localToMarker);
}
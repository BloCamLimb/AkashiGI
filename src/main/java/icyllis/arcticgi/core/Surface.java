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

/**
 * Surface is responsible for managing the pixels that a canvas draws into.
 * The pixels can be allocated on the GPU (a RenderTarget surface).
 * Surface takes care of allocating a Canvas that will draw into the surface.
 * Call {@link #getCanvas()} to use that canvas (it is managed by the surface).
 * Surface always has non-zero dimensions. If there is a request for a new surface,
 * and either of the requested dimensions are zero, then null will be returned.
 */
public abstract class Surface implements AutoCloseable {

    @Override
    public void close() {
    }
}
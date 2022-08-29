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

package icyllis.arcticgi.engine;

import icyllis.arcticgi.core.RefCnt;

/**
 * Base class for operating server resources that may be shared by multiple
 * objects, in particular objects that are tracked by a command buffer.
 * Unlike {@link GpuResource}, these resources will not have a large memory
 * allocation, but a set of constant states instead. When an existing owner
 * wants to share a reference, it calls {@link #ref()}. When an owner wants
 * to release its reference, it calls {@link #unref()}. When the shared
 * object's reference count goes to zero as the result of an {@link #unref()}
 * call, its {@link #onFree()} is called. It is an error for the destructor
 * to be called explicitly (or via the object going out of scope on the
 * stack or calling {@link #onFree()}) if {@link #getRefCnt()} > 1.
 */
public abstract class ManagedResource extends RefCnt {

    private final Server mServer;

    public ManagedResource(Server server) {
        mServer = server;
    }

    /**
     * @return the server
     */
    protected Server getServer() {
        return mServer;
    }
}
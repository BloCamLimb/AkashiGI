/*
 * Akashi GI.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Akashi GI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Akashi GI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Akashi GI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.akashigi.engine;

import icyllis.akashigi.core.*;
import org.lwjgl.system.NativeType;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import static icyllis.akashigi.engine.Engine.BackendApi;

/**
 * Describes the backend texture format, immutable.
 */
@Immutable
public abstract class BackendFormat {

    /**
     * @see BackendApi#kOpenGL
     * @see BackendApi#kVulkan
     * @see BackendApi#kMock
     */
    public abstract int getBackend();

    /**
     * If the backend API is Open GL this gets the format as a GLenum.
     */
    @NativeType("GLenum")
    public int getGLFormat() {
        throw new IllegalStateException();
    }

    /**
     * If the backend API is Vulkan this gets the format as a VkFormat.
     */
    @NativeType("VkFormat")
    public int getVkFormat() {
        throw new IllegalStateException();
    }

    /**
     * Gets the channels present in the format as a bitfield of ColorChannelFlag values.
     *
     * @see Color#COLOR_CHANNEL_FLAGS_RGBA
     */
    public abstract int getChannelFlags();

    public abstract boolean isSRGB();

    /**
     * Hints that a texture comes from external resources, and our engine cannot create such a texture
     * with this format. It will be read-only (can be read/sampled from but cannot be written/rendered to).
     */
    public abstract boolean isExternal();

    /**
     * @see Core.CompressionType#kNone
     */
    public abstract int getCompressionType();

    public final boolean isCompressed() {
        return getCompressionType() != Core.CompressionType.kNone;
    }

    /**
     * If possible, copies the BackendFormat and forces the isExternal to false.
     */
    @Nonnull
    public abstract BackendFormat makeInternal();

    /**
     * @return if compressed, bytes per block, otherwise bytes per pixel
     */
    public abstract int getBytesPerBlock();

    public abstract int getStencilBits();

    /**
     * @return a key that is unique in the backend
     */
    public abstract int getFormatKey();
}
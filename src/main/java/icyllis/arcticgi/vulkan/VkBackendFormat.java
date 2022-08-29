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

package icyllis.arcticgi.vulkan;

import icyllis.arcticgi.engine.BackendFormat;
import icyllis.arcticgi.engine.EngineTypes;
import org.lwjgl.system.NativeType;

import javax.annotation.Nonnull;

import static icyllis.arcticgi.vulkan.VkCore.*;

public final class VkBackendFormat extends BackendFormat {

    private final int mFormat;
    private final int mTextureType;

    /**
     * @see #makeVk(int, boolean)
     */
    public VkBackendFormat(@NativeType("VkFormat") int format, boolean isExternal) {
        mFormat = format;
        mTextureType = isExternal ? EngineTypes.TextureType_External : EngineTypes.TextureType_2D;
    }

    @Override
    public int backend() {
        return EngineTypes.Vulkan;
    }

    @Override
    public int textureType() {
        return mTextureType;
    }

    @Override
    public int getChannelMask() {
        return vkFormatChannels(mFormat);
    }

    @Override
    public int getVkFormat() {
        return mFormat;
    }

    @Nonnull
    @Override
    public BackendFormat makeTexture2D() {
        if (mTextureType == EngineTypes.TextureType_2D) {
            return this;
        }
        return makeVk(mFormat, false);
    }

    @Override
    public boolean isSRGB() {
        return mFormat == VK_FORMAT_R8G8B8A8_SRGB;
    }

    @Override
    public int getCompressionType() {
        return vkFormatCompressionType(mFormat);
    }

    @Override
    public int getBytesPerBlock() {
        return vkFormatBytesPerBlock(mFormat);
    }

    @Override
    public int getStencilBits() {
        return vkFormatStencilBits(mFormat);
    }

    @Override
    public int getFormatKey() {
        return mFormat;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return mFormat == ((VkBackendFormat) o).mFormat;
    }

    @Override
    public int hashCode() {
        return mFormat;
    }
}
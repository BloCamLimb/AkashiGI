/*
 * This file is part of Arc 3D.
 *
 * Copyright (C) 2022-2023 BloCamLimb <pocamelards@gmail.com>
 *
 * Arc 3D is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc 3D is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc 3D. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arc3d.engine;

import icyllis.arc3d.core.*;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * This class is a public API, except where noted.
 */
public abstract class Context extends RefCnt {

    protected final SharedContext mContextInfo;

    protected Context(SharedContext contextInfo) {
        mContextInfo = contextInfo;
    }

    /**
     * The 3D API backing this context.
     *
     * @return see {@link GpuDevice.BackendApi}
     */
    public final int getBackend() {
        return mContextInfo.getBackend();
    }

    /**
     * Retrieve the default {@link BackendFormat} for a given {@code ColorType} and renderability.
     * It is guaranteed that this backend format will be the one used by the following
     * {@code ColorType} and {@link SurfaceCharacterization#createBackendFormat(int, BackendFormat)}.
     * <p>
     * The caller should check that the returned format is valid (nullability).
     *
     * @param colorType  see {@link ImageInfo}
     * @param renderable true if the format will be used as color attachments
     */
    @Nullable
    public final BackendFormat getDefaultBackendFormat(int colorType, boolean renderable) {
        return mContextInfo.getDefaultBackendFormat(colorType, renderable);
    }

    /**
     * Retrieve the {@link BackendFormat} for a given {@code CompressionType}. This is
     * guaranteed to match the backend format used by the following
     * createCompressedBackendTexture methods that take a {@code CompressionType}.
     * <p>
     * The caller should check that the returned format is valid (nullability).
     *
     * @param compressionType see {@link ImageInfo}
     */
    @Nullable
    public final BackendFormat getCompressedBackendFormat(int compressionType) {
        return mContextInfo.getCompressedBackendFormat(compressionType);
    }

    /**
     * Gets the maximum supported sample count for a color type. 1 is returned if only non-MSAA
     * rendering is supported for the color type. 0 is returned if rendering to this color type
     * is not supported at all.
     *
     * @param colorType see {@link ImageInfo}
     */
    public final int getMaxSurfaceSampleCount(int colorType) {
        return mContextInfo.getMaxSurfaceSampleCount(colorType);
    }

    public final SharedContext getContextInfo() {
        return mContextInfo;
    }

    @ApiStatus.Internal
    public final boolean matches(Context c) {
        return mContextInfo.matches(c);
    }

    @ApiStatus.Internal
    public final ContextOptions getOptions() {
        return mContextInfo.getOptions();
    }

    /**
     * An identifier for this context. The id is used by all compatible contexts. For example,
     * if Images are created on one thread using an image creation context, then fed into a
     * Recorder on second thread (which has a recording context) and finally replayed on
     * a third thread with a direct context, then all three contexts will report the same id.
     * It is an error for an image to be used with contexts that report different ids.
     */
    @ApiStatus.Internal
    public final int getContextID() {
        return mContextInfo.getContextID();
    }

    @ApiStatus.Internal
    public final Caps getCaps() {
        return mContextInfo.getCaps();
    }

    public final Logger getLogger() {
        return Objects.requireNonNullElse(getOptions().mLogger, NOPLogger.NOP_LOGGER);
    }

    protected boolean init() {
        return mContextInfo.isValid();
    }
}

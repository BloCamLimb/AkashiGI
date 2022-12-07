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

import static icyllis.akashigi.engine.Engine.*;

/**
 * The drawing device is backed by GPU.
 */
public final class Device extends BaseDevice {

    private Device(SurfaceDrawContext context, ImageInfo info, boolean clear) {
        super(info);
    }

    @SharedPtr
    private static Device make(SurfaceDrawContext sdc,
                               int alphaType,
                               boolean clear) {
        if (sdc == null) {
            return null;
        }
        if (alphaType != Core.AlphaType.kPremul && alphaType != Core.AlphaType.kOpaque) {
            return null;
        }
        RecordingContext rContext = sdc.getContext();
        if (rContext.isDiscarded()) {
            return null;
        }
        int colorType = ColorType.toPublic(sdc.getColorType());
        if (rContext.isSurfaceCompatible(colorType)) {
            ImageInfo info = new ImageInfo(sdc.getWidth(), sdc.getHeight(), colorType, alphaType);
            return new Device(sdc, info, clear);
        }
        return null;
    }

    @SharedPtr
    public static Device make(RecordingContext rContext,
                              int colorType,
                              int alphaType,
                              int width, int height,
                              int sampleCount,
                              int surfaceFlags,
                              int origin,
                              boolean clear) {
        if (rContext == null) {
            return null;
        }
        SurfaceDrawContext sdc = SurfaceDrawContext.make(rContext,
                colorType, width, height, sampleCount, surfaceFlags, origin);
        return make(sdc, alphaType, clear);
    }

    @Override
    public boolean clipIsAA() {
        return false;
    }

    @Override
    public boolean clipIsWideOpen() {
        return false;
    }

    @Override
    protected int getClipType() {
        return 0;
    }

    @Override
    protected Rect2i getClipBounds() {
        return null;
    }

    @Override
    protected void drawPaint(Paint paint) {

    }
}

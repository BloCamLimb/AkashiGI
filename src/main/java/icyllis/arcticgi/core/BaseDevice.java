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

import icyllis.arcticgi.engine.RecordingContext;
import icyllis.arcticgi.engine.SurfaceDrawContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Base class for drawing devices.
 */
public abstract class BaseDevice extends MatrixProvider {

    protected static final int
            CLIP_TYPE_EMPTY = 0,
            CLIP_TYPE_RECT = 1,
            CLIP_TYPE_COMPLEX = 2;

    final ImageInfo mInfo;
    final Rect mBounds = new Rect();

    // mDeviceToGlobal and mGlobalToDevice are inverses of each other
    final Matrix4 mDeviceToGlobal = Matrix4.identity();
    final Matrix4 mGlobalToDevice = Matrix4.identity();

    public BaseDevice(ImageInfo info) {
        mInfo = info;
        mBounds.set(0, 0, info.width(), info.height());
    }

    /**
     * Internal resize for optimization purposes.
     */
    void resize(int width, int height) {
        mInfo.resize(width, height);
        mBounds.set(0, 0, width, height);
    }

    /**
     * Return image info for this device.
     */
    @Nonnull
    public final ImageInfo getImageInfo() {
        return mInfo;
    }

    public final int getWidth() {
        return mInfo.width();
    }

    public final int getHeight() {
        return mInfo.height();
    }

    /**
     * @return read-only
     */
    public final Rect getBounds() {
        return mBounds;
    }

    /**
     * Return the bounds of the device in the coordinate space of this device.
     */
    public final void getBounds(@Nonnull Rect bounds) {
        bounds.set(getBounds());
    }

    /**
     * Return the bounds of the device in the coordinate space of the root
     * canvas. The root device will have its top-left at 0,0, but other devices
     * such as those associated with saveLayer may have a non-zero origin.
     */
    public final void getGlobalBounds(@Nonnull Rect bounds) {
        mDeviceToGlobal.mapRectOut(getBounds(), bounds);
    }

    /**
     * Returns the bounding box of the current clip, in this device's
     * coordinate space. No pixels outside these bounds will be touched by
     * draws unless the clip is further modified (at which point this will
     * return the updated bounds).
     */
    public final void getClipBounds(@Nonnull Rect bounds) {
        bounds.set(getClipBounds());
    }

    /**
     * Return the device's coordinate space transform: this maps from the device's coordinate space
     * into the global canvas' space (or root device space). This includes the translation
     * necessary to account for the device's origin.
     */
    public final Matrix4 getDeviceToGlobal() {
        return mDeviceToGlobal;
    }

    /**
     * Return the inverse of getDeviceToGlobal(), mapping from the global canvas' space (or root
     * device space) into this device's coordinate space.
     */
    public final Matrix4 getGlobalToDevice() {
        return mGlobalToDevice;
    }

    /**
     * Returns true when this device's pixel grid is axis aligned with the global coordinate space,
     * and any relative translation between the two spaces is in integer pixel units.
     */
    public final boolean isPixelAlignedToGlobal() {
        Matrix4 mat = mDeviceToGlobal;
        return mat.m11 == 1 && mat.m12 == 0 && mat.m13 == 0 && mat.m14 == 0 &&
                mat.m21 == 0 && mat.m22 == 1 && mat.m23 == 0 && mat.m24 == 0 &&
                mat.m31 == 0 && mat.m32 == 0 && mat.m33 == 1 && mat.m34 == 0 &&
                mat.m41 == Math.floor(mat.m41) && mat.m42 == Math.floor(mat.m42) && mat.m43 == 0 && mat.m44 == 1;
    }

    /**
     * Get the transformation from this device's coordinate system to the provided device space.
     * This transform can be used to draw this device into the provided device, such that once
     * that device is drawn to the root device, the net effect will be that this device's contents
     * have been transformed by the global transform.
     */
    public final void getRelativeTransform(@Nonnull BaseDevice device, @Nonnull Matrix4 dest) {
        // To get the transform from this space to the other device's, transform from our space to
        // global and then from global to the other device.
        dest.set(mDeviceToGlobal);
        dest.postMultiply(device.mGlobalToDevice);
    }

    public final void save() {
        onSave();
    }

    public final void restore(Matrix4 globalTransform) {
        onRestore();
        setGlobalTransform(globalTransform);
    }

    public final void restoreLocal(Matrix4 localToDevice) {
        onRestore();
        setLocalToDevice(localToDevice);
    }

    public void clipRect(RectF rect, int clipOp, boolean doAA) {
    }

    public final void replaceClip(Rect rect) {
        onReplaceClip(rect);
    }

    protected void onReplaceClip(Rect rect) {
    }

    public abstract boolean clipIsAA();

    public abstract boolean clipIsWideOpen();

    public final void setGlobalTransform(@Nullable Matrix4 globalTransform) {
        if (globalTransform == null) {
            mLocalToDevice.setIdentity();
        } else {
            mLocalToDevice.set(globalTransform);
            mLocalToDevice.normalizePerspective();
        }
        // Map from the global transform state to this device's coordinate system.
        mLocalToDevice.postMultiply(mGlobalToDevice);
    }

    public final void setLocalToDevice(@Nullable Matrix4 localToDevice) {
        if (localToDevice == null) {
            mLocalToDevice.setIdentity();
        } else {
            mLocalToDevice.set(localToDevice);
        }
    }

    @Nullable
    public RecordingContext getRecordingContext() {
        return null;
    }

    @Nullable
    public SurfaceDrawContext getSurfaceDrawContext() {
        return null;
    }

    /**
     * Configure the device's coordinate spaces, specifying both how its device image maps back to
     * the global space (via 'deviceToGlobal') and the initial transform of the device (via
     * 'localToDevice', i.e. what geometry drawn into this device will be transformed with).
     * <p>
     * (bufferOriginX, bufferOriginY) defines where the (0,0) pixel the device's backing buffer
     * is anchored in the device space. The final device-to-global matrix stored by the Device
     * will include a pre-translation by T(deviceOriginX, deviceOriginY), and the final
     * local-to-device matrix will have a post-translation of T(-deviceOriginX, -deviceOriginY).
     */
    final void setCoordinateSystem(@Nullable Matrix4 deviceToGlobal,
                                   @Nullable Matrix4 globalToDevice,
                                   @Nullable Matrix4 localToDevice,
                                   int bufferOriginX,
                                   int bufferOriginY) {
        if (deviceToGlobal == null) {
            mDeviceToGlobal.setIdentity();
            mGlobalToDevice.setIdentity();
        } else {
            assert (globalToDevice != null);
            mDeviceToGlobal.set(deviceToGlobal);
            mDeviceToGlobal.normalizePerspective();
            mGlobalToDevice.set(globalToDevice);
            mGlobalToDevice.normalizePerspective();
        }
        if (localToDevice == null) {
            mLocalToDevice.setIdentity();
        } else {
            mLocalToDevice.set(localToDevice);
            mLocalToDevice.normalizePerspective();
        }
        if ((bufferOriginX | bufferOriginY) != 0) {
            mDeviceToGlobal.preTranslate(bufferOriginX, bufferOriginY);
            mGlobalToDevice.postTranslate(-bufferOriginX, -bufferOriginY);
            mLocalToDevice.postTranslate(-bufferOriginX, -bufferOriginY);
        }
    }

    /**
     * Convenience to configure the device to be axis-aligned with the root canvas, but with a
     * unique origin.
     */
    final void setOrigin(@Nullable Matrix4 globalTransform, int x, int y) {
        setCoordinateSystem(null, null, globalTransform, x, y);
    }

    protected void onSave() {
    }

    protected void onRestore() {
    }

    protected abstract int getClipType();

    protected abstract Rect getClipBounds();

    /**
     * These are called inside the per-device-layer loop for each draw call.
     * When these are called, we have already applied any saveLayer operations,
     * and are handling any looping from the paint.
     */
    protected abstract void drawPaint(Paint paint);

    @Nullable
    protected Surface makeSurface(ImageInfo info) {
        return null;
    }

    /**
     * Create a new device based on CreateInfo. If the paint is not null, then it represents a
     * preview of how the new device will be composed with its creator device (this).
     * <p>
     * The subclass may be handed this device in drawDevice(), so it must always return
     * a device that it knows how to draw, and that it knows how to identify if it is not of the
     * same subclass (since drawDevice is passed a BaseDevice). If the subclass cannot fulfill
     * that contract it should return null.
     */
    @Nullable
    protected BaseDevice createDevice(ImageInfo info, @Nullable Paint paint) {
        return null;
    }
}

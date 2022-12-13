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

import javax.annotation.Nonnull;

/**
 * This class is used to generate a generic pipeline cache key. The Vulkan backend
 * derive backend-specific versions which add additional information.
 */
public final class PipelineDesc extends KeyBuilder {

    private int mBaseLength;

    public PipelineDesc() {
    }

    /**
     * @return the number of ints of the base key, without additional information
     */
    public int getBaseLength() {
        return mBaseLength;
    }

    /**
     * Builds a base pipeline descriptor, without additional information.
     *
     * @param desc the pipeline descriptor
     * @param info the pipeline information
     * @param caps the context capabilities
     */
    @Nonnull
    public static PipelineDesc build(PipelineDesc desc, PipelineInfo info, Caps caps) {
        desc.reset();
        genKey(desc, info, caps);
        desc.mBaseLength = desc.length();
        return desc;
    }

    public static String describe(PipelineInfo info, Caps caps) {
        StringKeyBuilder b = new StringKeyBuilder();
        genKey(b, info, caps);
        return b.toString();
    }

    static void genKey(KeyBuilder b,
                       PipelineInfo info,
                       Caps caps) {
        genGPKey(info.geomProc(), b);

        //TODO more keys

        b.addBits(16, info.writeSwizzle(), "writeSwizzle");

        // Put a clean break between the "common" data written by this function, and any backend data
        // appended later. The initial key length will just be this portion (rounded to 4 bytes).
        b.flush();
    }

    /**
     * Functions which emit processor key info into the key builder.
     * For every effect, we include the effect's class ID (different for every GrProcessor subclass),
     * any information generated by the effect itself (addToKey), and some meta-information.
     * Shader code may be dependent on properties of the effect not placed in the key by the effect
     * (e.g. pixel format of textures used).
     */
    static void genGPKey(GeometryProcessor geomProc, KeyBuilder b) {
        b.appendComment(geomProc.name());
        // Currently we allow 8 bits for the class id
        b.addBits(8, geomProc.classID(), "gpClassID");

        geomProc.addToKey(b);
        geomProc.getAttributeKey(b);

        int numSamplers = geomProc.numTextureSamplers();
        b.addBits(4, numSamplers, "gpNumSamplers");
        for (int i = 0; i < numSamplers; i++) {
            b.addBits(16, geomProc.textureSamplerSwizzle(i), "swizzle");
        }
    }
}
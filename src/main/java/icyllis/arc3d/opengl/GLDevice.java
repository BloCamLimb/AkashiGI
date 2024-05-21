/*
 * This file is part of Arc 3D.
 *
 * Copyright (C) 2022-2024 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.opengl;

import icyllis.arc3d.core.*;
import icyllis.arc3d.engine.*;
import icyllis.arc3d.engine.Device;
import icyllis.arc3d.engine.Image;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import static icyllis.arc3d.opengl.GLCore.*;
import static org.lwjgl.system.MemoryUtil.memPutInt;

/**
 * The OpenGL device.
 */
public final class GLDevice extends Device {

    private final GLCaps mCaps;
    private final GLInterface mGLInterface;

    private final GLCommandBuffer mMainCmdBuffer;

    private final GLResourceProvider mResourceProvider;
    private final GLPipelineCache mPipelineCache;

    private final CpuBufferPool mCpuBufferPool;

    private final GpuBufferPool mVertexPool;
    private final GpuBufferPool mInstancePool;
    private final GpuBufferPool mIndexPool;

    // unique ptr
    private GLOpsRenderPass mCachedOpsRenderPass;

    private final ArrayDeque<FlushInfo.FinishedCallback> mFinishedCallbacks = new ArrayDeque<>();
    private final LongArrayFIFOQueue mFinishedFences = new LongArrayFIFOQueue();

    /**
     * Represents a certain resource ID is bound, but no {@link Resource} object is associated with.
     */
    // OpenGL 3 only.
    static final UniqueID INVALID_UNIQUE_ID = new UniqueID();

    //@formatter:off
    static final int BUFFER_TYPE_VERTEX         = 0;
    static final int BUFFER_TYPE_INDEX          = 1;
    static final int BUFFER_TYPE_XFER_SRC       = 2;
    static final int BUFFER_TYPE_XFER_DST       = 3;
    static final int BUFFER_TYPE_UNIFORM        = 4;
    static final int BUFFER_TYPE_DRAW_INDIRECT  = 5;

    static int bufferUsageToType(int usage) {
        // __builtin_ctz
        return Integer.numberOfTrailingZeros(usage);
    }

    static {
        assert BUFFER_TYPE_VERTEX           ==
                bufferUsageToType(BufferUsageFlags.kVertex);
        assert BUFFER_TYPE_INDEX            ==
                bufferUsageToType(BufferUsageFlags.kIndex);
        assert BUFFER_TYPE_XFER_SRC         ==
                bufferUsageToType(BufferUsageFlags.kTransferSrc);
        assert BUFFER_TYPE_XFER_DST         ==
                bufferUsageToType(BufferUsageFlags.kTransferDst);
        assert BUFFER_TYPE_UNIFORM          ==
                bufferUsageToType(BufferUsageFlags.kUniform);
        assert BUFFER_TYPE_DRAW_INDIRECT    ==
                bufferUsageToType(BufferUsageFlags.kDrawIndirect);
    }
    //@formatter:on

    static final class HWBufferState {
        final int mTarget;
        UniqueID mBoundBufferUniqueID;

        HWBufferState(int target) {
            mTarget = target;
        }
    }

    // context's buffer binding state
    private final HWBufferState[] mHWBufferStates = new HWBufferState[6];

    //@formatter:off
    {
        mHWBufferStates[BUFFER_TYPE_VERTEX]         =
                new HWBufferState(GL_ARRAY_BUFFER);
        mHWBufferStates[BUFFER_TYPE_INDEX]          =
                new HWBufferState(GL_ELEMENT_ARRAY_BUFFER);
        mHWBufferStates[BUFFER_TYPE_XFER_SRC]       =
                new HWBufferState(GL_PIXEL_UNPACK_BUFFER);
        mHWBufferStates[BUFFER_TYPE_XFER_DST]       =
                new HWBufferState(GL_PIXEL_PACK_BUFFER);
        mHWBufferStates[BUFFER_TYPE_UNIFORM]        =
                new HWBufferState(GL_UNIFORM_BUFFER);
        mHWBufferStates[BUFFER_TYPE_DRAW_INDIRECT]  =
                new HWBufferState(GL_DRAW_INDIRECT_BUFFER);
    }
    //@formatter:on

    /**
     * We have four methods to allocate UBOs.
     * <ol>
     * <li>sub allocate persistently mapped ring buffer (OpenGL 4.4+).</li>
     * <li>one uniform buffer per block, multiple BufferSubData in one frame.</li>
     * <li>managed a pool of uniform buffers, use one per draw call, triple buffering.</li>
     * <li>use uniform arrays like push constants (e.g. vec4[10] uboData;).</li>
     * </ol>
     * <p>
     * This is used in case 2 and 3.
     * <p>
     * For case 2, any block <= 128 bytes using binding 0, others using binding 1.
     * Each program has only one block (update per draw call).
     */
    private final UniqueID[] mBoundUniformBuffers = new UniqueID[4];

    // Below OpenGL 4.5.
    private int mHWActiveTextureUnit;

    // target is Texture2D
    private final UniqueID[] mHWTextureStates;

    static final class HWSamplerState {
        // default to invalid, we use 0 because it's not a valid sampler state
        int mSamplerState = 0;
        @SharedPtr
        GLSampler mBoundSampler = null;
    }

    private final HWSamplerState[] mHWSamplerStates;

    /**
     * Framebuffer used for pixel transfer operations, compatibility only.
     * Lazily init.
     */
    private int mCopySrcFramebuffer = 0;
    private int mCopyDstFramebuffer = 0;

    private boolean mNeedsFlush;

    private final ConcurrentLinkedQueue<Consumer<GLDevice>> mRenderCalls =
            new ConcurrentLinkedQueue<>();

    private GLDevice(ImmediateContext context, GLCaps caps, GLInterface glInterface) {
        super(context, caps);
        mCaps = caps;
        mGLInterface = glInterface;
        mMainCmdBuffer = new GLCommandBuffer(this);
        mResourceProvider = new GLResourceProvider(this, context);
        mPipelineCache = new GLPipelineCache(this, 256);
        mCpuBufferPool = new CpuBufferPool(6);
        mVertexPool = GpuBufferPool.makeVertexPool(mResourceProvider);
        mInstancePool = GpuBufferPool.makeInstancePool(mResourceProvider);
        mIndexPool = GpuBufferPool.makeIndexPool(mResourceProvider);

        int maxTextureUnits = caps.shaderCaps().mMaxFragmentSamplers;
        mHWTextureStates = new UniqueID[maxTextureUnits];
        mHWSamplerStates = new HWSamplerState[maxTextureUnits];
        for (int i = 0; i < maxTextureUnits; i++) {
            mHWSamplerStates[i] = new HWSamplerState();
        }
    }

    /**
     * Create a {@link GLDevice} with OpenGL context current in the current thread.
     *
     * @param context the owner context
     * @param options the context options
     * @return the engine or null if failed to create
     */
    @Nullable
    public static GLDevice make(ImmediateContext context, ContextOptions options,
                                Object capabilities) {
        try {
            final GLCaps caps;
            final GLInterface glInterface;
            switch (capabilities.getClass().getName()) {
                case "org.lwjgl.opengl.GLCapabilities" -> {
                    var impl = new GLCaps_GL(context, options, capabilities);
                    caps = impl;
                    glInterface = impl;
                }
                case "org.lwjgl.opengles.GLESCapabilities" -> {
                    var impl = new GLCaps_GLES(context, options, capabilities);
                    caps = impl;
                    glInterface = impl;
                }
                default -> {
                    context.getLogger().error("Failed to create GLDevice: invalid capabilities");
                    return null;
                }
            }
            return new GLDevice(context, caps, glInterface);
        } catch (Exception e) {
            context.getLogger().error("Failed to create GLDevice", e);
            return null;
        }
    }

    public boolean isOnExecutingThread() {
        return mContext.isOwnerThread();
    }

    /**
     * OpenGL only method.
     */
    public void executeRenderCall(Consumer<GLDevice> renderCall) {
        if (isOnExecutingThread()) {
            renderCall.accept(this);
        } else {
            recordRenderCall(renderCall);
        }
    }

    public void recordRenderCall(Consumer<GLDevice> renderCall) {
        mRenderCalls.add(renderCall);
    }

    void flushRenderCalls(GLDevice device) {
        //noinspection UnnecessaryLocalVariable
        final var queue = mRenderCalls;
        Consumer<GLDevice> r;
        while ((r = queue.poll()) != null) r.accept(device);
    }

    @Override
    public GLCaps getCaps() {
        return mCaps;
    }

    public GLInterface getGL() {
        return mGLInterface;
    }

    @Override
    public void disconnect(boolean cleanup) {
        super.disconnect(cleanup);
        mVertexPool.reset();
        mInstancePool.reset();
        mIndexPool.reset();
        mCpuBufferPool.releaseAll();

        mMainCmdBuffer.resetStates(~0);

        if (cleanup) {
            mPipelineCache.release();
            mResourceProvider.release();
        } else {
            mPipelineCache.discard();
            mResourceProvider.discard();
        }

        callAllFinishedCallbacks(cleanup);
    }

    @Override
    protected void handleDirtyContext(int state) {
        super.handleDirtyContext(state);
    }

    public GLCommandBuffer currentCommandBuffer() {
        return mMainCmdBuffer;
    }

    @Override
    public GLResourceProvider getResourceProvider() {
        return mResourceProvider;
    }

    @Override
    public GLPipelineCache getPipelineCache() {
        return mPipelineCache;
    }

    /**
     * As staging buffers.
     */
    public CpuBufferPool getCpuBufferPool() {
        return mCpuBufferPool;
    }

    @Override
    public GpuBufferPool getVertexPool() {
        return mVertexPool;
    }

    @Override
    public GpuBufferPool getInstancePool() {
        return mInstancePool;
    }

    @Override
    public GpuBufferPool getIndexPool() {
        return mIndexPool;
    }

    @Override
    protected void onResetContext(int resetBits) {
        currentCommandBuffer().resetStates(resetBits);

        // we assume these values
        if ((resetBits & GLBackendState.kPixelStore) != 0) {
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_PACK_ROW_LENGTH, 0);
        }

        if ((resetBits & GLBackendState.kPipeline) != 0) {
            mHWBufferStates[BUFFER_TYPE_VERTEX].mBoundBufferUniqueID = INVALID_UNIQUE_ID;
            mHWBufferStates[BUFFER_TYPE_INDEX].mBoundBufferUniqueID = INVALID_UNIQUE_ID;
        }

        if ((resetBits & GLBackendState.kTexture) != 0) {
            Arrays.fill(mHWTextureStates, INVALID_UNIQUE_ID);
            //TODO
            for (var ss : mHWSamplerStates) {
                ss.mSamplerState = 0;
                ss.mBoundSampler = RefCnt.move(ss.mBoundSampler);
            }
        }

        mHWActiveTextureUnit = -1; // invalid

        if ((resetBits & GLBackendState.kRaster) != 0) {
            getGL().glDisable(GL_LINE_SMOOTH);
            getGL().glDisable(GL_POLYGON_SMOOTH);

            getGL().glDisable(GL_DITHER);
            getGL().glEnable(GL_MULTISAMPLE);
        }

        if ((resetBits & GLBackendState.kBlend) != 0) {
            getGL().glDisable(GL_COLOR_LOGIC_OP);
        }

        if ((resetBits & GLBackendState.kMisc) != 0) {
            // we don't use the z-buffer at all
            getGL().glDisable(GL_DEPTH_TEST);
            glDepthMask(false);
            getGL().glDisable(GL_POLYGON_OFFSET_FILL);

            // We don't use face culling.
            getGL().glDisable(GL_CULL_FACE);
            // We do use separate stencil. Our algorithms don't care which face is front vs. back so
            // just set this to the default for self-consistency.
            glFrontFace(GL_CCW);

            // we only ever use lines in hairline mode
            glLineWidth(1);
            glPointSize(1);
            getGL().glDisable(GL_PROGRAM_POINT_SIZE);
        }
    }

    //FIXME this is temp, wait for beginRenderPass()
    public void forceResetContext(int state) {
        markContextDirty(state);
        handleDirtyContext(state);
    }

    /**
     * Call {@link #getError()} until there are no errors.
     */
    public void clearErrors() {
        //noinspection StatementWithEmptyBody
        while (getError() != GL_NO_ERROR)
            ;
    }

    /**
     * Polls an error code and sets the OOM and context lost state.
     */
    public int getError() {
        int error = getGL().glGetError();
        if (error == GL_OUT_OF_MEMORY) {
            mOutOfMemoryEncountered = true;
        } else if (error == GL_CONTEXT_LOST) {
            mDeviceIsLost = true;
        }
        return error;
    }

    public int createTexture(GLImageDesc desc) {
        assert desc.mTarget != GL_RENDERBUFFER;
        int width = desc.getWidth(), height = desc.getHeight();
        int handle;
        handleDirtyContext(Engine.GLBackendState.kTexture);
        handle = createTexture(width, height, desc.mFormat, desc.getMipLevelCount());
        if (handle != 0) {
            mStats.incImageCreates();
            if (desc.isSampledImage()) {
                mStats.incTextureCreates();
            }
        }
        return handle;
    }

    public int createRenderbuffer(GLImageDesc desc) {
        assert desc.mTarget == GL_RENDERBUFFER;
        int width = desc.getWidth(), height = desc.getHeight();
         /*int internalFormat = glFormat;
            if (GLUtil.glFormatStencilBits(glFormat) == 0) {
                internalFormat = getCaps().getRenderbufferInternalFormat(glFormat);
            }*/
        int handle = createRenderbuffer(width, height, desc.getSampleCount(), desc.mFormat);
        if (handle != 0) {
            mStats.incImageCreates();
        }
        return handle;
    }

    /*@Nullable
    @Override
    protected GLImage onCreateImage(int width, int height,
                                    BackendFormat format,
                                    int mipLevelCount,
                                    int sampleCount,
                                    int surfaceFlags) {
        assert (mipLevelCount > 0 && sampleCount > 0);
        // We don't support protected textures in OpenGL.
        if ((surfaceFlags & ISurface.FLAG_PROTECTED) != 0) {
            return null;
        }
        // There's no memoryless attachments in OpenGL.
        if ((surfaceFlags & ISurface.FLAG_MEMORYLESS) != 0) {
            return null;
        }
        if (format.isExternal()) {
            return null;
        }
        int glFormat = format.getGLFormat();
        final int handle;
        final int target;
        if (sampleCount > 1 && (surfaceFlags & ISurface.FLAG_SAMPLED_IMAGE) == 0) {
            int internalFormat = glFormat;
            if (GLUtil.glFormatStencilBits(glFormat) == 0) {
                internalFormat = getCaps().getRenderbufferInternalFormat(glFormat);
            }
            handle = createRenderbuffer(width, height, sampleCount, internalFormat);
            target = GL_RENDERBUFFER;
        } else {
            handleDirtyContext(GLBackendState.kTexture);
            handle = createTexture(width, height, glFormat, mipLevelCount);
            target = GL_TEXTURE_2D;
        }
        if (handle == 0) {
            return null;
        }
        *//*Function<GLTexture, GLRenderTarget> target = null;
        if ((surfaceFlags & ISurface.FLAG_RENDERABLE) != 0) {
            target = createRTObjects(
                    texture,
                    width, height,
                    glFormat,
                    sampleCount);
            if (target == null) {
                glDeleteTextures(texture);
                return null;
            }
        }*//*
        final GLImageInfo info = new GLImageInfo();
        info.mTarget = target;
        info.handle = handle;
        info.mFormat = glFormat;
        info.levels = mipLevelCount;
        info.samples = sampleCount;
        //if (target == null) {
        return new GLImage(this,
                width, height,
                info,
                format,
                surfaceFlags);
        *//*} else {
            return new GLRenderTexture(this,
                    width, height,
                    info,
                    format,
                    (surfaceFlags & ISurface.FLAG_BUDGETED) != 0,
                    target);
        }*//*
    }*/

    @Nullable
    @Override
    protected GpuRenderTarget onCreateRenderTarget(int width, int height,
                                                   int sampleCount,
                                                   int numColorTargets,
                                                   @Nullable Image[] colorTargets,
                                                   @Nullable Image[] resolveTargets,
                                                   @Nullable int[] mipLevels,
                                                   @Nullable Image depthStencilTarget,
                                                   int surfaceFlags) {
        var gl = getGL();
        // There's an NVIDIA driver bug that creating framebuffer via DSA with attachments of
        // different dimensions will report GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS_EXT.
        // The workaround is to use traditional glGen* and glBind* (validate).
        // see https://forums.developer.nvidia.com/t/framebuffer-incomplete-when-attaching-color-buffers-of-different-sizes-with-dsa/211550
        final int renderFramebuffer = gl.glGenFramebuffers();
        if (renderFramebuffer == 0) {
            return null;
        }

        int usedColorTargets = 0;
        if (colorTargets != null) {
            for (int i = 0; i < numColorTargets; i++) {
                usedColorTargets += colorTargets[i] != null ? 1 : 0;
            }
        }
        int usedResolveTargets = 0;
        if (resolveTargets != null) {
            for (int i = 0; i < numColorTargets; i++) {
                usedResolveTargets += resolveTargets[i] != null ? 1 : 0;
            }
        }

        // If we are using multisampling we will create two FBOs. We render to one and then resolve to
        // the texture bound to the other.
        final int resolveFramebuffer;
        if (usedResolveTargets > 0) {
            resolveFramebuffer = gl.glGenFramebuffers();
            if (resolveFramebuffer == 0) {
                gl.glDeleteFramebuffers(renderFramebuffer);
                return null;
            }
        } else {
            resolveFramebuffer = renderFramebuffer;
        }

        GLTexture[] glColorTargets = usedColorTargets > 0
                ? new GLTexture[numColorTargets] : null;
        GLTexture[] glResolveTargets = usedResolveTargets > 0
                ? new GLTexture[numColorTargets] : null;
        GLTexture glDepthStencilTarget = (GLTexture) depthStencilTarget;

        currentCommandBuffer().bindFramebuffer(renderFramebuffer);
        if (usedColorTargets > 0) {
            int[] drawBuffers = new int[numColorTargets];
            for (int index = 0; index < numColorTargets; index++) {
                GLTexture colorTarget = (GLTexture) colorTargets[index];
                if (colorTarget == null) {
                    // unused slot
                    drawBuffers[index] = GL_NONE;
                    continue;
                }
                glColorTargets[index] = colorTarget;
                attachColorAttachment(index,
                        colorTarget,
                        mipLevels == null ? 0 : mipLevels[index]);
                drawBuffers[index] = GL_COLOR_ATTACHMENT0 + index;
            }
            glDrawBuffers(drawBuffers);
        }
        if (glDepthStencilTarget != null) {
            //TODO renderbuffer?
            glFramebufferRenderbuffer(GL_FRAMEBUFFER,
                    GL_STENCIL_ATTACHMENT,
                    GL_RENDERBUFFER,
                    glDepthStencilTarget.getHandle());
            if (GLUtil.glFormatIsPackedDepthStencil(glDepthStencilTarget.getGLFormat())) {
                glFramebufferRenderbuffer(GL_FRAMEBUFFER,
                        GL_DEPTH_STENCIL_ATTACHMENT,
                        GL_RENDERBUFFER,
                        glDepthStencilTarget.getHandle());
            }
        }
        if (!mCaps.skipErrorChecks()) {
            int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                gl.glDeleteFramebuffers(renderFramebuffer);
                gl.glDeleteFramebuffers(resolveFramebuffer);
                return null;
            }
        }

        if (usedResolveTargets > 0) {
            currentCommandBuffer().bindFramebuffer(resolveFramebuffer);
            int[] drawBuffers = new int[numColorTargets];
            for (int index = 0; index < numColorTargets; index++) {
                GLTexture resolveTarget = (GLTexture) resolveTargets[index];
                if (resolveTarget == null) {
                    // unused slot
                    drawBuffers[index] = GL_NONE;
                    continue;
                }
                glResolveTargets[index] = resolveTarget;
                attachColorAttachment(index,
                        resolveTarget,
                        mipLevels == null ? 0 : mipLevels[index]);
                drawBuffers[index] = GL_COLOR_ATTACHMENT0 + index;
            }
            glDrawBuffers(drawBuffers);
            if (!mCaps.skipErrorChecks()) {
                int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
                if (status != GL_FRAMEBUFFER_COMPLETE) {
                    gl.glDeleteFramebuffers(renderFramebuffer);
                    gl.glDeleteFramebuffers(resolveFramebuffer);
                    return null;
                }
            }
        }

        return new GLRenderTarget(this,
                width,
                height,
                sampleCount,
                renderFramebuffer,
                resolveFramebuffer,
                numColorTargets,
                glColorTargets,
                glResolveTargets,
                glDepthStencilTarget,
                surfaceFlags);
    }

    @Nullable
    @Override
    protected GLRenderTarget onWrapRenderableBackendTexture(BackendImage texture,
                                                            int sampleCount,
                                                            boolean ownership) {
        if (texture.isProtected()) {
            // Not supported in GL backend at this time.
            return null;
        }
        /*if (!(texture instanceof GLBackendImage)) {
            return null;
        }
        final GLImageInfo info = new GLImageInfo();
        ((GLBackendImage) texture).getGLImageInfo(info);
        if (info.handle == 0 || info.mFormat == 0) {
            return null;
        }
        if (info.mTarget != GL_TEXTURE_2D) {
            return null;
        }
        int format = info.mFormat;
        if (!GLUtil.glFormatIsSupported(format)) {
            return null;
        }
        handleDirtyContext(GLBackendState.kTexture);
        assert mCaps.isFormatRenderable(format, sampleCount);
        assert mCaps.isFormatTexturable(format);

        sampleCount = mCaps.getRenderTargetSampleCount(sampleCount, format);
        assert sampleCount > 0;

        *//*var objects = createRTObjects(info.handle,
                texture.getWidth(), texture.getHeight(),
                texture.getBackendFormat(),
                sampleCount);*//*

        var colorTarget = createImage(
                texture.getWidth(), texture.getHeight(),
                texture.getBackendFormat(),
                sampleCount,
                ISurface.FLAG_BUDGETED | ISurface.FLAG_RENDERABLE,
                ""
        );*/

        //TODO create wrapped texture
        /*if (objects != null) {


            return new GLRenderTarget(this, texture.getWidth(), texture.getHeight(),
                format, sampleCount, framebuffer, msaaFramebuffer,
                texture, msaaColorBuffer, ownership);
        }*/

        return null;
    }

    @Override
    protected GpuRenderTarget onWrapGLDefaultFramebuffer(int width, int height,
                                                         int sampleCount,
                                                         int depthBits,
                                                         int stencilBits,
                                                         BackendFormat format) {
        int actualSamplerCount = mCaps.getRenderTargetSampleCount(sampleCount, format.getGLFormat());
        return GLRenderTarget.makeWrapped(this,
                width,
                height,
                format,
                actualSamplerCount,
                0,
                depthBits,
                stencilBits,
                false);
    }

    @Nullable
    @Override
    public GLRenderTarget onWrapBackendRenderTarget(BackendRenderTarget backendRenderTarget) {
        GLFramebufferInfo info = new GLFramebufferInfo();
        if (!backendRenderTarget.getGLFramebufferInfo(info)) {
            return null;
        }
        if (backendRenderTarget.isProtected()) {
            return null;
        }
        if (!mCaps.isFormatRenderable(info.mFormat, backendRenderTarget.getSampleCount())) {
            return null;
        }
        int actualSamplerCount = mCaps.getRenderTargetSampleCount(backendRenderTarget.getSampleCount(), info.mFormat);
        return GLRenderTarget.makeWrapped(this,
                backendRenderTarget.getWidth(),
                backendRenderTarget.getHeight(),
                backendRenderTarget.getBackendFormat(),
                actualSamplerCount,
                info.mFramebuffer,
                0,
                backendRenderTarget.getStencilBits(),
                false);
    }

    @Override
    protected boolean onWritePixels(Image image,
                                    int x, int y,
                                    int width, int height,
                                    int dstColorType,
                                    int srcColorType,
                                    int rowBytes, long pixels) {
        assert (!image.getBackendFormat().isCompressed());
        if (!image.isSampledImage() && !image.isStorageImage()) {
            return false;
        }
        GLTexture glTexture = (GLTexture) image;
        int glFormat = glTexture.getGLFormat();
        assert (mCaps.isFormatTexturable(glFormat));

        int target = glTexture.getTarget();
        if (target == GL_RENDERBUFFER) {
            return false;
        }

        int srcFormat = mCaps.getPixelsExternalFormat(
                glFormat, dstColorType, srcColorType, /*write*/true
        );
        if (srcFormat == 0) {
            return false;
        }
        int srcType = mCaps.getPixelsExternalType(
                glFormat, dstColorType, srcColorType
        );
        if (srcType == 0) {
            return false;
        }
        handleDirtyContext(GLBackendState.kTexture | GLBackendState.kPixelStore);

        boolean dsa = mCaps.hasDSASupport();
        int texName = glTexture.getHandle();
        int boundTexture = 0;
        //TODO not only 2D
        if (!dsa) {
            boundTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
            if (texName != boundTexture) {
                glBindTexture(target, texName);
            }
        }

        GLTextureMutableState mutableState = glTexture.getGLMutableState();
        if (mutableState.baseMipmapLevel != 0) {
            if (dsa) {
                glTextureParameteri(texName, GL_TEXTURE_BASE_LEVEL, 0);
            } else {
                glTexParameteri(target, GL_TEXTURE_BASE_LEVEL, 0);
            }
            mutableState.baseMipmapLevel = 0;
        }
        int maxLevel = glTexture.getMipLevelCount() - 1; // minus base level
        if (mutableState.maxMipmapLevel != maxLevel) {
            if (dsa) {
                glTextureParameteri(texName, GL_TEXTURE_MAX_LEVEL, maxLevel);
            } else {
                glTexParameteri(target, GL_TEXTURE_MAX_LEVEL, maxLevel);
            }
            // Bug fixed by Arc 3D
            mutableState.maxMipmapLevel = maxLevel;
        }

        assert (x >= 0 && y >= 0 && width > 0 && height > 0);
        assert (pixels != 0);
        int bpp = ColorInfo.bytesPerPixel(srcColorType);

        int trimRowBytes = width * bpp;
        if (rowBytes != trimRowBytes) {
            int rowLength = rowBytes / bpp;
            glPixelStorei(GL_UNPACK_ROW_LENGTH, rowLength);
        } else {
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        }

        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        if (dsa) {
            glTextureSubImage2D(texName, 0,
                    x, y, width, height, srcFormat, srcType, pixels);
        } else {
            glTexSubImage2D(target, 0,
                    x, y, width, height, srcFormat, srcType, pixels);
        }

        if (!dsa) {
            if (texName != boundTexture) {
                glBindTexture(target, boundTexture);
            }
        }

        return true;
    }

    @Override
    protected boolean onGenerateMipmaps(Image image) {
        var glImage = (GLTexture) image;
        if (mCaps.hasDSASupport()) {
            glGenerateTextureMipmap(glImage.getHandle());
        } else {
            var texName = glImage.getHandle();
            var boundTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
            if (texName != boundTexture) {
                glBindTexture(GL_TEXTURE_2D, texName);
            }
            glGenerateMipmap(GL_TEXTURE_2D);
            if (texName != boundTexture) {
                glBindTexture(GL_TEXTURE_2D, boundTexture);
            }
        }
        return true;
    }

    @Override
    protected boolean onCopySurface(GpuSurface src,
                                    int srcL, int srcT, int srcR, int srcB,
                                    GpuSurface dst,
                                    int dstL, int dstT, int dstR, int dstB,
                                    int filter) {
        int srcWidth = srcR - srcL;
        int srcHeight = srcB - srcT;
        int dstWidth = dstR - dstL;
        int dstHeight = dstB - dstT;

        // we restore the context, no need to handle
        // handleDirtyContext();

        if (srcWidth == dstWidth && srcHeight == dstHeight) {
            // no scaling
            if (mCaps.hasCopyImageSupport() &&
                    src.asImage() instanceof GLTexture srcImage &&
                    dst.asImage() instanceof GLTexture dstImage &&
                    mCaps.canCopyImage(
                            srcImage.getGLFormat(), 1,
                            dstImage.getGLFormat(), 1
                    )) {
                //TODO checks
                glCopyImageSubData(
                        srcImage.getHandle(),
                        srcImage.getTarget(),
                        0,
                        srcL, srcT, 0,
                        dstImage.getHandle(),
                        dstImage.getTarget(),
                        0,
                        dstL, dstT, 0,
                        srcWidth, srcHeight, 1
                );
                return true;
            }

            if (src.asImage() instanceof GLTexture srcTex &&
                    dst.asImage() instanceof GLTexture dstTex &&
                    mCaps.canCopyTexSubImage(
                            srcTex.getGLFormat(),
                            dstTex.getGLFormat()
                    )) {

                int dstTexName = dstTex.getHandle();
                int boundTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
                if (dstTexName != boundTexture) {
                    getGL().glBindTexture(GL_TEXTURE_2D, dstTexName);
                }

                int framebuffer = mCopySrcFramebuffer;
                if (framebuffer == 0) {
                    mCopySrcFramebuffer = framebuffer = getGL().glGenFramebuffers();
                }
                int boundFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
                getGL().glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
                glFramebufferTexture(
                        GL_READ_FRAMEBUFFER,
                        GL_COLOR_ATTACHMENT0,
                        srcTex.getHandle(),
                        0
                );

                glCopyTexSubImage2D(
                        GL_TEXTURE_2D,
                        0,
                        dstL, dstT,
                        srcL, srcT,
                        srcWidth, srcHeight
                );

                glFramebufferTexture(
                        GL_READ_FRAMEBUFFER,
                        GL_COLOR_ATTACHMENT0,
                        0,
                        0);
                getGL().glBindFramebuffer(GL_READ_FRAMEBUFFER, boundFramebuffer);

                if (dstTexName != boundTexture) {
                    getGL().glBindTexture(GL_TEXTURE_2D, boundTexture);
                }

                return true;
            }
        }

        //TODO

        return false;
    }

    @Override
    protected OpsRenderPass onGetOpsRenderPass(ImageProxyView writeView,
                                               Rect2i contentBounds,
                                               byte colorOps,
                                               byte stencilOps,
                                               float[] clearColor,
                                               Set<SurfaceProxy> sampledTextures,
                                               int pipelineFlags) {
        mStats.incRenderPasses();
        if (mCachedOpsRenderPass == null) {
            mCachedOpsRenderPass = new GLOpsRenderPass(this);
        }
        //TODO
        /*return mCachedOpsRenderPass.set(writeView.getProxy().getGpuRenderTarget(),
                contentBounds,
                writeView.getOrigin(),
                colorOps,
                stencilOps,
                clearColor);*/
        return null;
    }

    public GLCommandBuffer beginRenderPass(GLRenderTarget fs,
                                           byte colorOps,
                                           byte stencilOps,
                                           float[] clearColor) {
        handleDirtyContext(GLBackendState.kRenderTarget);

        GLCommandBuffer cmdBuffer = currentCommandBuffer();

        boolean colorLoadClear = LoadStoreOps.loadOp(colorOps) == LoadOp.Clear;
        boolean stencilLoadClear = LoadStoreOps.loadOp(stencilOps) == LoadOp.Clear;
        if (colorLoadClear || stencilLoadClear) {
            int framebuffer = fs.getRenderFramebuffer();
            cmdBuffer.flushScissorTest(false);
            if (colorLoadClear) {
                cmdBuffer.flushColorWrite(true);
                glClearNamedFramebufferfv(framebuffer,
                        GL_COLOR,
                        0,
                        clearColor);
            }
            if (stencilLoadClear) {
                glStencilMask(0xFFFFFFFF); // stencil will be flushed later
                glClearNamedFramebufferfi(framebuffer,
                        GL_DEPTH_STENCIL,
                        0,
                        1.0f, 0);
            }
        }
        cmdBuffer.flushRenderTarget(fs);

        return cmdBuffer;
    }

    public void endRenderPass(GLRenderTarget fs,
                              byte colorOps,
                              byte stencilOps) {
        handleDirtyContext(GLBackendState.kRenderTarget);

        boolean colorStoreDiscard = LoadStoreOps.storeOp(colorOps) == StoreOp.DontCare;
        boolean stencilStoreDiscard = LoadStoreOps.storeOp(stencilOps) == StoreOp.DontCare;
        if (colorStoreDiscard || stencilStoreDiscard) {
            int framebuffer = fs.getRenderFramebuffer();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                final long pAttachments = stack.nmalloc(4, 8);
                int numAttachments = 0;
                if (colorStoreDiscard) {
                    int attachment = fs.getRenderFramebuffer() == 0
                            ? GL_COLOR
                            : GL_COLOR_ATTACHMENT0;
                    memPutInt(pAttachments, attachment);
                    numAttachments++;
                }
                if (stencilStoreDiscard) {
                    int attachment = fs.getRenderFramebuffer() == 0
                            ? GL_STENCIL
                            : GL_STENCIL_ATTACHMENT;
                    memPutInt(pAttachments + (numAttachments << 2), attachment);
                    numAttachments++;
                }
                nglInvalidateNamedFramebufferData(framebuffer, numAttachments, pAttachments);
            }
        }
    }

    @Nullable
    @Override
    protected GLBuffer onCreateBuffer(long size, int flags) {
        handleDirtyContext(GLBackendState.kPipeline);
        return GLBuffer.make(this, size, flags);
    }

    @Override
    protected void onResolveRenderTarget(GpuRenderTarget renderTarget,
                                         int resolveLeft, int resolveTop,
                                         int resolveRight, int resolveBottom) {
        GLRenderTarget glRenderTarget = (GLRenderTarget) renderTarget;
        //TODO handle non-DSA case
        //handleDirtyContext();

        int renderFramebuffer = glRenderTarget.getRenderFramebuffer();
        int resolveFramebuffer = glRenderTarget.getResolveFramebuffer();

        // We should always have something to resolve
        assert (renderFramebuffer != 0 && renderFramebuffer != resolveFramebuffer);

        // BlitFramebuffer respects the scissor, so disable it.
        currentCommandBuffer().flushScissorTest(false);
        glBlitNamedFramebuffer(renderFramebuffer, resolveFramebuffer, // MSAA to single
                resolveLeft, resolveTop, resolveRight, resolveBottom, // src rect
                resolveLeft, resolveTop, resolveRight, resolveBottom, // dst rect
                GL_COLOR_BUFFER_BIT, GL_NEAREST);
    }

    private void flush(boolean forceFlush) {
        if (mNeedsFlush || forceFlush) {
            glFlush();
            mNeedsFlush = false;
        }
    }

    @Override
    public long insertFence() {
        long fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        mNeedsFlush = true;
        return fence;
    }

    @Override
    public boolean checkFence(long fence) {
        int result = glClientWaitSync(fence, 0, 0L);
        return (result == GL_CONDITION_SATISFIED || result == GL_ALREADY_SIGNALED);
    }

    @Override
    public void deleteFence(long fence) {
        glDeleteSync(fence);
    }

    @Override
    public void addFinishedCallback(FlushInfo.FinishedCallback callback) {
        mFinishedCallbacks.addLast(callback);
        mFinishedFences.enqueue(insertFence());
        assert (mFinishedCallbacks.size() == mFinishedFences.size());
    }

    @Override
    public void checkFinishedCallbacks() {
        // Bail after the first unfinished sync since we expect they signal in the order inserted.
        while (!mFinishedCallbacks.isEmpty() && checkFence(mFinishedFences.firstLong())) {
            // While we are processing a proc we need to make sure to remove it from the callback list
            // before calling it. This is because the client could trigger a call (e.g. calling
            // flushAndSubmit(/*sync=*/true)) that has us process the finished callbacks. We also must
            // process deleting the fence before a client may abandon the context.
            deleteFence(mFinishedFences.dequeueLong());
            mFinishedCallbacks.removeFirst().onFinished();
        }
        assert (mFinishedCallbacks.size() == mFinishedFences.size());
    }

    private void callAllFinishedCallbacks(boolean cleanup) {
        while (!mFinishedCallbacks.isEmpty()) {
            // While we are processing a proc we need to make sure to remove it from the callback list
            // before calling it. This is because the client could trigger a call (e.g. calling
            // flushAndSubmit(/*sync=*/true)) that has us process the finished callbacks. We also must
            // process deleting the fence before a client may abandon the context.
            if (cleanup) {
                deleteFence(mFinishedFences.dequeueLong());
            }
            mFinishedCallbacks.removeFirst().onFinished();
        }
        if (!cleanup) {
            mFinishedFences.clear();
        } else {
            assert (mFinishedFences.isEmpty());
        }
    }

    @Override
    public void waitForQueue() {
        glFinish();
    }

    // Binds a buffer to the GL target corresponding to 'type', updates internal state tracking, and
    // returns the GL target the buffer was bound to.
    // When 'type' is 'index', this function will also implicitly bind the default VAO.
    // If the caller wishes to bind an index buffer to a specific VAO, it can call glBind directly.
    public int bindBuffer(@Nonnull @RawPtr GLBuffer buffer) {
        assert !getCaps().hasDSASupport();

        handleDirtyContext(GLBackendState.kPipeline);

        int type = bufferUsageToType(buffer.getUsage());
        if (type == BUFFER_TYPE_INDEX) {
            // Index buffer state is tied to the vertex array.
            currentCommandBuffer().bindVertexArray(null);
        }

        var bufferState = mHWBufferStates[type];
        if (bufferState.mBoundBufferUniqueID != buffer.getUniqueID()) {
            getGL().glBindBuffer(bufferState.mTarget, buffer.getHandle());
            bufferState.mBoundBufferUniqueID = buffer.getUniqueID();
        }

        return bufferState.mTarget;
    }

    public void bindIndexBufferInPipe(@Nonnull @RawPtr GLBuffer buffer) {
        // pipeline is already handled
        //handleDirtyContext(GLBackendState.kPipeline);

        assert bufferUsageToType(buffer.getUsage()) == BUFFER_TYPE_INDEX;

        // force rebind
        var bufferState = mHWBufferStates[BUFFER_TYPE_INDEX];
        assert bufferState.mTarget == GL_ELEMENT_ARRAY_BUFFER;

        getGL().glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buffer.getHandle());
        bufferState.mBoundBufferUniqueID = buffer.getUniqueID();
    }

    /**
     * Bind raw buffer ID to context (below OpenGL 4.5).
     *
     * @param usage  {@link BufferUsageFlags}
     * @param buffer the nonzero texture ID
     */
    public int bindBufferForSetup(int usage, int buffer) {
        assert !getCaps().hasDSASupport();

        handleDirtyContext(GLBackendState.kPipeline);

        int type = bufferUsageToType(usage);
        if (type == BUFFER_TYPE_INDEX) {
            // Index buffer state is tied to the vertex array.
            currentCommandBuffer().bindVertexArray(null);
        }

        var bufferState = mHWBufferStates[type];
        getGL().glBindBuffer(bufferState.mTarget, buffer);
        bufferState.mBoundBufferUniqueID = INVALID_UNIQUE_ID;

        return bufferState.mTarget;
    }

    public void bindTextureSampler(int bindingUnit, GLTexture texture,
                                   int samplerState, short readSwizzle) {
        boolean dsa = mCaps.hasDSASupport();
        if (mHWTextureStates[bindingUnit] != texture.getUniqueID()) {
            if (dsa) {
                glBindTextureUnit(bindingUnit, texture.getHandle());
            } else {
                setTextureUnit(bindingUnit);
                getGL().glBindTexture(GL_TEXTURE_2D, texture.getHandle());
            }
            mHWTextureStates[bindingUnit] = texture.getUniqueID();
        }
        var hwSamplerState = mHWSamplerStates[bindingUnit];
        if (hwSamplerState.mSamplerState != samplerState) {
            GLSampler sampler = samplerState != 0
                    ? mResourceProvider.findOrCreateCompatibleSampler(samplerState)
                    : null;
            getGL().glBindSampler(bindingUnit, sampler != null
                    ? sampler.getHandle()
                    : 0);
            hwSamplerState.mBoundSampler = RefCnt.move(hwSamplerState.mBoundSampler, sampler);
        }
        GLTextureMutableState mutableState = texture.getGLMutableState();
        if (mutableState.baseMipmapLevel != 0) {
            if (dsa) {
                glTextureParameteri(texture.getHandle(), GL_TEXTURE_BASE_LEVEL, 0);
            } else {
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
            }
            mutableState.baseMipmapLevel = 0;
        }
        int maxLevel = texture.getMipLevelCount() - 1; // minus base level
        if (mutableState.maxMipmapLevel != maxLevel) {
            if (dsa) {
                glTextureParameteri(texture.getHandle(), GL_TEXTURE_MAX_LEVEL, maxLevel);
            } else {
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, maxLevel);
            }
            mutableState.maxMipmapLevel = maxLevel;
        }
        //TODO texture view
        // texture view is available since 4.3, but less used in OpenGL
        // in case of some driver bugs, we don't use GL_TEXTURE_SWIZZLE_RGBA
        // and OpenGL ES does not support GL_TEXTURE_SWIZZLE_RGBA at all
        for (int i = 0; i < 4; ++i) {
            int swiz = switch (readSwizzle & 0xF) {
                case 0 -> GL_RED;
                case 1 -> GL_GREEN;
                case 2 -> GL_BLUE;
                case 3 -> GL_ALPHA;
                case 4 -> GL_ZERO;
                case 5 -> GL_ONE;
                default -> throw new AssertionError(readSwizzle);
            };
            if (mutableState.getSwizzle(i) != swiz) {
                mutableState.setSwizzle(i, swiz);
                // swizzle enums are sequential
                int channel = GL_TEXTURE_SWIZZLE_R + i;
                if (dsa) {
                    glTextureParameteri(texture.getHandle(), channel, swiz);
                } else {
                    glTexParameteri(GL_TEXTURE_2D, channel, swiz);
                }
            }
            readSwizzle >>= 4;
        }
    }

    /**
     * Binds texture unit in context. OpenGL 3 only.
     *
     * @param unit 0-based texture unit index
     */
    public void setTextureUnit(int unit) {
        assert (unit >= 0 && unit < mHWTextureStates.length);
        if (unit != mHWActiveTextureUnit) {
            glActiveTexture(GL_TEXTURE0 + unit);
            mHWActiveTextureUnit = unit;
        }
    }

    private int createTexture(int width, int height, int format, int levels) {
        assert (GLUtil.glFormatIsSupported(format));
        assert (!GLUtil.glFormatIsCompressed(format));

        int internalFormat = mCaps.getTextureInternalFormat(format);
        if (internalFormat == 0) {
            return 0;
        }

        assert (mCaps.isFormatTexturable(format));
        final int texture;
        if (mCaps.hasDSASupport()) {
            assert (mCaps.isTextureStorageCompatible(format));
            texture = glCreateTextures(GL_TEXTURE_2D);
            if (texture == 0) {
                return 0;
            }
            if (mCaps.skipErrorChecks()) {
                glTextureStorage2D(texture, levels, internalFormat, width, height);
            } else {
                clearErrors();
                glTextureStorage2D(texture, levels, internalFormat, width, height);
                if (getError() != GL_NO_ERROR) {
                    glDeleteTextures(texture);
                    return 0;
                }
            }
        } else {
            texture = mGLInterface.glGenTextures();
            if (texture == 0) {
                return 0;
            }
            int boundTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
            glBindTexture(GL_TEXTURE_2D, texture);
            try {
                if (mCaps.isTextureStorageCompatible(format)) {
                    if (mCaps.skipErrorChecks()) {
                        glTexStorage2D(GL_TEXTURE_2D, levels, internalFormat, width, height);
                    } else {
                        clearErrors();
                        glTexStorage2D(GL_TEXTURE_2D, levels, internalFormat, width, height);
                        if (getError() != GL_NO_ERROR) {
                            glDeleteTextures(texture);
                            return 0;
                        }
                    }
                } else {
                    final int externalFormat = mCaps.getFormatDefaultExternalFormat(format);
                    final int externalType = mCaps.getFormatDefaultExternalType(format);
                    final boolean checks = !mCaps.skipErrorChecks();
                    int error = 0;
                    if (checks) {
                        clearErrors();
                    }
                    for (int level = 0; level < levels; level++) {
                        int currentWidth = Math.max(1, width >> level);
                        int currentHeight = Math.max(1, height >> level);
                        nglTexImage2D(GL_TEXTURE_2D, level, internalFormat,
                                currentWidth, currentHeight,
                                0, externalFormat, externalType, MemoryUtil.NULL);
                        if (checks) {
                            error |= getError();
                        }
                    }
                    if (error != 0) {
                        glDeleteTextures(texture);
                        return 0;
                    }
                }
            } finally {
                glBindTexture(GL_TEXTURE_2D, boundTexture);
            }
        }

        return texture;
    }

    private int createRenderbuffer(int width, int height, int sampleCount, int internalFormat) {
        var gl = getGL();
        int renderbuffer = gl.glGenRenderbuffers();
        if (renderbuffer == 0) {
            return 0;
        }
        gl.glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
        boolean skipError = getCaps().skipErrorChecks();
        if (!skipError) {
            clearErrors();
        }
        // GL has a concept of MSAA rasterization with a single sample, but we do not.
        if (sampleCount > 1) {
            gl.glRenderbufferStorageMultisample(GL_RENDERBUFFER, sampleCount, internalFormat, width, height);
        } else {
            // glRenderbufferStorage is equivalent to calling glRenderbufferStorageMultisample
            // with the samples set to zero. But we don't think sampleCount=1 is multisampled.
            gl.glRenderbufferStorage(GL_RENDERBUFFER, internalFormat, width, height);
        }
        if (!skipError && getError() != GL_NO_ERROR) {
            gl.glDeleteRenderbuffers(renderbuffer);
            return 0;
        }

        return renderbuffer;
    }

    private void attachColorAttachment(int index, GLTexture texture, int mipLevel) {
        switch (texture.getTarget()) {
            case GL_TEXTURE_2D, GL_TEXTURE_2D_MULTISAMPLE -> {
                glFramebufferTexture(GL_FRAMEBUFFER,
                        GL_COLOR_ATTACHMENT0 + index,
                        texture.getHandle(),
                        mipLevel);
            }
            case GL_RENDERBUFFER -> {
                glFramebufferRenderbuffer(GL_FRAMEBUFFER,
                        GL_COLOR_ATTACHMENT0 + index,
                        GL_RENDERBUFFER,
                        texture.getHandle());
            }
            default -> throw new UnsupportedOperationException();
        }
    }
}

/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024 BloCamLimb <pocamelards@gmail.com>
 *
 * Arc3D is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc3D is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc3D. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arc3d.test;

import icyllis.arc3d.core.Matrix4;
import icyllis.arc3d.core.RoundRect;
import icyllis.arc3d.engine.*;
import icyllis.arc3d.granite.*;
import icyllis.arc3d.granite.geom.SDFRoundRectStep;
import icyllis.arc3d.opengl.GLUtil;
import icyllis.arc3d.opengl.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.Objects;

import static icyllis.arc3d.opengl.GLCore.GL_DEBUG_CALLBACK_FUNCTION;
import static icyllis.arc3d.opengl.GLCore.GL_DEBUG_OUTPUT;
import static icyllis.arc3d.opengl.GLCore.glDebugMessageCallback;
import static icyllis.arc3d.opengl.GLUtil.*;
import static org.lwjgl.opengl.AMDDebugOutput.glDebugMessageCallbackAMD;
import static org.lwjgl.opengl.ARBDebugOutput.glDebugMessageCallbackARB;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class TestDrawPass {

    public static final Logger LOGGER = LoggerFactory.getLogger("Arc3D");

    // -Dorg.slf4j.simpleLogger.logFile=System.out -Dorg.slf4j.simpleLogger.defaultLogLevel=debug -ea
    public static void main(String[] args) {
        GLFW.glfwInit();
        TinyFileDialogs.tinyfd_messageBox(
                "Arc3D Test",
                "Arc3D starting with pid: " + ProcessHandle.current().pid(),
                "ok",
                "info",
                true
        );
        Objects.requireNonNull(GL.getFunctionProvider());
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, 0);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        final int[][] versions = {{4, 6}, {4, 5}, {4, 4}, {4, 3}, {4, 2}, {4, 1}, {3, 3}};
        long window = 0;
        for (int[] version : versions) {
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, version[0]);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, version[1]);
            window = GLFW.glfwCreateWindow(1280, 720, "Arc3D Test Window", 0, 0);
            if (window != 0) {
                break;
            }
        }
        if (window == 0) {
            throw new RuntimeException();
        }
        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);

        ContextOptions contextOptions = new ContextOptions();
        contextOptions.mLogger = LOGGER;
        ImmediateContext immediateContext = GLUtil.makeOpenGL(
                GL.createCapabilities(),
                contextOptions
        );
        if (immediateContext == null) {
            throw new RuntimeException();
        }
        RecordingContext recordingContext = immediateContext.makeRecordingContext();
        if (recordingContext == null) {
            throw new RuntimeException();
        }
        glSetupDebugCallback();
        GL11C.glDisable(GL30C.GL_MULTISAMPLE);

        //new Scanner(System.in).next();

        DrawCommandList commandList = new DrawCommandList();
        MeshDrawWriter drawWriter = new MeshDrawWriter(recordingContext.getDynamicBufferManager(),
                commandList);

        var step = new SDFRoundRectStep();

        drawWriter.newPipelineState(
                step.vertexBinding(),
                step.instanceBinding(),
                step.vertexStride(),
                step.instanceStride()
        );
        commandList.bindGraphicsPipeline(0);

        int nRects = 1000;
        for (int i = 0; i < nRects; i++) {
            Draw draw = new Draw();
            RoundRect rrect = new RoundRect();
            rrect.mLeft = (int) (Math.random() * 910);
            rrect.mTop = (int) (Math.random() * 450);
            int w = (int) (Math.random() * Math.random() * Math.random() * Math.random() * 350) + 20;
            int h = (int) (Math.random() * Math.random() * Math.random() * Math.random() * 250) + 20;
            rrect.mRight = rrect.mLeft + w;
            rrect.mBottom = rrect.mTop + h;
            rrect.mRadiusUL = Math.min((int) (Math.random() * 50), Math.min(w, h) / 2);
            draw.mGeometry = rrect;
            draw.mTransform = Matrix4.identity();
            float cx = (rrect.mLeft + rrect.mRight) * 0.5f;
            float cy = (rrect.mTop + rrect.mBottom) * 0.5f;
            draw.mTransform.preTranslate(cx, cy);
            draw.mTransform.preRotateZ(i);
            draw.mTransform.preTranslate(-cx, -cy);
            int stroke = (int) (Math.random() * 50);
            draw.mStrokeRadius = stroke < 25 ? -1 : stroke - 20;
            step.writeMesh(drawWriter, draw, new float[]{(float) (Math.random() * 0.2f),
                    (float) (Math.random() * 0.2f), (float) (Math.random() * 0.2f), 0.2f}); // premultiplied color
        }

        drawWriter.flush();
        commandList.finish();
        LOGGER.info("CommandList primitive size: {}", commandList.mPrimitives.limit());
        ObjectArrayList<Resource> resourceRefs = new ObjectArrayList<>();
        recordingContext.getDynamicBufferManager().flush(null, resourceRefs);

        LOGGER.info(Swizzle.toString(Swizzle.concat(Swizzle.make("000r"), Swizzle.make("aaaa"))));

        LOGGER.info(resourceRefs.toString());

        PrintWriter pw = new PrintWriter(System.out, true);
        commandList.debug(pw);

        GraphicsPipelineDesc graphicsPipelineDesc = new GraphicsPipelineDesc(step);

        int ubo = GL33C.glGenBuffers();
        GL33C.glBindBufferBase(GL33C.GL_UNIFORM_BUFFER, 0, ubo);
        GL44C.glBufferStorage(GL33C.GL_UNIFORM_BUFFER, new float[]{2.0f / 1280, -1.0f, -2.0f / 720, 1.0f}, 0);
        LOGGER.info("UBO: {}", ubo);

        GL33C.glEnable(GL33C.GL_BLEND);
        GL33C.glBlendFunc(GLCore.GL_ONE, GLCore.GL_ONE_MINUS_SRC_ALPHA);

        var pipeline = recordingContext.getResourceProvider().findOrCreateGraphicsPipeline(
                graphicsPipelineDesc, new RenderPassDesc());
        LOGGER.info(String.valueOf(pipeline));
        if (pipeline != null) {
            GLFW.glfwShowWindow(window);
            var device = (GLDevice) immediateContext.getDevice();
            device.flushRenderCalls();
            device.getGL().glViewport(0, 0, 1280, 720);
            var cmdBuffer = (GLCommandBuffer) immediateContext.currentCommandBuffer();
            assert cmdBuffer != null;
            cmdBuffer.bindGraphicsPipeline(pipeline);

            commandList.execute(cmdBuffer);
            GLFW.glfwSwapBuffers(window);

            while (!GLFW.glfwWindowShouldClose(window)) {
                GLFW.glfwWaitEvents();
                cmdBuffer.resetStates(~0);
                GL33C.glClearBufferfv(GL33C.GL_COLOR, 0,
                        new float[]{(float) (GLFW.glfwGetTime() % 1.0), 0.5f, 0.5f, 1.0f});
                cmdBuffer.bindGraphicsPipeline(pipeline);
                commandList.execute(cmdBuffer);
                GLFW.glfwSwapBuffers(window);
            }

            pipeline.unref();
        }

        GL11C.glFinish();

        //DrawPass.make()

        //deviceGpu.unref();

        recordingContext.unref();
        immediateContext.unref();
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }

    public static void glSetupDebugCallback() {

        GLCapabilities caps = GL.getCapabilities();

        if (glGetPointer(GL_DEBUG_CALLBACK_FUNCTION) == NULL) {
            if (caps.OpenGL43 || caps.GL_KHR_debug) {
                LOGGER.debug("Using OpenGL 4.3 for debug logging");
                glDebugMessageCallback(TestDrawPass::glDebugMessage, NULL);
                glEnable(GL_DEBUG_OUTPUT);
            } else if (caps.GL_ARB_debug_output) {
                LOGGER.debug("Using ARB_debug_output for debug logging");
                GLDebugMessageARBCallback proc = new GLDebugMessageARBCallback() {
                    @Override
                    public void invoke(int source, int type, int id, int severity, int length, long message,
                                       long userParam) {
                        LOGGER.info("0x{}[{},{},{}]: {}", Integer.toHexString(id),
                                getSourceARB(source), getTypeARB(type), getSeverityARB(severity),
                                GLDebugMessageARBCallback.getMessage(length, message));
                    }
                };
                glDebugMessageCallbackARB(proc, NULL);
            } else if (caps.GL_AMD_debug_output) {
                LOGGER.debug("Using AMD_debug_output for debug logging");
                GLDebugMessageAMDCallback proc = new GLDebugMessageAMDCallback() {
                    @Override
                    public void invoke(int id, int category, int severity, int length, long message,
                                       long userParam) {
                        LOGGER.info("0x{}[{},{}]: {}", Integer.toHexString(id),
                                getCategoryAMD(category), getSeverityAMD(severity),
                                GLDebugMessageAMDCallback.getMessage(length, message));
                    }
                };
                glDebugMessageCallbackAMD(proc, NULL);
            } else {
                LOGGER.debug("No debug callback function was used...");
            }
        } else {
            LOGGER.debug("The debug callback function is already set.");
        }
    }

    public static void glDebugMessage(int source, int type, int id, int severity, int length, long message,
                                      long userParam) {
        switch (severity) {
            case GL_DEBUG_SEVERITY_HIGH -> LOGGER.error("({}|{}|0x{}) {}",
                    getDebugSource(source), getDebugType(type), Integer.toHexString(id),
                    GLDebugMessageCallback.getMessage(length, message));
            case GL_DEBUG_SEVERITY_MEDIUM -> LOGGER.warn("({}|{}|0x{}) {}",
                    getDebugSource(source), getDebugType(type), Integer.toHexString(id),
                    GLDebugMessageCallback.getMessage(length, message));
            case GL_DEBUG_SEVERITY_LOW -> LOGGER.info("({}|{}|0x{}) {}",
                    getDebugSource(source), getDebugType(type), Integer.toHexString(id),
                    GLDebugMessageCallback.getMessage(length, message));
            case GL_DEBUG_SEVERITY_NOTIFICATION -> LOGGER.debug("({}|{}|0x{}) {}",
                    getDebugSource(source), getDebugType(type), Integer.toHexString(id),
                    GLDebugMessageCallback.getMessage(length, message));
        }
    }
}
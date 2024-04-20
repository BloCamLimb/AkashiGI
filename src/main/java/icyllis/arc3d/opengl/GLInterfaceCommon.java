/*
 * This file is part of Arc 3D.
 *
 * Copyright (C) 2024 BloCamLimb <pocamelards@gmail.com>
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

import org.lwjgl.system.NativeType;

import javax.annotation.Nullable;

/**
 * OpenGL 3.1 Core and OpenGL ES 3.0 have a common subset.
 * <p>
 * No javadoc here, please refer to LWJGL javadoc and OpenGL specification.
 */
public interface GLInterfaceCommon {

    void glEnable(@NativeType("GLenum") int cap);

    void glDisable(@NativeType("GLenum") int cap);

    @NativeType("void")
    int glGenTextures();

    void glDeleteTextures(@NativeType("GLuint const *") int texture);

    void glBindTexture(@NativeType("GLenum") int target, @NativeType("GLuint") int texture);

    void glBlendFunc(@NativeType("GLenum") int sfactor, @NativeType("GLenum") int dfactor);

    void glColorMask(@NativeType("GLboolean") boolean red, @NativeType("GLboolean") boolean green,
                     @NativeType("GLboolean") boolean blue, @NativeType("GLboolean") boolean alpha);

    void glDrawArrays(@NativeType("GLenum") int mode, @NativeType("GLint") int first, @NativeType("GLsizei") int count);

    void glDrawElements(@NativeType("GLenum") int mode, @NativeType("GLsizei") int count,
                        @NativeType("GLenum") int type, @NativeType("void const *") long indices);

    @NativeType("GLenum")
    int glGetError();

    @Nullable
    @NativeType("GLubyte const *")
    String glGetString(@NativeType("GLenum") int name);

    @NativeType("void")
    int glGetInteger(@NativeType("GLenum") int pname);

    void glScissor(@NativeType("GLint") int x, @NativeType("GLint") int y, @NativeType("GLsizei") int width,
                   @NativeType("GLsizei") int height);

    void glViewport(@NativeType("GLint") int x, @NativeType("GLint") int y, @NativeType("GLsizei") int width,
                    @NativeType("GLsizei") int height);

    @NativeType("void")
    int glGenBuffers();

    void glDeleteBuffers(@NativeType("GLuint const *") int buffer);

    void glBindBuffer(@NativeType("GLenum") int target, @NativeType("GLuint") int buffer);

    void glBufferData(@NativeType("GLenum") int target, @NativeType("GLsizeiptr") long size,
                      @NativeType("void const *") long data, @NativeType("GLenum") int usage);

    void glBufferSubData(@NativeType("GLenum") int target, @NativeType("GLintptr") long offset,
                         @NativeType("GLsizeiptr") long size, @NativeType("void const *") long data);

    @NativeType("GLboolean")
    boolean glUnmapBuffer(@NativeType("GLenum") int target);

    @NativeType("GLuint")
    int glCreateProgram();

    void glDeleteProgram(@NativeType("GLuint") int program);

    @NativeType("GLuint")
    int glCreateShader(@NativeType("GLenum") int type);

    void glDeleteShader(@NativeType("GLuint") int shader);

    void glAttachShader(@NativeType("GLuint") int program, @NativeType("GLuint") int shader);

    void glDetachShader(@NativeType("GLuint") int program, @NativeType("GLuint") int shader);

    void glShaderSource(@NativeType("GLuint") int shader, @NativeType("GLsizei") int count,
                        @NativeType("GLchar const * const *") long strings,
                        @NativeType("GLint const *") long length);

    void glCompileShader(@NativeType("GLuint") int shader);

    void glLinkProgram(@NativeType("GLuint") int program);

    void glUseProgram(@NativeType("GLuint") int program);

    @NativeType("void")
    int glGetShaderi(@NativeType("GLuint") int shader, @NativeType("GLenum") int pname);

    @NativeType("void")
    int glGetProgrami(@NativeType("GLuint") int program, @NativeType("GLenum") int pname);

    @NativeType("void")
    String glGetShaderInfoLog(@NativeType("GLuint") int shader);

    @NativeType("void")
    String glGetProgramInfoLog(@NativeType("GLuint") int program);

    void glEnableVertexAttribArray(@NativeType("GLuint") int index);

    void glVertexAttribPointer(@NativeType("GLuint") int index, @NativeType("GLint") int size,
                               @NativeType("GLenum") int type, @NativeType("GLboolean") boolean normalized,
                               @NativeType("GLsizei") int stride, @NativeType("void const *") long pointer);

    void glVertexAttribIPointer(@NativeType("GLuint") int index, @NativeType("GLint") int size,
                                @NativeType("GLenum") int type, @NativeType("GLsizei") int stride,
                                @NativeType("void const *") long pointer);

    @NativeType("void")
    int glGenVertexArrays();

    void glDeleteVertexArrays(@NativeType("GLuint const *") int array);

    void glBindVertexArray(@NativeType("GLuint") int array);

    @NativeType("void")
    int glGenFramebuffers();

    void glDeleteFramebuffers(@NativeType("GLuint const *") int framebuffer);

    void glBindFramebuffer(@NativeType("GLenum") int target, @NativeType("GLuint") int framebuffer);

    void glBindBufferBase(@NativeType("GLenum") int target, @NativeType("GLuint") int index,
                          @NativeType("GLuint") int buffer);

    @NativeType("void")
    int glGenRenderbuffers();

    void glDeleteRenderbuffers(@NativeType("GLuint const *") int renderbuffer);

    void glBindRenderbuffer(@NativeType("GLenum") int target, @NativeType("GLuint") int renderbuffer);

    void glRenderbufferStorage(@NativeType("GLenum") int target, @NativeType("GLenum") int internalformat,
                               @NativeType("GLsizei") int width, @NativeType("GLsizei") int height);

    void glRenderbufferStorageMultisample(@NativeType("GLenum") int target, @NativeType("GLsizei") int samples,
                                          @NativeType("GLenum") int internalformat, @NativeType("GLsizei") int width,
                                          @NativeType("GLsizei") int height);

    @NativeType("void *")
    long glMapBufferRange(@NativeType("GLenum") int target, @NativeType("GLintptr") long offset,
                          @NativeType("GLsizeiptr") long length, @NativeType("GLbitfield") int access);

    void glDrawArraysInstanced(@NativeType("GLenum") int mode, @NativeType("GLint") int first,
                               @NativeType("GLsizei") int count, @NativeType("GLsizei") int instancecount);

    void glDrawElementsInstanced(@NativeType("GLenum") int mode, @NativeType("GLsizei") int count,
                                 @NativeType("GLenum") int type, @NativeType("void const *") long indices,
                                 @NativeType("GLsizei") int instancecount);

    @NativeType("GLsync")
    long glFenceSync(@NativeType("GLenum") int condition, @NativeType("GLbitfield") int flags);

    void glDeleteSync(@NativeType("GLsync") long sync);

    @NativeType("GLenum")
    int glClientWaitSync(@NativeType("GLsync") long sync, @NativeType("GLbitfield") int flags,
                         @NativeType("GLuint64") long timeout);

    @NativeType("void")
    int glGenSamplers();

    void glDeleteSamplers(@NativeType("GLuint const *") int sampler);

    void glBindSampler(@NativeType("GLuint") int unit, @NativeType("GLuint") int sampler);

    void glSamplerParameteri(@NativeType("GLuint") int sampler, @NativeType("GLenum") int pname,
                             @NativeType("GLint") int param);

    void glSamplerParameterf(@NativeType("GLuint") int sampler, @NativeType("GLenum") int pname,
                             @NativeType("GLfloat") float param);

    void glVertexAttribDivisor(@NativeType("GLuint") int index, @NativeType("GLuint") int divisor);
}
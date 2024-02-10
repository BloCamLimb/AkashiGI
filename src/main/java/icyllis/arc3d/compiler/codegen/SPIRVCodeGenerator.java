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

package icyllis.arc3d.compiler.codegen;

import icyllis.arc3d.compiler.Context;
import icyllis.arc3d.compiler.Position;
import icyllis.arc3d.compiler.tree.*;
import icyllis.arc3d.core.MathUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.lwjgl.BufferUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.IdentityHashMap;

import static org.lwjgl.util.spvc.Spv.*;

/**
 * SPIR-V code generator for OpenGL 4.6 and Vulkan 1.1.
 */
public final class SPIRVCodeGenerator extends CodeGenerator {

    public enum SPIRVVersion {
        /**
         * SPIR-V version 1.0 for OpenGL 4.6.
         */
        VERSION_100(0x00010000),
        /**
         * SPIR-V version 1.3 for Vulkan 1.1.
         */
        VERSION_130(0x00010300);

        public final int mVersionNumber;

        SPIRVVersion(int versionNumber) {
            mVersionNumber = versionNumber;
        }
    }

    public final SPIRVVersion mOutputVersion;

    private interface Output {
        // write a 4-byte word
        void writeWord(int word);

        // write a sequence of 4-byte words
        void writeWords(int[] words, int size);

        // write a string as UTF-8 encoded, null-terminated and 4-byte aligned in LITTLE-ENDIAN order
        // however, our compiler only allows ASCII characters
        void writeString8(String s);
    }

    private final Output mMainOutput = new Output() {
        @Override
        public void writeWord(int word) {
            grow(mBuffer.limit() + 4);
            mBuffer.putInt(word);
        }

        @Override
        public void writeWords(int[] words, int size) {
            grow(mBuffer.limit() + (size << 2));
            mBuffer.asIntBuffer().put(words, 0, size);
        }

        @Override
        public void writeString8(String s) {
            int len = s.length();
            ByteBuffer buffer = grow(mBuffer.limit() +
                    MathUtil.align4(len + 1)); // +1 null-terminator
            int word = 0;
            int shift = 0;
            for (int i = 0; i < len; i++) {
                char c = s.charAt(i);
                if (c == 0 || c >= 0x80) {
                    mContext.error(Position.NO_POS, "invalid character '" + c + "'");
                }
                word |= c << shift;
                shift += 8;
                if (shift == 32) {
                    buffer.putInt(word);
                    word = 0;
                    shift = 0;
                }
            }
            // null-terminator and padding
            buffer.putInt(word);
        }
    };

    private static class WordBuffer implements Output {
        int[] a;
        int size;

        WordBuffer() {
            a = new int[16];
        }

        @Override
        public void writeWord(int word) {
            int s = size;
            grow(s + 1)[s] = word;
            size = s + 1;
        }

        @Override
        public void writeWords(int[] words, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeString8(String s) {
            throw new UnsupportedOperationException();
        }

        private int[] grow(int minCapacity) {
            if (minCapacity > a.length) {
                // double the buffer, overflow will throw exception
                int newCapacity = Math.max(minCapacity, a.length << 1);
                a = Arrays.copyOf(a, newCapacity);
            }
            return a;
        }
    }

    private WordBuffer mConstantBuffer;
    private WordBuffer mDecorationBuffer;

    private IdentityHashMap<Type, Integer> mStructTable = new IdentityHashMap<>();
    private IdentityHashMap<FunctionDecl, Integer> mFunctionTable = new IdentityHashMap<>();
    private IdentityHashMap<Variable, Integer> mVariableTable = new IdentityHashMap<>();

    private IntSet mCapabilities = new IntOpenHashSet(16, 0.5f);

    // id 0 is reserved
    private int mIdCount = 1;

    public SPIRVCodeGenerator(Context context,
                              TranslationUnit translationUnit,
                              SPIRVVersion outputVersion) {
        super(context, translationUnit);
        mOutputVersion = outputVersion;
    }

    @Nonnull
    @Override
    public ByteBuffer generateCode() {
        assert mContext.getErrorHandler().getNumErrors() == 0;
        // Header
        // 0 - magic number
        // 1 - version number
        // 2 - generator's magic number (Arc 3D is not registered, so this is zero)
        // 3 - bound (set later)
        // 4 - reserved (always zero)
        mBuffer = BufferUtils.createByteBuffer(1024)
                .putInt(SpvMagicNumber)
                .putInt(mOutputVersion.mVersionNumber)
                .putInt(0x00000000)
                .putInt(0)
                .putInt(0);

        writeInstructions();

        ByteBuffer buffer = mBuffer.putInt(12, mIdCount); // set bound
        mBuffer = null;
        return buffer.flip();
    }

    private int nextId(@Nonnull Type type) {
        return nextId(type.getComponentType().getScalarWidth() == 16);
    }

    private int nextId(boolean relaxed) {
        if (relaxed) {

        }
        return nextId();
    }

    private int nextId() {
        return mIdCount++;
    }

    private void writeLayout(@Nonnull Layout layout, int target, int pos) {
        boolean isPushConstant = (layout.layoutFlags() & Layout.kPushConstant_LayoutFlag) != 0;
        if (layout.mLocation >= 0) {
            writeInstruction(SpvOpDecorate, target, SpvDecorationLocation,
                    layout.mLocation, mDecorationBuffer);
        }
        if (layout.mComponent >= 0) {
            writeInstruction(SpvOpDecorate, target, SpvDecorationComponent,
                    layout.mComponent, mDecorationBuffer);
        }
        if (layout.mIndex >= 0) {
            writeInstruction(SpvOpDecorate, target, SpvDecorationIndex,
                    layout.mIndex, mDecorationBuffer);
        }
        if (layout.mBinding >= 0) {
            if (isPushConstant) {
                mContext.error(pos, "Can't apply 'binding' to push constants");
            } else {
                writeInstruction(SpvOpDecorate, target, SpvDecorationBinding,
                        layout.mBinding, mDecorationBuffer);
            }
        }
        if (layout.mSet >= 0) {
            if (isPushConstant) {
                mContext.error(pos, "Can't apply 'set' to push constants");
            } else {
                writeInstruction(SpvOpDecorate, target, SpvDecorationDescriptorSet,
                        layout.mSet, mDecorationBuffer);
            }
        }
        if (layout.mInputAttachmentIndex >= 0) {
            writeInstruction(SpvOpDecorate, target, SpvDecorationInputAttachmentIndex,
                    layout.mInputAttachmentIndex, mDecorationBuffer);
            mCapabilities.add(SpvCapabilityInputAttachment);
        }
        if (layout.mBuiltin >= 0) {
            writeInstruction(SpvOpDecorate, target, SpvDecorationBuiltIn,
                    layout.mBuiltin, mDecorationBuffer);
        }
    }

    private void writeInstructions() {

    }

    private void writeOpcode(int opcode, int count, Output output) {
        output.writeWord((count << 16) | opcode);
    }

    private void writeInstruction(int opcode, Output output) {
        writeOpcode(opcode, 1, output);
    }

    private void writeInstruction(int opcode, int word1, Output output) {
        writeOpcode(opcode, 2, output);
        output.writeWord(word1);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  Output output) {
        writeOpcode(opcode, 3, output);
        output.writeWord(word1);
        output.writeWord(word2);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  int word3, Output output) {
        writeOpcode(opcode, 4, output);
        output.writeWord(word1);
        output.writeWord(word2);
        output.writeWord(word3);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  int word3, int word4, Output output) {
        writeOpcode(opcode, 5, output);
        output.writeWord(word1);
        output.writeWord(word2);
        output.writeWord(word3);
        output.writeWord(word4);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  int word3, int word4, int word5,
                                  Output output) {
        writeOpcode(opcode, 6, output);
        output.writeWord(word1);
        output.writeWord(word2);
        output.writeWord(word3);
        output.writeWord(word4);
        output.writeWord(word5);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  int word3, int word4, int word5,
                                  int word6, Output output) {
        writeOpcode(opcode, 7, output);
        output.writeWord(word1);
        output.writeWord(word2);
        output.writeWord(word3);
        output.writeWord(word4);
        output.writeWord(word5);
        output.writeWord(word6);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  int word3, int word4, int word5,
                                  int word6, int word7, Output output) {
        writeOpcode(opcode, 8, output);
        output.writeWord(word1);
        output.writeWord(word2);
        output.writeWord(word3);
        output.writeWord(word4);
        output.writeWord(word5);
        output.writeWord(word6);
        output.writeWord(word7);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  int word3, int word4, int word5,
                                  int word6, int word7, int word8,
                                  Output output) {
        writeOpcode(opcode, 9, output);
        output.writeWord(word1);
        output.writeWord(word2);
        output.writeWord(word3);
        output.writeWord(word4);
        output.writeWord(word5);
        output.writeWord(word6);
        output.writeWord(word7);
        output.writeWord(word8);
    }
}

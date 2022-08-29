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

package icyllis.arcticgi.test;

import icyllis.arcticgi.core.MathUtil;
import icyllis.arcticgi.core.Matrix4;
import org.lwjgl.system.MemoryUtil;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Fork(2)
@Threads(2)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
public class TestBenchmark {

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder()
                .include(TestBenchmark.class.getSimpleName())
                .shouldFailOnError(true).shouldDoGC(true)
                .jvmArgs("-XX:+UseFMA")
                .build())
                .run();
    }

    private final Matrix4 mMatrix = Matrix4.identity();
    {
        mMatrix.preRotate(MathUtil.PI_O_3, MathUtil.PI_O_6, MathUtil.PI_O_4);
    }
    private final long mData = MemoryUtil.nmemAlignedAllocChecked(8, 64);

    @Benchmark
    public void uploadMethod1() {
        mMatrix.put(mData);
    }
}

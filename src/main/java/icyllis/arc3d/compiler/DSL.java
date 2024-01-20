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

package icyllis.arc3d.compiler;

import java.util.Objects;

public final class DSL {

    /**
     * Starts the DSL on the current thread for compiling programs.
     */
    public static void start(ExecutionModel model, CompileOptions options, SharedLibrary parent) {
        if (ThreadContext.isActive()) {
            throw new IllegalStateException("DSL is already started");
        }
        new ThreadContext(model, options, parent, false);
    }

    /**
     * Starts the DSL on the current thread for compiling shared libraries (include files).
     */
    public static void startLibrary(ExecutionModel model, CompileOptions options, SharedLibrary parent) {
        if (ThreadContext.isActive()) {
            throw new IllegalStateException("DSL is already started");
        }
        new ThreadContext(model, options, parent, true);
    }

    /**
     * Ends the DSL on the current thread.
     */
    public static void end() {
        ThreadContext.getInstance().end();
    }

    /**
     * Returns the ErrorHandler which will be notified of any errors that occur during DSL calls.
     * The default error handler throws RuntimeException on any error.
     */
    public static ErrorHandler getErrorHandler() {
        return ThreadContext.getInstance().mErrorHandler;
    }

    /**
     * Installs an ErrorHandler which will be notified of any errors that occur during DSL calls.
     */
    public static void setErrorHandler(ErrorHandler errorHandler) {
        ThreadContext.getInstance().mErrorHandler = Objects.requireNonNull(errorHandler);
    }
}
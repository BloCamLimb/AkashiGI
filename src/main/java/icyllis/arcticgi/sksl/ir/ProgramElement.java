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

package icyllis.arcticgi.sksl.ir;

/**
 * Represents a top-level element (e.g. function or global variable) in a program.
 */
public abstract class ProgramElement extends IRNode {

    public static final int Kind_First = 0;
    public static final int
            Kind_Extension = Kind_First,
            Kind_Function = Kind_First + 1,
            Kind_FunctionPrototype = Kind_First + 2,
            Kind_GlobalVar = Kind_First + 3,
            Kind_InterfaceBlock = Kind_First + 4,
            Kind_Modifiers = Kind_First + 5,
            Kind_StructDefinition = Kind_First + 6;
    public static final int Kind_Last = Kind_StructDefinition;

    protected ProgramElement(int start, int end, int kind) {
        super(start, end, kind);
        assert (kind >= Kind_First && kind <= Kind_Last);
    }

    public final int kind() {
        return mKind;
    }
}
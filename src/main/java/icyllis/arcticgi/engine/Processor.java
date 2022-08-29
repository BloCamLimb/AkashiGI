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

package icyllis.arcticgi.engine;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * Provides custom shader code to the Arctic shading pipeline. Processor objects <em>must</em> be
 * immutable: after being constructed, their fields may not change.
 */
@Immutable
public abstract class Processor {

    /**
     * Class IDs.
     */
    public static final int
            Null_ClassID = 0, // Reserved ID for missing (null) processors
            CircularRRect_Geom_ClassID = 1,
            Circle_Geom_ClassID = 2,
            RoundRect_Geom_ClassID = 3;

    protected final int mClassID;

    protected Processor(int classID) {
        mClassID = classID;
    }

    /**
     * Human-meaningful string to identify this processor; may be embedded in generated shader
     * code and must be a legal SkSL identifier prefix.
     */
    @Nonnull
    public abstract String name();

    /**
     * @return unique ID that identifies this processor class.
     */
    public final int classID() {
        return mClassID;
    }
}
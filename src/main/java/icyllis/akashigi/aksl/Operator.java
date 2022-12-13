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

package icyllis.akashigi.aksl;

import icyllis.akashigi.aksl.ir.Type;

/**
 * Operators.
 */
public enum Operator {
    ADD,            // +
    SUB,            // -
    MUL,            // *
    DIV,            // /
    MOD,            // %
    SHL,            // <<
    SHR,            // >>
    LOGICAL_NOT,    // !
    LOGICAL_AND,    // &&
    LOGICAL_OR,     // ||
    LOGICAL_XOR,    // ^^
    BITWISE_NOT,    // ~
    BITWISE_AND,    // &
    BITWISE_OR,     // |
    BITWISE_XOR,    // ^
    ASSIGN,         // =
    EQ,             // ==
    NE,             // !=
    LT,             // <
    GT,             // >
    LE,             // <=
    GE,             // >=
    ADD_ASSIGN,     // +=
    SUB_ASSIGN,     // -=
    MUL_ASSIGN,     // *=
    DIV_ASSIGN,     // /=
    MOD_ASSIGN,     // %=
    SHL_ASSIGN,     // <<=
    SHR_ASSIGN,     // >>=
    AND_ASSIGN,     // &=
    OR_ASSIGN,      // |=
    XOR_ASSIGN,     // ^=
    INC,            // ++
    DEC,            // --
    COMMA;          // ,

    /**
     * {@code Precedence}.
     *
     * <h5>Enum values:</h5>
     */
    public static final int
            PRECEDENCE_PARENTHESES = 1,     // ()
            PRECEDENCE_POSTFIX = 2,         // ++ --
            PRECEDENCE_PREFIX = 3,          // ++ -- + - ~ !
            PRECEDENCE_MULTIPLICATIVE = 4,  // * / %
            PRECEDENCE_ADDITIVE = 5,        // + -
            PRECEDENCE_SHIFT = 6,           // << >>
            PRECEDENCE_RELATIONAL = 7,      // < > <= >=
            PRECEDENCE_EQUALITY = 8,        // == !=
            PRECEDENCE_BITWISE_AND = 9,     // &
            PRECEDENCE_BITWISE_XOR = 10,    // ^
            PRECEDENCE_BITWISE_OR = 11,     // |
            PRECEDENCE_LOGICAL_AND = 12,    // &&
            PRECEDENCE_LOGICAL_XOR = 13,    // ^^
            PRECEDENCE_LOGICAL_OR = 14,     // ||
            PRECEDENCE_CONDITIONAL = 15,    // ?:
            PRECEDENCE_ASSIGNMENT = 16,     // = += -= *= /= %= <<= >>= &= ^= |=
            PRECEDENCE_SEQUENCE = 17,       // ,
            PRECEDENCE_TOP_LEVEL = PRECEDENCE_SEQUENCE;

    Operator() {
    }

    public int getBinaryPrecedence() {
        return switch (this) {
            case MUL,
                    DIV,
                    MOD -> PRECEDENCE_MULTIPLICATIVE;
            case ADD,
                    SUB -> PRECEDENCE_ADDITIVE;
            case SHL,
                    SHR -> PRECEDENCE_SHIFT;
            case LT,
                    GT,
                    LE,
                    GE -> PRECEDENCE_RELATIONAL;
            case EQ,
                    NE -> PRECEDENCE_EQUALITY;
            case BITWISE_AND -> PRECEDENCE_BITWISE_AND;
            case BITWISE_XOR -> PRECEDENCE_BITWISE_XOR;
            case BITWISE_OR -> PRECEDENCE_BITWISE_OR;
            case LOGICAL_AND -> PRECEDENCE_LOGICAL_AND;
            case LOGICAL_XOR -> PRECEDENCE_LOGICAL_XOR;
            case LOGICAL_OR -> PRECEDENCE_LOGICAL_OR;
            case ASSIGN,
                    ADD_ASSIGN,
                    SUB_ASSIGN,
                    MUL_ASSIGN,
                    DIV_ASSIGN,
                    MOD_ASSIGN,
                    SHL_ASSIGN,
                    SHR_ASSIGN,
                    AND_ASSIGN,
                    XOR_ASSIGN,
                    OR_ASSIGN -> PRECEDENCE_ASSIGNMENT;
            case COMMA -> PRECEDENCE_SEQUENCE;
            default -> throw new IllegalArgumentException();
        };
    }

    /**
     * Returns the operator name surrounded by the expected whitespace for a tidy binary expression.
     */
    public String operatorName() {
        return switch (this) {
            case ADD -> " + ";
            case SUB -> " - ";
            case MUL -> " * ";
            case DIV -> " / ";
            case MOD -> " % ";
            case SHL -> " << ";
            case SHR -> " >> ";
            case LOGICAL_NOT -> "!";
            case LOGICAL_AND -> " && ";
            case LOGICAL_OR -> " || ";
            case LOGICAL_XOR -> " ^^ ";
            case BITWISE_NOT -> "~";
            case BITWISE_AND -> " & ";
            case BITWISE_OR -> " | ";
            case BITWISE_XOR -> " ^ ";
            case ASSIGN -> " = ";
            case EQ -> " == ";
            case NE -> " != ";
            case LT -> " < ";
            case GT -> " > ";
            case LE -> " <= ";
            case GE -> " >= ";
            case ADD_ASSIGN -> " += ";
            case SUB_ASSIGN -> " -= ";
            case MUL_ASSIGN -> " *= ";
            case DIV_ASSIGN -> " /= ";
            case MOD_ASSIGN -> " %= ";
            case SHL_ASSIGN -> " <<= ";
            case SHR_ASSIGN -> " >>= ";
            case AND_ASSIGN -> " &= ";
            case OR_ASSIGN -> " |= ";
            case XOR_ASSIGN -> " ^= ";
            case INC -> "++";
            case DEC -> "--";
            case COMMA -> ", ";
        };
    }

    /**
     * Returns the operator name without any surrounding whitespace.
     */
    public String tightOperatorName() {
        return switch (this) {
            case ADD -> "+";
            case SUB -> "-";
            case MUL -> "*";
            case DIV -> "/";
            case MOD -> "%";
            case SHL -> "<<";
            case SHR -> ">>";
            case LOGICAL_NOT -> "!";
            case LOGICAL_AND -> "&&";
            case LOGICAL_OR -> "||";
            case LOGICAL_XOR -> "^^";
            case BITWISE_NOT -> "~";
            case BITWISE_AND -> "&";
            case BITWISE_OR -> "|";
            case BITWISE_XOR -> "^";
            case ASSIGN -> "=";
            case EQ -> "==";
            case NE -> "!=";
            case LT -> "<";
            case GT -> ">";
            case LE -> "<=";
            case GE -> ">=";
            case ADD_ASSIGN -> "+=";
            case SUB_ASSIGN -> "-=";
            case MUL_ASSIGN -> "*=";
            case DIV_ASSIGN -> "/=";
            case MOD_ASSIGN -> "%=";
            case SHL_ASSIGN -> "<<=";
            case SHR_ASSIGN -> ">>=";
            case AND_ASSIGN -> "&=";
            case OR_ASSIGN -> "|=";
            case XOR_ASSIGN -> "^=";
            case INC -> "++";
            case DEC -> "--";
            case COMMA -> ",";
        };
    }

    public boolean isEquality() {
        return this == EQ || this == NE;
    }

    // Returns true if op is '=' or any compound assignment operator ('+=', '-=', etc.)
    public boolean isAssignment() {
        return switch (this) {
            case ASSIGN,
                    ADD_ASSIGN,
                    SUB_ASSIGN,
                    MUL_ASSIGN,
                    DIV_ASSIGN,
                    MOD_ASSIGN,
                    SHL_ASSIGN,
                    SHR_ASSIGN,
                    OR_ASSIGN,
                    XOR_ASSIGN,
                    AND_ASSIGN -> true;
            default -> false;
        };
    }

    // Given a compound assignment operator, returns the non-assignment version of the operator
    // (e.g. '+=' becomes '+')
    public Operator removeAssignment() {
        return switch (this) {
            case ADD_ASSIGN -> ADD;
            case SUB_ASSIGN -> SUB;
            case MUL_ASSIGN -> MUL;
            case DIV_ASSIGN -> DIV;
            case MOD_ASSIGN -> MOD;
            case SHL_ASSIGN -> SHL;
            case SHR_ASSIGN -> SHR;
            case OR_ASSIGN -> BITWISE_OR;
            case XOR_ASSIGN -> BITWISE_XOR;
            case AND_ASSIGN -> BITWISE_AND;
            default -> this;
        };
    }

    /**
     * Defines the set of relational (comparison) operators:
     * <  <=  >  >=
     */
    public boolean isRelational() {
        return switch (this) {
            case LT,
                    GT,
                    LE,
                    GE -> true;
            default -> false;
        };
    }

    /**
     * Defines the set of operators which are only valid on integral types:
     * <<  <<=  >>  >>=  &  &=  |  |=  ^  ^=  %  %=
     */
    public boolean isOnlyValidForIntegralTypes() {
        return switch (this) {
            case SHL,
                    SHR,
                    BITWISE_AND,
                    BITWISE_OR,
                    BITWISE_XOR,
                    MOD,
                    SHL_ASSIGN,
                    SHR_ASSIGN,
                    AND_ASSIGN,
                    OR_ASSIGN,
                    XOR_ASSIGN,
                    MOD_ASSIGN -> true;
            default -> false;
        };
    }

    /**
     * Defines the set of operators which perform vector/matrix math.
     * +  +=  -  -=  *  *=  /  /=  %  %=  <<  <<=  >>  >>=  &  &=  |  |=  ^  ^=
     */
    public boolean isValidForMatrixOrVector() {
        return switch (this) {
            case ADD,
                    SUB,
                    MUL,
                    DIV,
                    MOD,
                    SHL,
                    SHR,
                    BITWISE_AND,
                    BITWISE_OR,
                    BITWISE_XOR,
                    ADD_ASSIGN,
                    SUB_ASSIGN,
                    MUL_ASSIGN,
                    DIV_ASSIGN,
                    MOD_ASSIGN,
                    SHL_ASSIGN,
                    SHR_ASSIGN,
                    AND_ASSIGN,
                    OR_ASSIGN,
                    XOR_ASSIGN -> true;
            default -> false;
        };
    }

    private boolean isMatrixMultiply(Type left, Type right) {
        if (this != MUL && this != MUL_ASSIGN) {
            return false;
        }
        if (left.isMatrix()) {
            return right.isMatrix() || right.isVector();
        }
        return left.isVector() && right.isMatrix();
    }

    /**
     * Determines the operand and result types of a binary expression. Returns true if the
     * expression is legal, false otherwise. If false, the values of the out parameters are
     * undefined.
     *
     * @param outTypes left type, right type, result type
     */
    public boolean determineBinaryType(ThreadContext context,
                                       Type left,
                                       Type right,
                                       Type[] outTypes) {
        assert (outTypes.length >= 3);
        switch (this) {
            case ASSIGN -> {  // left = right
                if (left.isVoid()) {
                    return false;
                }
                outTypes[0] = left;
                outTypes[1] = left;
                outTypes[2] = left;
                return right.canCoerceTo(left);
            }
            case EQ, // left == right
                    NE -> {  // left != right
                if (left.isVoid() || left.isOpaque()) {
                    return false;
                }
                int rightToLeft = right.coercionCost(left),
                        leftToRight = left.coercionCost(right);
                if (Type.CoercionCost.compare(rightToLeft, leftToRight) < 0) {
                    if (Type.CoercionCost.isPossible(rightToLeft)) {
                        outTypes[0] = left;
                        outTypes[1] = left;
                        outTypes[2] = context.getTypes().mBool;
                        return true;
                    }
                } else {
                    if (Type.CoercionCost.isPossible(leftToRight)) {
                        outTypes[0] = right;
                        outTypes[1] = right;
                        outTypes[2] = context.getTypes().mBool;
                        return true;
                    }
                }
                return false;
            }
            case LOGICAL_OR, // left || right
                    LOGICAL_AND,  // left && right
                    LOGICAL_XOR -> {  // left ^^ right
                outTypes[0] = context.getTypes().mBool;
                outTypes[1] = context.getTypes().mBool;
                outTypes[2] = context.getTypes().mBool;
                return left.canCoerceTo(context.getTypes().mBool) &&
                        right.canCoerceTo(context.getTypes().mBool);
            }
            case COMMA -> {  // left, right
                if (left.isOpaque() || right.isOpaque()) {
                    return false;
                }
                outTypes[0] = left;
                outTypes[1] = right;
                outTypes[2] = right;
                return true;
            }
        }

        // Boolean types only support the operators listed above (, = == != || && ^^).
        // If we've gotten this far with a boolean, we have an unsupported operator.
        final Type leftComponentType = left.getComponentType();
        final Type rightComponentType = right.getComponentType();
        if (leftComponentType.isBoolean() || rightComponentType.isBoolean()) {
            return false;
        }

        boolean isAssignment = isAssignment();
        if (isMatrixMultiply(left, right)) {  // left * right
            // Determine final component type.
            if (!determineBinaryType(context, left.getComponentType(), right.getComponentType(),
                    outTypes)) {
                return false;
            }
            // Convert component type to compound.
            outTypes[0] = outTypes[0].toCompound(context, left.cols(), left.rows());
            outTypes[1] = outTypes[1].toCompound(context, right.cols(), right.rows());
            int leftColumns = left.cols(), leftRows = left.rows();
            int rightColumns = right.cols(), rightRows = right.rows();
            if (right.isVector()) {
                // `matrix * vector` treats the vector as a column vector; we need to transpose it.
                int t = leftColumns;
                leftColumns = rightColumns;
                rightColumns = t;
                assert (rightColumns == 1);
            }
            if (rightColumns > 1) {
                outTypes[2] = outTypes[2].toCompound(context, rightColumns, leftRows);
            } else {
                // The result was a column vector. Transpose it back to a row.
                outTypes[2] = outTypes[2].toCompound(context, leftRows, rightColumns);
            }
            if (isAssignment && (outTypes[2].cols() != leftColumns ||
                    outTypes[2].rows() != leftRows)) {
                return false;
            }
            return leftColumns == rightRows;
        }

        boolean leftIsVectorOrMatrix = left.isVector() || left.isMatrix();
        boolean validMatrixOrVectorOp = isValidForMatrixOrVector();

        if (leftIsVectorOrMatrix && validMatrixOrVectorOp && right.isScalar()) {
            // Determine final component type.
            if (!determineBinaryType(context, left.getComponentType(), right,
                    outTypes)) {
                return false;
            }
            // Convert component type to compound.
            outTypes[0] = outTypes[0].toCompound(context, left.cols(), left.rows());
            if (!isRelational()) {
                outTypes[2] = outTypes[2].toCompound(context, left.cols(), left.rows());
            }
            return true;
        }

        boolean rightIsVectorOrMatrix = right.isVector() || right.isMatrix();

        if (!isAssignment && rightIsVectorOrMatrix && validMatrixOrVectorOp && left.isScalar()) {
            // Determine final component type.
            if (!determineBinaryType(context, left, right.getComponentType(),
                    outTypes)) {
                return false;
            }
            // Convert component type to compound.
            outTypes[1] = outTypes[1].toCompound(context, right.cols(), right.rows());
            if (!isRelational()) {
                outTypes[2] = outTypes[2].toCompound(context, right.cols(), right.rows());
            }
            return true;
        }

        int rightToLeftCost = right.coercionCost(left);
        int leftToRightCost = isAssignment ? Type.CoercionCost.impossible()
                : left.coercionCost(right);

        if ((left.isScalar() && right.isScalar()) || (leftIsVectorOrMatrix && validMatrixOrVectorOp)) {
            if (isOnlyValidForIntegralTypes()) {
                if (!leftComponentType.isInteger() || !rightComponentType.isInteger()) {
                    return false;
                }
            }
            if (Type.CoercionCost.isPossible(rightToLeftCost) &&
                    Type.CoercionCost.compare(rightToLeftCost, leftToRightCost) < 0) {
                // Right-to-Left conversion is possible and cheaper
                outTypes[0] = left;
                outTypes[1] = left;
                outTypes[2] = left;
            } else if (Type.CoercionCost.isPossible(leftToRightCost)) {
                // Left-to-Right conversion is possible (and at least as cheap as Right-to-Left)
                outTypes[0] = right;
                outTypes[1] = right;
                outTypes[2] = right;
            } else {
                return false;
            }
            if (isRelational()) {
                outTypes[2] = context.getTypes().mBool;
            }
            return true;
        }
        return false;
    }
}
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

import icyllis.arc3d.compiler.parser.Lexer;
import icyllis.arc3d.compiler.parser.Token;
import icyllis.arc3d.compiler.tree.*;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Consumes AkSL text and invokes DSL functions to instantiate the program.
 */
public class Parser {

    public static final byte
            LAYOUT_TOKEN_LOCATION = 0,
            LAYOUT_TOKEN_OFFSET = 1,
            LAYOUT_TOKEN_BINDING = 2,
            LAYOUT_TOKEN_INDEX = 3,
            LAYOUT_TOKEN_SET = 4,
            LAYOUT_TOKEN_BUILTIN = 5,
            LAYOUT_TOKEN_INPUT_ATTACHMENT_INDEX = 6,
            LAYOUT_TOKEN_ORIGIN_UPPER_LEFT = 7,
            LAYOUT_TOKEN_BLEND_SUPPORT_ALL_EQUATIONS = 8,
            LAYOUT_TOKEN_PUSH_CONSTANT = 9,
            LAYOUT_TOKEN_COLOR = 10;

    private final Compiler mCompiler;

    private final ExecutionModel mModel;
    private final CompileOptions mOptions;

    private final String mSource;
    private final Lexer mLexer;

    private final LongList mPushback = new LongArrayList();

    public Parser(Compiler compiler, ExecutionModel model, CompileOptions options, String source) {
        // ideally we can break long text into pieces, but shader code should not be too long
        if (source.length() > 0x7FFFFE) {
            throw new IllegalArgumentException("Source code is too long, " + source.length() + " > 8,388,606");
        }
        mCompiler = Objects.requireNonNull(compiler);
        mOptions = Objects.requireNonNull(options);
        mModel = Objects.requireNonNull(model);
        mSource = source;
        mLexer = new Lexer(source);
    }

    @Nullable
    public Program parse(SharedLibrary parent) {
        Objects.requireNonNull(parent);
        ErrorHandler errorHandler = mCompiler.getErrorHandler();
        DSL.start(mModel, mOptions, parent);
        DSL.setErrorHandler(errorHandler);
        errorHandler.setSource(mSource);
        //TODO declarations
        errorHandler.setSource(null);
        DSL.end();
        return null;
    }

    @Nullable
    public SharedLibrary parseLibrary(SharedLibrary parent) {
        Objects.requireNonNull(parent);
        ErrorHandler errorHandler = mCompiler.getErrorHandler();
        DSL.startLibrary(mModel, mOptions, parent);
        DSL.setErrorHandler(errorHandler);
        errorHandler.setSource(mSource);
        //TODO declarations
        final SharedLibrary result;
        if (DSL.getErrorHandler().getNumErrors() == 0) {
            result = new SharedLibrary();
            result.mParent = parent;
            result.mSymbols = ThreadContext.getInstance().getSymbolTable();
            result.mElements = ThreadContext.getInstance().getUniqueElements();
        } else {
            result = null;
        }
        errorHandler.setSource(null);
        DSL.end();
        return result;
    }

    /**
     * Return the next token, including whitespace tokens, from the parse stream.
     */
    private long nextRawToken() {
        final long token;
        if (!mPushback.isEmpty()) {
            // Retrieve the token from the pushback buffer.
            token = mPushback.removeLong(0);
        } else {
            // Fetch a token from the lexer.
            token = mLexer.next();

            if (Token.kind(token) == Lexer.TK_RESERVED) {
                error(token, "'" + text(token) + "' is a reserved keyword");
                // reduces additional follow-up errors
                return Token.replace(token, Lexer.TK_IDENTIFIER);
            }
        }
        return token;
    }

    // @formatter:off
    private static boolean isWhitespace(int kind) {
        return switch (kind) {
            case Lexer.TK_WHITESPACE,
                    Lexer.TK_LINE_COMMENT,
                    Lexer.TK_BLOCK_COMMENT -> true;
            default -> false;
        };
    }
    // @formatter:on

    /**
     * Return the next non-whitespace token from the parse stream.
     */
    // @formatter:off
    private long nextToken() {
        for (;;) {
            long token = nextRawToken();
            if (!isWhitespace(Token.kind(token))) {
                return token;
            }
        }
    }
    // @formatter:on

    /**
     * Push a token back onto the parse stream, so that it is the next one read. Only a single level
     * of pushback is supported (that is, it is an error to call pushback() twice in a row without
     * an intervening nextToken()).
     */
    private void pushback(long token) {
        mPushback.add(token);
    }

    /**
     * Returns the next non-whitespace token without consuming it from the stream.
     */
    private long peek() {
        if (mPushback.isEmpty()) {
            long token = nextToken();
            mPushback.add(token);
            return token;
        }
        return mPushback.getLong(0);
    }

    private boolean peek(int kind) {
        return Token.kind(peek()) == kind;
    }

    @Nonnull
    private String text(long token) {
        int offset = Token.offset(token);
        int length = Token.length(token);
        return mSource.substring(offset, offset + length);
    }

    private int position(long token) {
        int offset = Token.offset(token);
        return Position.range(offset, offset + Token.length(token));
    }

    private void error(long token, String msg) {
        int offset = Token.offset(token);
        error(offset, offset + Token.length(token), msg);
    }

    private void error(int start, int end, String msg) {
        ThreadContext.getInstance().error(start, end, msg);
    }

    // Returns the range from `start` to the current parse position.
    private int rangeFrom0(int startOffset) {
        int endOffset = mPushback.isEmpty()
                ? mLexer.offset()
                : Token.offset(mPushback.getLong(0));
        return Position.range(startOffset, endOffset);
    }

    private int rangeFrom(int startPos) {
        return rangeFrom0(Position.getStartOffset(startPos));
    }

    private int rangeFrom(long startToken) {
        return rangeFrom0(Token.offset(startToken));
    }

    /**
     * Checks to see if the next token is of the specified type. If so, stores it in result (if
     * result is non-null) and returns true. Otherwise, pushes it back and returns -1.
     */
    private long checkNext(int kind) {
        long next = peek();
        if (Token.kind(next) == kind) {
            return nextToken();
        }
        return -1;
    }

    /**
     * Behaves like checkNext(TK_IDENTIFIER), but also verifies that identifier is not a builtin
     * type. If the token was actually a builtin type, false is returned (the next token is not
     * considered to be an identifier).
     */
    private long checkIdentifier() {
        long next = peek();
        if (Token.kind(next) == Lexer.TK_IDENTIFIER &&
                !ThreadContext.getInstance().getSymbolTable().isBuiltinType(text(next))) {
            return nextToken();
        }
        return -1;
    }

    /**
     * Reads the next non-whitespace token and generates an error if it is not the expected type.
     * The {@code expected} string is part of the error message, which reads:
     * <p>
     * "expected [expected], but found '[actual text]'"
     */
    private long expect(int kind, String expected) {
        long next = nextToken();
        if (Token.kind(next) != kind) {
            String msg = "expected " + expected + ", but found '" + text(next) + "'";
            error(next, msg);
            throw new IllegalStateException(msg);
        }
        return next;
    }

    /**
     * Behaves like expect(TK_IDENTIFIER), but also verifies that identifier is not a type.
     * If the token was actually a type, generates an error message of the form:
     * <p>
     * "expected an identifier, but found type 'float2'"
     */
    private long expectIdentifier() {
        long token = expect(Lexer.TK_IDENTIFIER, "an identifier");
        if (ThreadContext.getInstance().getSymbolTable().isBuiltinType(text(token))) {
            String msg = "expected an identifier, but found type '" + text(token) + "'";
            error(token, msg);
            throw new IllegalStateException(msg);
        }
        return token;
    }

    /**
     * If the next token is a newline, consumes it and returns true. If not, returns false.
     */
    private boolean expectNewline() {
        long token = nextRawToken();
        if (Token.kind(token) == Lexer.TK_WHITESPACE) {
            // The lexer doesn't distinguish newlines from other forms of whitespace, so we check
            // for newlines by searching through the token text.
            String tokenText = text(token);
            if (tokenText.indexOf('\r') != -1 ||
                    tokenText.indexOf('\n') != -1) {
                return true;
            }
        }
        // We didn't find a newline.
        pushback(token);
        return false;
    }

    private boolean declaration() {
        long token = peek();
        if (Token.kind(token) == Lexer.TK_SEMICOLON) {
            nextToken();
            error(token, "expected a declaration, but found ';'");
            return false;
        }

        return false;
    }

    /**
     * LAYOUT LPAREN IDENTIFIER (EQ INT_LITERAL)? (COMMA IDENTIFIER (EQ INT_LITERAL)?)* RPAREN
     */
    private Layout layout() {
        if (checkNext(Lexer.TK_LAYOUT) == -1) {
            return null;
        }
        expect(Lexer.TK_LPAREN, "'('");
        int flags = 0;
        int location = 0;
        int component = 0;
        int index = 0;
        int binding = 0;
        int offset = 0;
        int align = 0;
        int set = 0;
        int inputAttachmentIndex = 0;
        int builtin = 0;
        for (;;) {
            long t = nextToken();
            String text = text(t);
            int pos = position(t);
            switch (text) {
                case "origin_upper_left" -> flags = Layout.flag(flags, Layout.kOriginUpperLeft_Flag, text, pos);
                case "pixel_center_integer" -> flags = Layout.flag(flags, Layout.kPixelCenterInteger_Flag, text, pos);
                case "early_fragment_tests" -> flags = Layout.flag(flags, Layout.kEarlyFragmentTests_Flag, text, pos);
                case "blend_support_all_equations" ->
                        flags = Layout.flag(flags, Layout.kBlendSupportAllEquations_Flag, text, pos);
                case "push_constant" -> flags = Layout.flag(flags, Layout.kPushConstant_Flag, text, pos);
            }
        }
    }

    private int layoutInt() {
        expect(Lexer.TK_EQ, "'='");

        return -1;
    }

    @Nullable
    private Expression operatorRight(Expression left, Operator op,
                                     java.util.function.Function<Parser, Expression> rightFn) {
        nextToken();
        Expression right = rightFn.apply(this);
        if (right == null) {
            return null;
        }
        int pos = Position.range(left.getStartOffset(), right.getEndOffset());
        Expression result = BinaryExpression.convert(pos, left, op, right);
        if (result != null) {
            return result;
        }
        return Poison.make(pos);
    }

    /**
     * <pre>{@literal
     * Expression
     *     : AssignmentExpression
     *     | Expression COMMA AssignmentExpression
     * }</pre>
     */
    @Nullable
    private Expression Expression() {
        Expression result = AssignmentExpression();
        if (result == null) {
            return null;
        }
        while (peek(Lexer.TK_COMMA)) {
            if ((result = operatorRight(result, Operator.COMMA,
                    Parser::AssignmentExpression)) == null) {
                return null;
            }
        }
        return result;
    }

    /**
     * <pre>{@literal
     * AssignmentExpression
     *     : ConditionalExpression
     *     | ConditionalExpression AssignmentOperator AssignmentExpression
     *
     * AssignmentOperator
     *     : EQ
     *     | PLUSEQ
     *     | MINUSEQ
     *     | STAREQ
     *     | SLASHEQ
     *     | PERCENTEQ
     *     | AMPEQ
     *     | PIPEEQ
     *     | CARETEQ
     *     | LTLTEQ
     *     | GTGTEQ
     * }</pre>
     */
    // @formatter:off
    @Nullable
    private Expression AssignmentExpression() {
        Expression result = ConditionalExpression();
        if (result == null) {
            return null;
        }
        for (;;) {
            Operator op = switch (Token.kind(peek())) {
                case Lexer.TK_EQ        -> Operator.    ASSIGN;
                case Lexer.TK_PLUSEQ    -> Operator.ADD_ASSIGN;
                case Lexer.TK_MINUSEQ   -> Operator.SUB_ASSIGN;
                case Lexer.TK_STAREQ    -> Operator.MUL_ASSIGN;
                case Lexer.TK_SLASHEQ   -> Operator.DIV_ASSIGN;
                case Lexer.TK_PERCENTEQ -> Operator.MOD_ASSIGN;
                case Lexer.TK_LTLTEQ    -> Operator.SHL_ASSIGN;
                case Lexer.TK_GTGTEQ    -> Operator.SHR_ASSIGN;
                case Lexer.TK_AMPEQ     -> Operator.AND_ASSIGN;
                case Lexer.TK_PIPEEQ    -> Operator. OR_ASSIGN;
                case Lexer.TK_CARETEQ   -> Operator.XOR_ASSIGN;
                default -> null;
            };
            if (op != null) {
                if ((result = operatorRight(result, op,
                        Parser::AssignmentExpression)) == null) {
                    return null;
                }
            } else {
                return result;
            }
        }
    }
    // @formatter:on

    @Nonnull
    private Expression expressionOrPoison(int pos, @Nullable Expression expr) {
        if (expr == null) {
            expr = Poison.make(pos);
        }
        return expr;
    }

    /**
     * <pre>{@literal
     * ConditionalExpression
     *     : LogicalOrExpression
     *     | LogicalOrExpression QUES Expression COLON AssignmentExpression
     * }</pre>
     */
    @Nullable
    private Expression ConditionalExpression() {
        Expression base = LogicalOrExpression();
        if (base == null) {
            return null;
        }
        if (!peek(Lexer.TK_QUES)) {
            return base;
        }
        nextToken();
        Expression whenTrue = Expression();
        if (whenTrue == null) {
            return null;
        }
        expect(Lexer.TK_COLON, "':'");
        Expression whenFalse = AssignmentExpression();
        if (whenFalse == null) {
            return null;
        }
        int pos = Position.range(base.getStartOffset(), whenFalse.getEndOffset());
        return expressionOrPoison(pos,
                ConditionalExpression.convert(pos, base, whenTrue, whenFalse));
    }

    /**
     * <pre>{@literal
     * LogicalOrExpression
     *     : LogicalXorExpression
     *     | LogicalOrExpression PIPEPIPE LogicalXorExpression
     * }</pre>
     */
    @Nullable
    private Expression LogicalOrExpression() {
        Expression result = LogicalXorExpression();
        if (result == null) {
            return null;
        }
        while (peek(Lexer.TK_PIPEPIPE)) {
            if ((result = operatorRight(result, Operator.LOGICAL_OR,
                    Parser::LogicalXorExpression)) == null) {
                return null;
            }
        }
        return result;
    }

    /**
     * <pre>{@literal
     * LogicalXorExpression
     *     : LogicalAndExpression
     *     | LogicalXorExpression CARETCARET LogicalAndExpression
     * }</pre>
     */
    @Nullable
    private Expression LogicalXorExpression() {
        Expression result = LogicalAndExpression();
        if (result == null) {
            return null;
        }
        while (peek(Lexer.TK_CARETCARET)) {
            if ((result = operatorRight(result, Operator.LOGICAL_XOR,
                    Parser::LogicalAndExpression)) == null) {
                return null;
            }
        }
        return result;
    }

    /**
     * <pre>{@literal
     * LogicalAndExpression
     *     : BitwiseOrExpression
     *     | LogicalAndExpression AMPAMP BitwiseOrExpression
     * }</pre>
     */
    @Nullable
    private Expression LogicalAndExpression() {
        Expression result = BitwiseOrExpression();
        if (result == null) {
            return null;
        }
        while (peek(Lexer.TK_AMPAMP)) {
            if ((result = operatorRight(result, Operator.LOGICAL_AND,
                    Parser::BitwiseOrExpression)) == null) {
                return null;
            }
        }
        return result;
    }

    /**
     * <pre>{@literal
     * BitwiseOrExpression
     *     : BitwiseXorExpression
     *     | BitwiseOrExpression PIPE BitwiseXorExpression
     * }</pre>
     */
    @Nullable
    private Expression BitwiseOrExpression() {
        Expression result = BitwiseXorExpression();
        if (result == null) {
            return null;
        }
        while (peek(Lexer.TK_PIPE)) {
            if ((result = operatorRight(result, Operator.BITWISE_OR,
                    Parser::BitwiseXorExpression)) == null) {
                return null;
            }
        }
        return result;
    }

    /**
     * <pre>{@literal
     * BitwiseXorExpression
     *     : BitwiseAndExpression
     *     | BitwiseXorExpression CARET BitwiseAndExpression
     * }</pre>
     */
    @Nullable
    private Expression BitwiseXorExpression() {
        Expression result = BitwiseAndExpression();
        if (result == null) {
            return null;
        }
        while (peek(Lexer.TK_CARET)) {
            if ((result = operatorRight(result, Operator.BITWISE_XOR,
                    Parser::BitwiseAndExpression)) == null) {
                return null;
            }
        }
        return result;
    }

    /**
     * <pre>{@literal
     * BitwiseAndExpression
     *     : EqualityExpression
     *     | BitwiseAndExpression AMP EqualityExpression
     * }</pre>
     */
    @Nullable
    private Expression BitwiseAndExpression() {
        Expression result = EqualityExpression();
        if (result == null) {
            return null;
        }
        while (peek(Lexer.TK_AMP)) {
            if ((result = operatorRight(result, Operator.BITWISE_AND,
                    Parser::EqualityExpression)) == null) {
                return null;
            }
        }
        return result;
    }

    /**
     * <pre>{@literal
     * EqualityExpression
     *     : RelationalExpression
     *     | EqualityExpression EQEQ RelationalExpression
     *     | EqualityExpression BANGEQ RelationalExpression
     * }</pre>
     */
    // @formatter:off
    @Nullable
    private Expression EqualityExpression() {
        Expression result = RelationalExpression();
        if (result == null) {
            return null;
        }
        for (;;) {
            Operator op = switch (Token.kind(peek())) {
                case Lexer.TK_EQEQ   -> Operator.EQ;
                case Lexer.TK_BANGEQ -> Operator.NE;
                default -> null;
            };
            if (op != null) {
                if ((result = operatorRight(result, op,
                        Parser::RelationalExpression)) == null) {
                    return null;
                }
            } else {
                return result;
            }
        }
    }
    // @formatter:on

    /**
     * <pre>{@literal
     * RelationalExpression
     *     : ShiftExpression
     *     | RelationalExpression LT ShiftExpression
     *     | RelationalExpression GT ShiftExpression
     *     | RelationalExpression LTEQ ShiftExpression
     *     | RelationalExpression GTEQ ShiftExpression
     * }</pre>
     */
    // @formatter:off
    @Nullable
    private Expression RelationalExpression() {
        Expression result = ShiftExpression();
        if (result == null) {
            return null;
        }
        for (;;) {
            Operator op = switch (Token.kind(peek())) {
                case Lexer.TK_LT   -> Operator.LT;
                case Lexer.TK_GT   -> Operator.GT;
                case Lexer.TK_LTEQ -> Operator.LE;
                case Lexer.TK_GTEQ -> Operator.GE;
                default -> null;
            };
            if (op != null) {
                if ((result = operatorRight(result, op,
                        Parser::ShiftExpression)) == null) {
                    return null;
                }
            } else {
                return result;
            }
        }
    }
    // @formatter:on

    /**
     * <pre>{@literal
     * ShiftExpression
     *     : AdditiveExpression
     *     | ShiftExpression LTLT AdditiveExpression
     *     | ShiftExpression GTGT AdditiveExpression
     * }</pre>
     */
    // @formatter:off
    @Nullable
    private Expression ShiftExpression() {
        Expression result = AdditiveExpression();
        if (result == null) {
            return null;
        }
        for (;;) {
            Operator op = switch (Token.kind(peek())) {
                case Lexer.TK_LTLT -> Operator.SHL;
                case Lexer.TK_GTGT -> Operator.SHR;
                default -> null;
            };
            if (op != null) {
                if ((result = operatorRight(result, op,
                        Parser::AdditiveExpression)) == null) {
                    return null;
                }
            } else {
                return result;
            }
        }
    }
    // @formatter:on

    /**
     * <pre>{@literal
     * AdditiveExpression
     *     : MultiplicativeExpression
     *     | AdditiveExpression PLUS MultiplicativeExpression
     *     | AdditiveExpression MINUS MultiplicativeExpression
     * }</pre>
     */
    // @formatter:off
    @Nullable
    private Expression AdditiveExpression() {
        Expression result = MultiplicativeExpression();
        if (result == null) {
            return null;
        }
        for (;;) {
            Operator op = switch (Token.kind(peek())) {
                case Lexer.TK_PLUS  -> Operator.ADD;
                case Lexer.TK_MINUS -> Operator.SUB;
                default -> null;
            };
            if (op != null) {
                if ((result = operatorRight(result, op,
                        Parser::MultiplicativeExpression)) == null) {
                    return null;
                }
            } else {
                return result;
            }
        }
    }
    // @formatter:on

    /**
     * <pre>{@literal
     * MultiplicativeExpression
     *     : UnaryExpression
     *     | MultiplicativeExpression STAR UnaryExpression
     *     | MultiplicativeExpression SLASH UnaryExpression
     *     | MultiplicativeExpression PERCENT UnaryExpression
     * }</pre>
     */
    // @formatter:off
    @Nullable
    private Expression MultiplicativeExpression() {
        Expression result = UnaryExpression();
        if (result == null) {
            return null;
        }
        for (;;) {
            Operator op = switch (Token.kind(peek())) {
                case Lexer.TK_STAR    -> Operator.MUL;
                case Lexer.TK_SLASH   -> Operator.DIV;
                case Lexer.TK_PERCENT -> Operator.MOD;
                default -> null;
            };
            if (op != null) {
                if ((result = operatorRight(result, op,
                        Parser::UnaryExpression)) == null) {
                    return null;
                }
            } else {
                return result;
            }
        }
    }
    // @formatter:on

    /**
     * <pre>{@literal
     * UnaryExpression
     *     : PrimaryExpression Selector* (PLUSPLUS | MINUSMINUS)?
     *     | PLUSPLUS UnaryExpression
     *     | MINUSMINUS UnaryExpression
     *     | UnaryOperator UnaryExpression
     *
     * UnaryOperator
     *     : PLUS
     *     | MINUS
     *     | BANG
     *     | TILDE
     * }</pre>
     */
    // @formatter:off
    @Nullable
    private Expression UnaryExpression() {
        long prefix = peek();
        Operator op = switch (Token.kind(prefix)) {
            case Lexer.TK_PLUSPLUS   -> Operator.INC;
            case Lexer.TK_MINUSMINUS -> Operator.DEC;
            case Lexer.TK_PLUS       -> Operator.ADD;
            case Lexer.TK_MINUS      -> Operator.SUB;
            case Lexer.TK_BANG       -> Operator.LOGICAL_NOT;
            case Lexer.TK_TILDE      -> Operator.BITWISE_NOT;
            default -> null;
        };
        if (op != null) {
            nextToken();
            Expression base = UnaryExpression();
            if (base == null) {
                return null;
            }
            int pos = Position.range(Token.offset(prefix), base.getEndOffset());
            return expressionOrPoison(pos,
                    PrefixExpression.convert(pos, op, base));
        }
        return PostfixExpression();
    }
    // @formatter:on

    /**
     * <pre>{@literal
     * PostfixExpression
     *     : PrimaryExpression
     *     | PostfixExpression LBRACKET Expression? RBRACKET
     *     | PostfixExpression LPAREN (VOID | Expression)? RPAREN
     *     | PostfixExpression DOT IDENTIFIER
     *     | PostfixExpression PLUSPLUS
     *     | PostfixExpression MINUSMINU
     * }</pre>
     */
    @Nullable
    private Expression PostfixExpression() {
        Expression result = PrimaryExpression();
        if (result == null) {
            return null;
        }
        for (;;) {
            long t = peek();
            switch (Token.kind(t)) {
                case Lexer.TK_LBRACKET:
                case Lexer.TK_DOT:
                case Lexer.TK_LPAREN:
                case Lexer.TK_PLUSPLUS:
                case Lexer.TK_MINUSMINUS:
                    // ..
                    break;
                default:
                    return result;
            }
        }
    }

    /**
     * <pre>{@literal
     * PrimaryExpression
     *     : IDENTIFIER
     *     | INTLITERAL
     *     | FLOATLITERAL
     *     | BOOLEANLITERAL
     *     | LPAREN Expression RPAREN
     * }</pre>
     */
    @Nullable
    private Expression PrimaryExpression() {
        long t = peek();
        return switch (Token.kind(t)) {
            case Lexer.TK_IDENTIFIER -> {
                nextToken();
                yield ThreadContext.getInstance().convertIdentifier(position(t), text(t));
            }
            case Lexer.TK_INTLITERAL -> IntLiteral();
            case Lexer.TK_FLOATLITERAL -> FloatLiteral();
            case Lexer.TK_TRUE, Lexer.TK_FALSE -> BooleanLiteral();
            case Lexer.TK_LPAREN -> {
                nextToken();
                Expression result = Expression();
                if (result != null) {
                    expect(Lexer.TK_RPAREN, "')' to complete expression");
                    result.mPosition = rangeFrom(t);
                    yield result;
                }
                yield null;
            }
            default -> {
                nextToken();
                error(t, "expected identifier, literal constant or parenthesized expression, but found '" +
                        text(t) + "'");
                throw new IllegalStateException();
            }
        };
    }

    /**
     * INTLITERAL
     */
    @Nullable
    private Literal IntLiteral() {
        long token = expect(Lexer.TK_INTLITERAL, "integer literal");
        String s = text(token);
        if (s.endsWith("u") || s.endsWith("U")) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            long value = Long.decode(s);
            if (value <= 0xFFFF_FFFFL) {
                return Literal.makeInteger(
                        position(token),
                        value);
            }
            error(token, "integer value is too large: " + s);
            return null;
        } catch (NumberFormatException e) {
            error(token, "invalid integer value: " + e.getMessage());
            return null;
        }
    }

    /**
     * FLOATLITERAL
     */
    @Nullable
    private Literal FloatLiteral() {
        long token = expect(Lexer.TK_FLOATLITERAL, "float literal");
        String s = text(token);
        try {
            float value = Float.parseFloat(s);
            if (Float.isFinite(value)) {
                return Literal.makeFloat(
                        position(token),
                        value);
            }
            error(token, "floating-point value is too large: " + s);
            return null;
        } catch (NumberFormatException e) {
            error(token, "invalid floating-point value: " + e.getMessage());
            return null;
        }
    }

    /**
     * TRUE | FALSE
     */
    @Nullable
    private Literal BooleanLiteral() {
        long token = nextToken();
        return switch (Token.kind(token)) {
            case Lexer.TK_TRUE -> Literal.makeBoolean(
                    position(token),
                    true);
            case Lexer.TK_FALSE -> Literal.makeBoolean(
                    position(token),
                    false);
            default -> {
                error(token, "expected 'true' or 'false', but found '" +
                        text(token) + "'");
                yield null;
            }
        };
    }

    @Nonnull
    private Modifiers modifiers() {
        long start = peek();
        Modifiers modifiers = new Modifiers(Position.NO_POS);
        for (;;) {
            int tokenFlag = switch (Token.kind(peek())) {
                case Lexer.TK_CONST -> Modifiers.kConst_Flag;
                case Lexer.TK_IN -> Modifiers.kIn_Flag;
                case Lexer.TK_OUT -> Modifiers.kOut_Flag;
                default -> 0;
            };
            if (tokenFlag == 0) {
                break;
            }
            long token = nextToken();
            modifiers.setFlag(tokenFlag, position(token));
        }
        modifiers.mPosition = rangeFrom(start);
        return modifiers;
    }

    private boolean allowUnsizedArrays() {
        return mModel == ExecutionModel.COMPUTE ||
                mModel == ExecutionModel.VERTEX ||
                mModel == ExecutionModel.FRAGMENT;
    }

    /**
     * <pre>{@literal
     * TypeSpecifier
     *     : IDENTIFIER ArraySpecifier*
     * }</pre>
     */
    @Nullable
    private Type TypeSpecifier(Modifiers modifiers) {
        long start = expect(Lexer.TK_IDENTIFIER, "a type name");
        String name = text(start);
        var symbol = ThreadContext.getInstance().getSymbolTable().find(name);
        if (symbol == null) {
            error(start, "no symbol named '" + name + "'");
            return ThreadContext.getInstance().getTypes().mPoison;
        }
        if (!(symbol instanceof Type result)) {
            error(start, "symbol '" + name + "' is not a type");
            return ThreadContext.getInstance().getTypes().mPoison;
        }
        if (result.isInterfaceBlock()) {
            error(start, "expected a type, found interface block '" + name + "'");
            return ThreadContext.getInstance().getTypes().mInvalid;
        }
        result = ArraySpecifier(position(start), result);
        return result;
    }

    @Nullable
    private Type ArraySpecifier(int pos, Type type) {
        long bracket;
        while ((bracket = checkNext(Lexer.TK_LBRACKET)) != -1) {
            if (checkNext(Lexer.TK_RBRACKET) != -1) {
                if (allowUnsizedArrays()) {
                    type = UnsizedArrayType(type, rangeFrom(pos));
                } else {
                    error(rangeFrom(bracket), "unsized arrays are not permitted here");
                }
            } else {
                Expression size = Expression();
                if (size == null) {
                    return null;
                }
                expect(Lexer.TK_RBRACKET, "']'");
                type = ArrayType(type, size, rangeFrom(pos));
            }
        }
        return type;
    }

    private Type ArrayType(@Nonnull Type elemType, Expression size, int pos) {
        int arraySize = elemType.convertArraySize(pos, size);
        if (arraySize == 0) {
            return ThreadContext.getInstance().getTypes().mPoison;
        }
        return ThreadContext.getInstance().getSymbolTable().getArrayType(elemType, arraySize);
    }

    private Type UnsizedArrayType(@Nonnull Type elemType, int pos) {
        if (!elemType.isUsableInArray(pos)) {
            return ThreadContext.getInstance().getTypes().mPoison;
        }
        return ThreadContext.getInstance().getSymbolTable().getArrayType(elemType, Type.kUnsizedArray);
    }

    /**
     * <pre>{@literal
     * VarDeclarationRest
     *     : IDENTIFIER ArraySpecifier* (EQ AssignmentExpression)? (COMMA
     *       IDENTIFIER ArraySpecifier* (EQ AssignmentExpression)?)* SEMICOLON
     * }</pre>
     */
    private Statement VarDeclarationRest(int pos, Modifiers modifiers, Type baseType) {
        long name = expectIdentifier();
        Type type = ArraySpecifier(pos, baseType);
        if (type == null) {
            return null;
        }
        Expression init = null;
        if (checkNext(Lexer.TK_EQ) != -1) {
            init = AssignmentExpression();
            if (init == null) {
                return null;
            }
        }
        Statement result = null; // TODO

        for (;;) {
            if (checkNext(Lexer.TK_COMMA) == -1) {
                expect(Lexer.TK_SEMICOLON, "';'");
                break;
            }
            name = expectIdentifier();
            type = ArraySpecifier(pos, baseType);
            if (type == null) {
                break;
            }
            init = null;
            if (checkNext(Lexer.TK_EQ) != -1) {
                init = AssignmentExpression();
                if (init == null) {
                    break;
                }
            }
            Statement next = null; // TODO

            result = BlockStatement.makeCompound(result, next);
        }
        pos = rangeFrom(pos);
        return statementOrEmpty(pos, result);
    }

    private Statement VarDeclarationOrExpressionStatement() {
        long peek = peek();
        if (Token.kind(peek) == Lexer.TK_CONST) {
            int pos = position(peek);
            Modifiers modifiers = modifiers();
            Type type = TypeSpecifier(modifiers);
            if (type == null) {
                return null;
            }
            return VarDeclarationRest(pos, modifiers, type);
        }
        if (ThreadContext.getInstance().getSymbolTable().isType(text(peek))) {
            int pos = position(peek);
            Modifiers modifiers = new Modifiers(pos);
            Type type = TypeSpecifier(modifiers);
            if (type == null) {
                return null;
            }
            return VarDeclarationRest(pos, modifiers, type);
        }
        return ExpressionStatement();
    }

    /**
     * <pre>{@literal
     * ExpressionStatement
     *     : Expression SEMICOLON
     * }</pre>
     */
    @Nullable
    private Statement ExpressionStatement() {
        Expression expr = Expression();
        if (expr == null) {
            return null;
        }
        expect(Lexer.TK_SEMICOLON, "';'");
        int pos = expr.mPosition;
        return statementOrEmpty(pos, ExpressionStatement.convert(expr));
    }

    /**
     * <pre>{@literal
     * StructDeclaration
     *     : STRUCT IDENTIFIER LBRACE VarDeclaration+ RBRACE
     *
     * VarDeclaration
     *     : Modifiers? Type VarDeclarator (COMMA VarDeclarator)* SEMICOLON
     *
     * VarDeclarator
     *     : IDENTIFIER (LBRACKET ConditionalExpression RBRACKET)*
     * }</pre>
     */
    private Type StructDeclaration() {
        long start = peek();
        expect(Lexer.TK_STRUCT, "'struct'");
        long typeName = expectIdentifier();
        expect(Lexer.TK_LBRACE, "'{'");
        return null;
    }

    @Nonnull
    private Statement statementOrEmpty(int pos, @Nullable Statement stmt) {
        if (stmt == null) {
            stmt = new EmptyStatement(pos);
        }
        return stmt;
    }

    private Statement Statement() {
        return null;
    }

    /**
     * <pre>{@literal
     * SelectionStatement
     *     : IF LPAREN Expression RPAREN Statement (ELSE Statement)?
     * }</pre>
     */
    @Nullable
    private Statement SelectionStatement() {
        long start = expect(Lexer.TK_IF, "'if'");
        expect(Lexer.TK_LPAREN, "'('");
        Expression test = Expression();
        if (test == null) {
            return null;
        }
        expect(Lexer.TK_RPAREN, "')'");
        Statement whenTrue = Statement();
        if (whenTrue == null) {
            return null;
        }
        Statement whenFalse = null;
        if (checkNext(Lexer.TK_ELSE) != -1) {
            whenFalse = Statement();
            if (whenFalse == null) {
                return null;
            }
        }
        int pos = rangeFrom(start);
        return statementOrEmpty(pos,
                IfStatement.convert(pos, test, whenTrue, whenFalse));
    }

    /**
     * <pre>{@literal
     * SwitchStatement
     *     : SWITCH LPAREN Expression RPAREN LBRACE Statement* RBRACE
     * }</pre>
     */
    private Statement SwitchStatement() {
        return null;
    }

    /**
     * <pre>{@literal
     * ForStatement
     *     : FOR LPAREN ForInit SEMICOLON Expression? SEMICOLON Expression? RPAREN Statement
     *
     * ForInit
     *     : (DeclarationStatement | ExpressionStatement)?
     * }</pre>
     */
    private Statement ForStatement() {
        long start = expect(Lexer.TK_FOR, "'for'");
        long lparen = expect(Lexer.TK_LPAREN, "'('");
        ThreadContext.getInstance()
                .enterScope();
        try {
            Statement init = null;
            int firstSemicolonOffset;
            if (peek(Lexer.TK_SEMICOLON)) {
                // An empty init-statement.
                firstSemicolonOffset = Token.offset(nextToken());
            } else {

            }

            return null;
        } finally {
            ThreadContext.getInstance()
                    .leaveScope();
        }
    }
}
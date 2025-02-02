package norswap.sigh;

import norswap.autumn.positions.Span;
import norswap.sigh.ast.*;
import norswap.sigh.scopes.DeclarationContext;
import norswap.sigh.scopes.DeclarationKind;
import norswap.sigh.scopes.RootScope;
import norswap.sigh.scopes.Scope;
import norswap.sigh.scopes.SyntheticDeclarationNode;
import norswap.sigh.types.*;
import norswap.uranium.Attribute;
import norswap.uranium.Reactor;
import norswap.uranium.Rule;
import norswap.uranium.SemanticError;
import norswap.utils.visitors.ReflectiveFieldWalker;
import norswap.utils.visitors.Walker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Stack;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static norswap.sigh.ast.BinaryOperator.*;
import static norswap.sigh.interpreter.Interpreter.getTypeFromName;
import static norswap.utils.Util.cast;
import static norswap.utils.Vanilla.forEachIndexed;
import static norswap.utils.Vanilla.list;
import static norswap.utils.visitors.WalkVisitType.POST_VISIT;
import static norswap.utils.visitors.WalkVisitType.PRE_VISIT;

/**
 * Holds the logic implementing semantic analyzis for the language, including typing and name
 * resolution.
 *
 * <p>The entry point into this class is {@link #createWalker(Reactor)}.
 *
 * <h2>Big Principles
 * <ul>
 *     <li>Every {@link DeclarationNode} instance must have its {@code type} attribute to an
 *     instance of {@link Type} which is the type of the value declared (note that for struct
 *     declaration, this is always {@link TypeType}.</li>
 *
 *     <li>Additionally, {@link StructDeclarationNode} (and default
 *     {@link SyntheticDeclarationNode} for types) must have their {@code declared} attribute set to
 *     an instance of the type being declared.</li>
 *
 *     <li>Every {@link ExpressionNode} instance must have its {@code type} attribute similarly
 *     set.</li>
 *
 *     <li>Every {@link ReferenceNode} instance must have its {@code decl} attribute set to the the
 *     declaration it references and its {@code scope} attribute set to the {@link Scope} in which
 *     the declaration it references lives. This speeds up lookups in the interpreter and simplifies the compiler.</li>
 *
 *     <li>For the same reasons, {@link VarDeclarationNode} and {@link ParameterNode} should have
 *     their {@code scope} attribute set to the scope in which they appear (this also speeds up the
 *     interpreter).</li>
 *
 *     <li>All statements introducing a new scope must have their {@code scope} attribute set to the
 *     corresponding {@link Scope} (only {@link RootNode}, {@link BlockNode} and {@link
 *     FunDeclarationNode} (for parameters)). These nodes must also update the {@code scope}
 *     field to track the current scope during the walk.</li>
 *
 *     <li>Every {@link TypeNode} instance must have its {@code value} set to the {@link Type} it
 *     denotes.</li>
 *
 *     <li>Every {@link ReturnNode}, {@link BlockNode} and {@link IfNode} must have its {@code
 *     returns} attribute set to a boolean to indicate whether its execution causes
 *     unconditional exit from the surrounding function or main script.</li>
 *
 *     <li>The rules check typing constraints: assignment of values to variables, of arguments to
 *     parameters, checking that if/while conditions are booleans, and array indices are
 *     integers.</li>
 *
 *     <li>The rules also check a number of other constraints: that accessed struct fields exist,
 *     that variables are declared before being used, etc...</li>
 * </ul>
 */
public final class SemanticAnalysis
{
    // =============================================================================================
    // region [Initialization]
    // =============================================================================================

    private final Reactor R;

    /** Current scope. */
    private Scope scope;

    /** Current context for type inference (currently only to infer the type of empty arrays). */
    private SighNode inferenceContext;

    /** Index of the current function argument. */
    private int argumentIndex;

    // ---------------------------------------------------------------------------------------------

    private SemanticAnalysis(Reactor reactor) {
        this.R = reactor;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Call this method to create a tree walker that will instantiate the typing rules defined
     * in this class when used on an AST, using the given {@code reactor}.
     */
    public static Walker<SighNode> createWalker (Reactor reactor)
    {
        ReflectiveFieldWalker<SighNode> walker = new ReflectiveFieldWalker<>(
            SighNode.class, PRE_VISIT, POST_VISIT);

        SemanticAnalysis analysis = new SemanticAnalysis(reactor);

        // expressions
        walker.register(IntLiteralNode.class,           PRE_VISIT,  analysis::intLiteral);
        walker.register(FloatLiteralNode.class,         PRE_VISIT,  analysis::floatLiteral);
        walker.register(StringLiteralNode.class,        PRE_VISIT,  analysis::stringLiteral);
        walker.register(ReferenceNode.class,            PRE_VISIT,  analysis::reference);
        walker.register(ConstructorNode.class,          PRE_VISIT,  analysis::constructor);
        walker.register(ArrayLiteralNode.class,         PRE_VISIT,  analysis::arrayLiteral);
        walker.register(ParenthesizedNode.class,        PRE_VISIT,  analysis::parenthesized);
        walker.register(FieldAccessNode.class,          PRE_VISIT,  analysis::fieldAccess);
        walker.register(ArrayAccessNode.class,          PRE_VISIT,  analysis::arrayAccess);
        walker.register(FunCallNode.class,              PRE_VISIT,  analysis::funCall);
        walker.register(UnaryExpressionNode.class,      PRE_VISIT,  analysis::unaryExpression);
        walker.register(BinaryExpressionNode.class,     PRE_VISIT,  analysis::binaryExpression);
        walker.register(AssignmentNode.class,           PRE_VISIT,  analysis::assignment);

        // types
        walker.register(SimpleTypeNode.class,           PRE_VISIT,  analysis::simpleType);
        walker.register(ArrayTypeNode.class,            PRE_VISIT,  analysis::arrayType);

        // declarations & scopes
        walker.register(RootNode.class,                 PRE_VISIT,  analysis::root);
        walker.register(BlockNode.class,                PRE_VISIT,  analysis::block);
        walker.register(VarDeclarationNode.class,       PRE_VISIT,  analysis::varDecl);
        walker.register(FieldDeclarationNode.class,     PRE_VISIT,  analysis::fieldDecl);
        walker.register(ParameterNode.class,            PRE_VISIT,  analysis::parameter);
        walker.register(FunDeclarationNode.class,       PRE_VISIT,  analysis::funDecl);
        walker.register(StructDeclarationNode.class,    PRE_VISIT,  analysis::structDecl);
        // added for the template feature
        walker.register(GenericDeclarationNode.class, PRE_VISIT,  analysis::genericDecl);

        //Logic Programming
        walker.register(QueryDeclarationNode.class,     PRE_VISIT,  analysis::queryDecl);
        walker.register(FactDeclarationNode.class,      PRE_VISIT,  analysis::factDecl);
        walker.register(ClauseDeclarationNode.class,    PRE_VISIT,  analysis::clauseDecl);
      // walker.register(AtomNode.class,                 PRE_VISIT,  analysis::factCall);
        walker.register(AtomNode.class,                 PRE_VISIT,  analysis::atomTmp);

        walker.register(TermNode.class,                 PRE_VISIT,  analysis::termLiteral);
        //

        walker.register(RootNode.class,                 POST_VISIT, analysis::popScope);
        walker.register(BlockNode.class,                POST_VISIT, analysis::popScope);
        walker.register(FunDeclarationNode.class,       POST_VISIT, analysis::popScope);

        // statements
        walker.register(ExpressionStatementNode.class,  PRE_VISIT,  node -> {});
        walker.register(IfNode.class,                   PRE_VISIT,  analysis::ifStmt);
        walker.register(WhileNode.class,                PRE_VISIT,  analysis::whileStmt);
        walker.register(ForNode.class,                PRE_VISIT,  analysis::forStmt);
        walker.register(ReturnNode.class,               PRE_VISIT,  analysis::returnStmt);

        walker.registerFallback(POST_VISIT, node -> {});

        return walker;
    }

    // endregion
    // =============================================================================================
    // region [Expressions]
    // =============================================================================================

    private void intLiteral (IntLiteralNode node) {
        R.set(node, "type", IntType.INSTANCE);
    }

    // ---------------------------------------------------------------------------------------------

    private void floatLiteral (FloatLiteralNode node) {
        R.set(node, "type", FloatType.INSTANCE);
    }

    // ---------------------------------------------------------------------------------------------

    private void stringLiteral (StringLiteralNode node) {
        R.set(node, "type", StringType.INSTANCE);
        R.set(node, "name", node.value);
    }

    // ---------------------------------------------------------------------------------------------
    // LP
    private void termLiteral (TermNode node){

        R.set(node, "type", TermType.INSTANCE);
        R.set(node, "name", node.value);
    }

    // ---------------------------------------------------------------------------------------------

    private void reference (ReferenceNode node)
    {
        final Scope scope = this.scope;

        // Try to lookup immediately. This must succeed for variables, but not necessarily for
        // functions or types. By looking up now, we can report looked up variables later
        // as being used before being defined.
        DeclarationContext maybeCtx = scope.lookup(node.name);

        if (maybeCtx != null) {
            R.set(node, "decl",  maybeCtx.declaration);
            R.set(node, "scope", maybeCtx.scope);
            R.set(node, "name", node.name);

            R.rule(node, "type")
            .using(maybeCtx.declaration, "type")
            .by(Rule::copyFirst);
            return;
        }

        // Re-lookup after the scopes have been built.
        R.rule(node.attr("decl"), node.attr("scope"))
        .by(r -> {
            DeclarationContext ctx = scope.lookup(node.name);
            DeclarationNode decl = ctx == null ? null : ctx.declaration;

            if (ctx == null) {
                r.errorFor("Could not resolve: " + node.name,
                    node, node.attr("decl"), node.attr("scope"), node.attr("type"));
            }
            else {
                r.set(node, "scope", ctx.scope);
                r.set(node, "decl", decl);

                if (decl instanceof VarDeclarationNode)
                    r.errorFor("Variable used before declaration: " + node.name,
                        node, node.attr("type"));
                else
                    R.rule(node, "type")
                    .using(decl, "type")
                    .by(Rule::copyFirst);
            }
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void constructor (ConstructorNode node)
    {
        R.rule()
        .using(node.ref, "decl")
        .by(r -> {
            DeclarationNode decl = r.get(0);

            if (!(decl instanceof StructDeclarationNode)) {
                String description =
                        "Applying the constructor operator ($) to non-struct reference for: "
                        + decl;
                r.errorFor(description, node, node.attr("type"));
                return;
            }

            StructDeclarationNode structDecl = (StructDeclarationNode) decl;

            Attribute[] dependencies = new Attribute[structDecl.fields.size() + 1];
            dependencies[0] = decl.attr("declared");
            forEachIndexed(structDecl.fields, (i, field) ->
                dependencies[i + 1] = field.attr("type"));

            R.rule(node, "type")
            .using(dependencies)
            .by(rr -> {
                Type structType = rr.get(0);
                Type[] params = IntStream.range(1, dependencies.length).<Type>mapToObj(rr::get)
                        .toArray(Type[]::new);
                rr.set(0, new FunType(structType, params));
            });
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void arrayLiteral (ArrayLiteralNode node)
    {
        if (node.components.size() == 0) { // []
            // Empty array: we need a type int to know the desired type.

            final SighNode context = this.inferenceContext;

            if (context instanceof VarDeclarationNode)
                R.rule(node, "type")
                .using(context, "type")
                .by(Rule::copyFirst);
            else if (context instanceof FunCallNode) {
                R.rule(node, "type")
                .using(((FunCallNode) context).function.attr("type"), node.attr("index"))
                .by(r -> {
                    FunType funType = r.get(0);
                    r.set(0, funType.paramTypes[(int) r.get(1)]);
                });
            }
            return;
        }

        Attribute[] dependencies =
            node.components.stream().map(it -> it.attr("type")).toArray(Attribute[]::new);

        R.rule(node, "type")
        .using(dependencies)
        .by(r -> {
            Type[] types = IntStream.range(0, dependencies.length).<Type>mapToObj(r::get)
                    .distinct().toArray(Type[]::new);

            int i = 0;
            Type supertype = null;
            for (Type type: types) {
                if (type instanceof VoidType)
                    // We report the error, but compute a type for the array from the other elements.
                    r.errorFor("Void-valued expression in array literal", node.components.get(i));
                else if (supertype == null)
                    supertype = type;
                else {
                    supertype = commonSupertype(supertype, type);
                    if (supertype == null) {
                        r.error("Could not find common supertype in array literal.", node);
                        return;
                    }
                }
                ++i;
            }

            if (supertype == null)
                r.error(
                    "Could not find common supertype in array literal: all members have Void type.",
                    node);
            else
                r.set(0, new ArrayType(supertype));
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void parenthesized (ParenthesizedNode node)
    {
        R.rule(node, "type")
        .using(node.expression, "type")
        .by(Rule::copyFirst);
    }

    // ---------------------------------------------------------------------------------------------

    private void fieldAccess (FieldAccessNode node)
    {
        R.rule()
        .using(node.stem, "type")
        .by(r -> {
            Type type = r.get(0);

            if (type instanceof ArrayType) {
                if (node.fieldName.equals("length"))
                    R.rule(node, "type")
                    .by(rr -> rr.set(0, IntType.INSTANCE));
                else
                    r.errorFor("Trying to access a non-length field on an array", node,
                        node.attr("type"));
                return;
            }
            
            if (!(type instanceof StructType)) {
                r.errorFor("Trying to access a field on an expression of type " + type,
                        node,
                        node.attr("type"));
                return;
            }

            StructDeclarationNode decl = ((StructType) type).node;

            for (DeclarationNode field: decl.fields)
            {
                if (!field.name().equals(node.fieldName)) continue;

                R.rule(node, "type")
                .using(field, "type")
                .by(Rule::copyFirst);

                return;
            }

            String description = format("Trying to access missing field %s on struct %s",
                    node.fieldName, decl.name);
            r.errorFor(description, node, node.attr("type"));
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void arrayAccess (ArrayAccessNode node)
    {
        R.rule()
        .using(node.index, "type")
        .by(r -> {
            Type type = r.get(0);
            if (!(type instanceof IntType))
                r.error("Indexing an array using a non-Int-valued expression", node.index);
        });

        R.rule(node, "type")
        .using(node.array, "type")
        .by(r -> {
            Type type = r.get(0);
            if (type instanceof ArrayType)
                r.set(0, ((ArrayType) type).componentType);
            else
                r.error("Trying to index a non-array expression of type " + type, node);
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void funCall (FunCallNode node)
    {
        this.inferenceContext = node;
        DeclarationContext context;

        int optional=node.expectedReturnType != null ? 2 : 0;

        Attribute[] dependencies = new Attribute[node.arguments.size() + 1 + optional];
        dependencies[0] = node.function.attr("type");

        forEachIndexed(node.arguments, (i, arg) -> {
            dependencies[i+1] = arg.attr("type");
            R.set(arg, "index", i);
        });

        /* add the optional generic parameter to the dependency if they are present in the declaration */
        int count = node.arguments.size() + 1;
        if (node.expectedReturnType != null) {
            dependencies[count ] = node.expectedReturnType.attr("value");
            R.set(node.expectedReturnType, "index", count );
            count += 1 ;
        }

        if (node.expectedReturnType != null) {
            String name = "";
            if (node.function instanceof ReferenceNode) {
                name = ((ReferenceNode) node.function).name;
            }
            context = scope.lookup(name);
            SimpleTypeNode str= (SimpleTypeNode) node.expectedReturnType;

            if(str.name.equals("String")) {
                ReturnNode st = (ReturnNode) ((FunDeclarationNode) context.declaration).block.statements.get(0);
                BinaryExpressionNode b = (BinaryExpressionNode) st.expression;
                BinaryOperator usedOperator = b.operator;
                if(usedOperator!=ADD)
                    R.error(new SemanticError("Only Add operation is valid in Strings", null,node));
            }
            dependencies[count] = new Attribute(context.declaration, "type");
        }

        R.rule(node, "type")
            .using(dependencies)
            .by(r -> {
                Type maybeFunType = r.get(0);

                if (!(maybeFunType instanceof FunType)) {
                    r.error("trying to call a non-function expression: " + node.function, node.function);
                    return;
                }

                FunType funType = cast(maybeFunType);
                r.set(0, funType.returnType);

                Type[] params = funType.paramTypes;
                List<ExpressionNode> args = node.arguments;

                if (params.length != args.size())
                    r.errorFor(format("wrong number of arguments, expected %d but got %d", params.length, args.size()), node);

                int checkedArgs = Math.min(params.length, args.size());

                Object lastNode = r.dependencies[r.dependencies.length-1].node;

                if (lastNode instanceof FunDeclarationNode) {
                    if (node.expectedReturnType != null)
                        node.mapTtoType.put(((FunDeclarationNode) lastNode).genericParam.name,
                            node.expectedReturnType);
                }

                for (int i = 0; i < checkedArgs; ++i) {
                    Type funParamT=funType.paramTypes[i];
                    Type argType = r.get(i + 1);
                    Type paramType = (funParamT instanceof GenericType) ?
                        getTypeFromName(node.expectedReturnType) : funParamT;

                    if (!isAssignableTo(argType, paramType)) {
                            r.errorFor(format(
                                    "incompatible argument provided for argument %d: expected %s but got %s", i, paramType, argType),
                                node.arguments.get(i));

                    }
                }
            });
    }

    // ---------------------------------------------------------------------------------------------

    private void unaryExpression (UnaryExpressionNode node)
    {
        if(node.operator == UnaryOperator.NOT){
            R.set(node, "type", BoolType.INSTANCE);
            R.rule()
                .using(node.operand, "type")
                .by(r -> {
                    Type opType = r.get(0);
                    if (!(opType instanceof BoolType))
                        r.error("Trying to negate type: " + opType, node);
                });
        }

        if(node.operator == UnaryOperator.INCRE){
            R.set(node, "type", IntType.INSTANCE);
            R.rule()
                .using(node.operand, "type")
                .by(r -> {
                    Type opType = r.get(0);
                    if (!(opType instanceof IntType))
                        r.error("Trying to increment type: " + opType, node);
                });
        }

        if(node.operator == UnaryOperator.DECRE){
            R.set(node, "type", IntType.INSTANCE);
            R.rule()
                .using(node.operand, "type")
                .by(r -> {
                    Type opType = r.get(0);
                    if (!(opType instanceof IntType))
                        r.error("Trying to increment type: " + opType, node);
                });
        }
    }

    // endregion
    // =============================================================================================
    // region [Binary Expressions]
    // =============================================================================================

    private void binaryExpression (BinaryExpressionNode node)
    {
        Scope s = scope;

        /* Check If any node is Template Type in Binary Expression Node*/
        boolean flag= checkIfGenericInAst(node.left,scope) || checkIfGenericInAst(node.right,scope);

        R.rule(node, "scope")
            .by(rule -> {
                rule.set(0, s);
            });

        R.rule(node, "type")
            .using(node.left.attr("type"), node.right.attr("type"))
            .by(r -> {
                Type left  = r.get(0);
                Type right = r.get(1);
                if(flag) {
                  r.set(0, (left instanceof GenericType) ? left : right);
                }else {
                if (left instanceof GenericType){
                    left = ((GenericType) left).node!=null?((GenericType) left).node.type:left;
                }
                if (right instanceof GenericType){
                    right = ((GenericType) right).node!=null?((GenericType) right).node.type:right;
                }
                if (node.operator == ADD && (left instanceof StringType || right instanceof StringType))
                    r.set(0, StringType.INSTANCE);
                else if (isArithmetic(node.operator))
                    binaryArithmetic(r, node, left, right);
                else if (isComparison(node.operator))
                    binaryComparison(r, node, left, right);
                else if (isLogic(node.operator))
                    binaryLogic(r, node, left, right);
                else if (isEquality(node.operator))
                    binaryEquality(r, node, left, right);
              }
            });
    }

    // ---------------------------------------------------------------------------------------------

    private boolean isArithmetic (BinaryOperator op) {
        return op == ADD || op == MULTIPLY || op == SUBTRACT || op == DIVIDE || op == REMAINDER;
    }

    private boolean isComparison (BinaryOperator op) {
        return op == GREATER || op == GREATER_EQUAL || op == LOWER || op == LOWER_EQUAL;
    }

    private boolean isLogic (BinaryOperator op) {
        return op == OR || op == AND || op==XOR;
    }

    private boolean isEquality (BinaryOperator op) {
        return op == EQUALITY || op == NOT_EQUALS;
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryArithmetic (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        if (left instanceof IntType)
            if (right instanceof IntType)
                r.set(0, IntType.INSTANCE);
            else if (right instanceof FloatType)
                r.set(0, FloatType.INSTANCE);
            else
                r.error(arithmeticError(node, "Int", right), node);
        else if (left instanceof FloatType)
            if (right instanceof IntType || right instanceof FloatType)
                r.set(0, FloatType.INSTANCE);
            else
                r.error(arithmeticError(node, "Float", right), node);
        else
            r.error(arithmeticError(node, left, right), node);
    }

    // ---------------------------------------------------------------------------------------------

    private static String arithmeticError (BinaryExpressionNode node, Object left, Object right) {
        return format("Trying to %s %s with %s", node.operator.name().toLowerCase(), left, right);
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryComparison (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        r.set(0, BoolType.INSTANCE);

        if (!(left instanceof IntType) && !(left instanceof FloatType))
            r.errorFor("Attempting to perform arithmetic comparison on non-numeric type: " + left,
                node.left);
        if (!(right instanceof IntType) && !(right instanceof FloatType))
            r.errorFor("Attempting to perform arithmetic comparison on non-numeric type: " + right,
                node.right);
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryEquality (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        r.set(0, BoolType.INSTANCE);

        if (!isComparableTo(left, right))
            r.errorFor(format("Trying to compare incomparable types %s and %s", left, right),
                node);
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryLogic (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        r.set(0, BoolType.INSTANCE);

        if (!(left instanceof BoolType))
            r.errorFor("Attempting to perform binary logic on non-boolean type: " + left,
                node.left);
        if (!(right instanceof BoolType))
            r.errorFor("Attempting to perform binary logic on non-boolean type: " + right,
                node.right);
    }

    // ---------------------------------------------------------------------------------------------

    private void assignment (AssignmentNode node)
    {
        R.rule(node, "type")
        .using(node.left.attr("type"), node.right.attr("type"))
        .by(r -> {
            Type left  = r.get(0);
            Type right = r.get(1);

            r.set(0, r.get(0)); // the type of the assignment is the left-side type

            if (node.left instanceof ReferenceNode
            ||  node.left instanceof FieldAccessNode
            ||  node.left instanceof ArrayAccessNode) {
                if (!isAssignableTo(right, left))
                    r.errorFor("Trying to assign a value to a non-compatible lvalue.", node);
            }
            else
                r.errorFor("Trying to assign to an non-lvalue expression.", node.left);
        });
    }

    // endregion
    // =============================================================================================
    // region [Types & Typing Utilities]
    // =============================================================================================

    private void simpleType (SimpleTypeNode node)
    {
        final Scope scope = this.scope;

        R.rule()
        .by(r -> {
            // type declarations may occur after use
            DeclarationContext ctx = scope.lookup(node.name);
            DeclarationNode decl = ctx == null ? null : ctx.declaration;

            if (ctx == null)
                r.errorFor("could not resolve: " + node.name,
                    node,
                    node.attr("value"));

            else if (!isTypeDecl(decl))
                r.errorFor(format(
                    "%s did not resolve to a type declaration but to a %s declaration",
                    node.name, decl.declaredThing()),
                    node,
                    node.attr("value"));

            else
                R.rule(node, "value")
                .using(decl, "declared")
                .by(Rule::copyFirst);
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void arrayType (ArrayTypeNode node)
    {
        R.rule(node, "value")
        .using(node.componentType, "value")
        .by(r -> r.set(0, new ArrayType(r.get(0))));
    }

    // ---------------------------------------------------------------------------------------------

    private static boolean isTypeDecl (DeclarationNode decl)
    {
        if (decl instanceof StructDeclarationNode) return true;
        /** added GenericDeclarationNode */
        if (decl instanceof GenericDeclarationNode) return true;
        if (!(decl instanceof SyntheticDeclarationNode)) return false;
        SyntheticDeclarationNode synthetic = cast(decl);
        return synthetic.kind() == DeclarationKind.TYPE;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Indicates whether a value of type {@code a} can be assigned to a location (variable,
     * parameter, ...) of type {@code b}.
     */
    private static boolean isAssignableTo (Type a, Type b)
    {
        if (a instanceof VoidType || b instanceof VoidType)
            return false;

        if (a instanceof IntType && b instanceof FloatType)
            return true;

        if (a instanceof ArrayType)
            return b instanceof ArrayType
                && isAssignableTo(((ArrayType)a).componentType, ((ArrayType)b).componentType);

        return a instanceof NullType && b.isReference() || a.equals(b);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Indicate whether the two types are comparable.
     */
    private static boolean isComparableTo (Type a, Type b)
    {
        if (a instanceof VoidType || b instanceof VoidType)
            return false;

        return a.isReference() && b.isReference()
            || a.equals(b)
            || a instanceof IntType && b instanceof FloatType
            || a instanceof FloatType && b instanceof IntType;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the common supertype between both types, or {@code null} if no such supertype
     * exists.
     */
    private static Type commonSupertype (Type a, Type b)
    {
        if (a instanceof VoidType || b instanceof VoidType)
            return null;
        if (isAssignableTo(a, b))
            return b;
        if (isAssignableTo(b, a))
            return a;
        else
            return null;
    }

    // endregion
    // =============================================================================================
    // region [Scopes & Declarations]
    // =============================================================================================

    private void popScope (SighNode node) {
        scope = scope.parent;
    }

    // ---------------------------------------------------------------------------------------------

    private void root (RootNode node) {
        assert scope == null;
        scope = new RootScope(node, R);
        R.set(node, "scope", scope);
    }

    // ---------------------------------------------------------------------------------------------

    private void block (BlockNode node) {
        scope = new Scope(node, scope);
        R.set(node, "scope", scope);

        Attribute[] deps = getReturnsDependencies(node.statements);
        R.rule(node, "returns")
        .using(deps)
        .by(r -> r.set(0, deps.length != 0 && Arrays.stream(deps).anyMatch(r::get)));
    }

    // ---------------------------------------------------------------------------------------------

    private void varDecl (VarDeclarationNode node)
    {
        this.inferenceContext = node;

        scope.declare(node.name, node);
        R.set(node, "scope", scope);

        R.rule(node, "type")
        .using(node.type, "value")
        .by(Rule::copyFirst);

        R.rule()
        .using(node.type.attr("value"), node.initializer.attr("type"))
        .by(r -> {
            Type expected = r.get(0);
            Type actual = r.get(1);

            if (!isAssignableTo(actual, expected))
                r.error(format(
                    "incompatible initializer type provided for variable `%s`: expected %s but got %s",
                    node.name, expected, actual),
                    node.initializer);
        });
    }

    // ---------------------------------------------------------------------------------------------
                // LOGIC PROGRAMMING
    // ---------------------------------------------------------------------------------------------

    private void atomTmp(AtomNode node){

    }
    // ---------------------------------------------------------------------------------------------

    private void clauseDecl(ClauseDeclarationNode node){
        scope.declare(node.left_atom.name,node);
        scope= new Scope(node,scope);
        R.set(node,"scope",scope);
        int cpt=node.left_atom.terms.size();

        for(int i=0;i<node.right_atoms.size();i++){ cpt+= node.right_atoms.get(i).terms.size();}

        Attribute[] dependencies = new Attribute[cpt];
        Attribute[] dependecies_= new Attribute[cpt];

        forEachIndexed(node.left_atom.terms,(i,term)-> {
            dependencies[i]= term.attr("type");
         //   dependecies_[i]= term.attr("name");
                });
        cpt=node.left_atom.terms.size();

        for(int i=0;i<node.right_atoms.size();i++){
           for(int j=0;j<node.right_atoms.get(i).terms.size();j++){
               dependencies[cpt+j]= node.right_atoms.get(i).terms.get(j).attr("type");
            //   dependecies_[cpt+j]= node.right_atoms.get(i).terms.get(j).attr("name");

           }
           cpt+=node.right_atoms.get(i).terms.size();
        }
        R.rule()
            .using(dependencies)
            .by(r->{
                for(int i=0; i< dependencies.length; i++){
                    if(!(r.get(i) instanceof TermType)) {
                        r.error("non term type found where term type required instead of "+ r.get(i).toString(),node);
                    }
                }
                //    r.set(node,"declared",new QueryType(node));
            });
/**
        R.rule()
            .using(dependecies_)
            .by(r->{
                for(int i=0; i<node.left_atom.terms.size();i++){
                    boolean exist=false;
                    for(int j=node.left_atom.terms.size(); j<dependecies_.length;j++){
                        if (dependecies_[i]== dependecies_[j]) exist=true;
                    }
                    if(!exist) r.error("clause term not part of right part "+dependecies_[i],node);
                }
            });**/


    }
    // ---------------------------------------------------------------------------------------------

    private void queryDecl(QueryDeclarationNode node){
        scope.declare(node.name(),node);
        scope= new Scope(node,scope);
        R.set(node,"scope",scope);

        Attribute[] dependencies = new Attribute[node.atom.terms.size()];
        forEachIndexed(node.atom.terms,(i,term)->{
            dependencies[i]= term.attr("type");
         //   System.out.println(term.attr("name"));
        });

        R.rule()
            .using(dependencies)
            .by(r->{
                for(int i=0; i< dependencies.length; i++){
                    if(!(r.get(i) instanceof TermType)) {
                        r.error("non term type found where term type required instead of "+ r.get(i).toString(),node);
                    }
                }
            });
        /**
        R.rule()
            .using(node.name,"type")
            .by(r -> {
                Type type = r.get(0);
                if (!(type instanceof FactType)){
                    r.error(" query with a non fact ",node);
                }
            });**/


    }

    // ---------------------------------------------------------------------------------------------
    private void factDecl(FactDeclarationNode node){
        scope.declare(node.name,node);
        scope= new Scope(node,scope);
        R.set(node,"scope",scope);

        Attribute[] dependencies = new Attribute[node.terms.size()];
     //   System.out.println("checkpoint: size of terms= "+node.terms.size());
        forEachIndexed(node.terms,(i,term)->{
       //  System.out.println("checkpoint: i ="+i+"  att= "+term.attr("name"));
            dependencies[i]= term.attr("type");});
       // System.out.println(" GET HERE");
        R.rule()
            .using(dependencies)
            .by(r->{
             //   System.out.println("IN HERE");
               for(int i=0; i< dependencies.length; i++){
                   if(!(r.get(i) instanceof TermType)) {
                       r.error("non term type found where term type required instead of "+ r.get(i).toString(),node);
                   }
               }
            });
        R.set(node,"declared",new FactType(node));

    }

    // ---------------------------------------------------------------------------------------------

    private void factCall(AtomNode node){
        this.inferenceContext=node;
        Attribute[] dependencies = new Attribute[node.terms.size()+1];
      //  dependencies[0]=node.fact.attr("type");
        forEachIndexed(node.terms,(i,term)->{
            dependencies[i]= term.attr("type");
            R.set(term,"index",i);
        });

        R.rule(node,"type")
            .using(dependencies)
            .by(r->{
                Type maybeFactType = r.get(0);
                if(!(maybeFactType instanceof FactType)){
                    r.error("Query on non-fact type"+node.name,node);
                    return;
                }
                FactType factType= cast(maybeFactType);
                List<ExpressionNode> param= factType.node.terms;
                List<ExpressionNode> args = node.terms;

                if(param.size() != args.size()){
                    r.error(" fact called with wrong arity of terms",node);
                }
            });

    }

    //----------------------------------------------------------------------------------------------
    private void fieldDecl (FieldDeclarationNode node)
    {
        R.rule(node, "type")
        .using(node.type, "value")
        .by(Rule::copyFirst);
    }
    // ---------------------------------------------------------------------------------------------
    private void parameter (ParameterNode node)
    {
        R.set(node, "scope", scope);
        scope.declare(node.name, node); // scope pushed by FunDeclarationNode

        R.rule(node, "type")
        .using(node.type, "value")
        .by(Rule::copyFirst);
    }

    // ---------------------------------------------------------------------------------------------

    private void funDecl (FunDeclarationNode node)
    {
        // Preparing scope
        scope.declare(node.name, node);
        scope = new Scope(node, scope);

        // Setting scope
        R.set(node, "scope", scope);
        boolean isGeneric=false;
        List<ParameterNode> parameterNodes = node.parameters;

        /* if the node contains the Template Parameter in declaration*/
        if(node.genericParam!=null)
            isGeneric = node.genericParam.name.equals(((SimpleTypeNode) node.returnType).name);

        Attribute[] dependencies = new Attribute[parameterNodes.size() + (isGeneric ? 0 : 1)];

        if (!isGeneric) {
            dependencies[0] = node.returnType.attr("value");
        }

        boolean finalIsGeneric1 = isGeneric;
        forEachIndexed(parameterNodes, (i, param) ->
            dependencies[i + (finalIsGeneric1 ? 0 : 1)] = param.attr("type"));

        if (dependencies.length > 0) {
            boolean finalIsGeneric = isGeneric;
            if(node.genericParam!=null) {
                R.rule()
                    .using(node.genericParam.attr("type"))
                    .by(r -> {
                        GenericDeclarationNode obj = (GenericDeclarationNode) r.dependencies[0].node;
                        if (!(obj.name.equals("T"))) {
                            r.error("T should be used as Template Parameter instead of " + obj.name , node);
                        }
                    });
            }
            R.rule(node, "type")
                .using(dependencies)
                .by (r -> {
                    Type[] paramTypes = new Type[parameterNodes.size()];
                    for (int i = 0; i < paramTypes.length; ++i)
                        paramTypes[i] = r.get(i + (finalIsGeneric ? 0 : 1));
                    r.set(0, new FunType(r.get(0), paramTypes));
                });
        }

            R.rule()
                .using(node.block.attr("returns"), node.returnType.attr("value"))
                .by(r -> {
                    boolean returns = r.get(0);
                    Type returnType = r.get(1);
                    if (!returns && !(returnType instanceof VoidType))
                        r.error("Missing return in function.", node);
                });

    }

    // ---------------------------------------------------------------------------------------------

    private void structDecl (StructDeclarationNode node) {
        scope.declare(node.name, node);
        R.set(node, "type", TypeType.INSTANCE);
        R.set(node, "declared", new StructType(node));
    }
    // ---------------------------------------------------------------------------------------------
    /* GenericDeclarationNode is added */
    private void genericDecl (GenericDeclarationNode node) {
        scope.declare(node.name, node);
        R.set(node, "type", TypeType.INSTANCE);
        R.set(node, "declared", new GenericType(node));
    }

    // endregion
    // =============================================================================================
    // region [Other Statements]
    // =============================================================================================

    private void ifStmt (IfNode node) {
        R.rule()
        .using(node.condition, "type")
        .by(r -> {
            Type type = r.get(0);
            if (!(type instanceof BoolType)) {
                r.error("If statement with a non-boolean condition of type: " + type,
                    node.condition);
            }
        });

        Attribute[] deps = getReturnsDependencies(list(node.trueStatement, node.falseStatement));
        R.rule(node, "returns")
        .using(deps)
        .by(r -> r.set(0, deps.length == 2 && Arrays.stream(deps).allMatch(r::get)));
    }

    // ---------------------------------------------------------------------------------------------

    private void whileStmt (WhileNode node) {
        R.rule()
        .using(node.condition, "type")
        .by(r -> {
            Type type = r.get(0);
            if (!(type instanceof BoolType)) {
                r.error("While statement with a non-boolean condition of type: " + type,
                    node.condition);
            }
        });
    }
    /* For loop added */
    private void forStmt (ForNode node) {
        R.rule()
            .using(node.initialization, "type")
            .by(r -> {
                Type type = r.get(0);
                if (!(type instanceof IntType)) {
                    System.out.println(type);
                    r.error("Intialization variable is non-integer:" + type, node.initialization);
                }
            });

        R.rule()
            .using(node.condition, "type")
            .by(r -> {
                Type type = r.get(0);
                if (!(type instanceof BoolType)) {
                    r.error("For statement with a non-boolean condition of type: " + type,
                        node.condition);
                }
            });

        R.rule()
            .using(node.indec, "type")
            .by(r -> {
                Type type = r.get(0);
                if (!(type instanceof IntType)) {
                    r.error("For statement with a non-int increment of type: " + type,
                        node.indec);
                }
            });
    }


    // ---------------------------------------------------------------------------------------------

    private void returnStmt (ReturnNode node)
    {
        Scope s= scope;
        R.set(node, "returns", true);
        // DeclarationContext ctx = s.lookup((ReferenceNode)((ExpressionNode)((ReturnNode)node).expression).function);
        FunDeclarationNode function = currentFunction();
        if (function == null)
            return;

        if (node.expression == null)
            R.rule()
                .using(function.returnType, "value")
                .by(r -> {
                    Type returnType = r.get(0);
                    if (!(returnType instanceof VoidType))
                        r.error("Return without value in a function with a return type.", node);
                });

            R.rule()
                .using(function.returnType.attr("value"), node.expression.attr("type"))
                .by(r -> {
                    Type formal = r.get(0);
                    Type actual = r.get(1);
               /* Check for Template type */
                    formal = formal instanceof GenericType ?
                        ((GenericType) formal).node.type!=null? ((GenericType) formal).node.type
                            : formal : formal;

                    if (formal instanceof VoidType)
                        r.error("Return with value in a Void function.", node);
                     else if (!isAssignableTo(actual, formal)) {
                        r.errorFor(format("Incompatible return type, expected %s but got %s", formal, actual), node.expression);
                    }
                });
    }

    // ---------------------------------------------------------------------------------------------

    private FunDeclarationNode currentFunction()
    {
        Scope scope = this.scope;
        while (scope != null) {
            SighNode node = scope.node;
            if (node instanceof FunDeclarationNode)
                return (FunDeclarationNode) node;
            scope = scope.parent;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private boolean isReturnContainer (SighNode node) {
        return node instanceof BlockNode
            || node instanceof IfNode
            || node instanceof ReturnNode;
    }

    // ---------------------------------------------------------------------------------------------

    /** Get the depedencies necessary to compute the "returns" attribute of the parent. */
    private Attribute[] getReturnsDependencies (List<? extends SighNode> children) {
        return children.stream()
            .filter(Objects::nonNull)
            .filter(this::isReturnContainer)
            .map(it -> it.attr("returns"))
            .toArray(Attribute[]::new);
    }

    /** Take the outermost node and explore the innermost node associated with it by traversing the AST */
    private boolean checkIfGenericInAst(SighNode node, Scope scope) {

        Stack<SighNode> stack = new Stack<>();
        stack.push(node);

        while (!stack.isEmpty()) {
            SighNode popN = stack.pop();
            if (popN instanceof GenericDeclarationNode) {
                return true;
            }else if (popN instanceof ReferenceNode) {
                DeclarationContext ctx = scope.lookup(((ReferenceNode) popN).name);
                if (ctx != null){
                    stack.push(ctx.declaration);
                }
            }else if(popN instanceof ParameterNode) {
                TypeNode t = ((ParameterNode) popN).type;

                if (t instanceof SimpleTypeNode && ((SimpleTypeNode) t).name.equals("T")) {
                    DeclarationContext ctx = scope.lookup(((ParameterNode) popN).name);

                    if (ctx != null) {
                        SighNode sn = ctx.scope.node;
                        if (sn instanceof FunDeclarationNode) {
                            GenericDeclarationNode declNode= ((FunDeclarationNode) sn).genericParam;
                            if ( declNode != null && declNode.name.equals(((SimpleTypeNode) t).name)) {
                                if(declNode.name.equals("T")) {
                                    stack.push(declNode);
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    // endregion
    // =============================================================================================
}
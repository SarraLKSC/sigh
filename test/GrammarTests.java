import norswap.autumn.AutumnTestFixture;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.*;
import org.testng.annotations.Test;

import java.util.Collections;

import static java.util.Arrays.asList;
import static norswap.sigh.ast.BinaryOperator.*;

public class GrammarTests extends AutumnTestFixture {
    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final Class<?> grammarClass = grammar.getClass();

    // ---------------------------------------------------------------------------------------------

    private static IntLiteralNode intlit (long i) {
        return new IntLiteralNode(null, i);
    }

    private static FloatLiteralNode floatlit (double d) {
        return new FloatLiteralNode(null, d);
    }

    private static StringLiteralNode stringlit (String s) {
        return new StringLiteralNode(null, s);
    }


    // ---------------------------------------------------------------------------------------------

    // LP grammar test method addition: testLP - testFact - testClause - testQuery + additional tests in testDeclarations !

    @Test void testLP () {
        rule=grammar.expression;
        successExpect("LP",new ReferenceNode(null,"LP"));
        successExpect("LPC",new ReferenceNode(null,"LPC"));
    }
    @Test void testFact(){
        rule=grammar.statement;
        successExpect("LP sing ( #harry )",new FactDeclarationNode(null,"sing",asList(
            new TermNode(null,"#harry"))
        ));
        successExpect("LP sing ( x )",new FactDeclarationNode(null,"sing",asList(
            new ReferenceNode(null,"x"))
        ));
        successExpect("LP song (#Style,#taylorSwift)", new FactDeclarationNode(null,"song", asList(
            new TermNode(null,"#Style"),
            new TermNode(null,"#taylorSwift")
        )));
        successExpect("LP feat( #drake,#rihanna, #takecare )", new FactDeclarationNode(null,"feat",asList(
            new TermNode(null,"#drake"),
            new TermNode(null,"#rihanna"),
            new TermNode(null,"#takecare")
        )));
    }
    @Test void testClause(){

        rule=grammar.statement;
        successExpect("LPC animal(#a) :- dog(#a) ", new ClauseDeclarationNode(null,
            new AtomNode(null,"animal",asList(new TermNode(null,"#a"))), asList(
                new AtomNode(null,"dog",asList(new TermNode(null,"#a")))
            )));

        successExpect("LPC sister(#a,#b) :- mom(#a,#c) , mom(#b,#c)", new ClauseDeclarationNode(null,
            new AtomNode(null,"sister",asList(new TermNode(null,"#a"),new TermNode(null,"#b"))),
            asList(
            new AtomNode(null,"mom",asList(new TermNode(null,"#a"), new TermNode(null,"#c"))),
            new AtomNode(null,"mom",asList(new TermNode(null,"#b"), new TermNode(null,"#c")))
        )));
    }

    @Test void testQuery(){
        rule= grammar.statement;
        successExpect("-? boy( #baby )", new QueryDeclarationNode(null,new AtomNode(null,"boy", asList(
            new TermNode(null,"#baby")))
        ));
        successExpect("-? mother( #lilly, #harry)", new QueryDeclarationNode(null, new AtomNode(null,"mother",
            asList(
            new TermNode(null,"#lilly"),
            new TermNode(null,"#harry")
        ))));
    }
    //----------------------------------------------------------------------------------------//

    @Test
    public void testLiteralsAndUnary () {
        rule = grammar.expression;

        successExpect("42", intlit(42));
        successExpect("42.0", floatlit(42d));
        successExpect("\"hello\"", new StringLiteralNode(null, "hello"));
        successExpect("(42)", new ParenthesizedNode(null, intlit(42)));
        successExpect("[1, 2, 3]", new ArrayLiteralNode(null, asList(intlit(1), intlit(2), intlit(3))));
        successExpect("true", new ReferenceNode(null, "true"));
        successExpect("false", new ReferenceNode(null, "false"));
        successExpect("null", new ReferenceNode(null, "null"));
        successExpect("!false", new UnaryExpressionNode(null, UnaryOperator.NOT, new ReferenceNode(null, "false")));
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testNumericBinary () {
        successExpect("1 + 2", new BinaryExpressionNode(null, intlit(1), ADD, intlit(2)));
        successExpect("2 - 1", new BinaryExpressionNode(null, intlit(2), SUBTRACT,  intlit(1)));
        successExpect("2 * 3", new BinaryExpressionNode(null, intlit(2), MULTIPLY, intlit(3)));
        successExpect("2 / 3", new BinaryExpressionNode(null, intlit(2), DIVIDE, intlit(3)));
        successExpect("2 % 3", new BinaryExpressionNode(null, intlit(2), REMAINDER, intlit(3)));

        successExpect("1.0 + 2.0", new BinaryExpressionNode(null, floatlit(1), ADD, floatlit(2)));
        successExpect("2.0 - 1.0", new BinaryExpressionNode(null, floatlit(2), SUBTRACT, floatlit(1)));
        successExpect("2.0 * 3.0", new BinaryExpressionNode(null, floatlit(2), MULTIPLY, floatlit(3)));
        successExpect("2.0 / 3.0", new BinaryExpressionNode(null, floatlit(2), DIVIDE, floatlit(3)));
        successExpect("2.0 % 3.0", new BinaryExpressionNode(null, floatlit(2), REMAINDER, floatlit(3)));

        successExpect("2 * (4-1) * 4.0 / 6 % (2+1)", new BinaryExpressionNode(null,
            new BinaryExpressionNode(null,
                new BinaryExpressionNode(null,
                    new BinaryExpressionNode(null,
                        intlit(2),
                        MULTIPLY,
                        new ParenthesizedNode(null, new BinaryExpressionNode(null,
                            intlit(4),
                            SUBTRACT,
                            intlit(1)))),
                    MULTIPLY,
                    floatlit(4d)),
                DIVIDE,
                intlit(6)),
            REMAINDER,
            new ParenthesizedNode(null, new BinaryExpressionNode(null,
                intlit(2),
                ADD,
                intlit(1)))));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayStructAccess () {
        rule = grammar.expression;
        successExpect("[1][0]", new ArrayAccessNode(null,
            new ArrayLiteralNode(null, asList(intlit(1))), intlit(0)));
        successExpect("[1].Length", new FieldAccessNode(null,
            new ArrayLiteralNode(null, asList(intlit(1))), "Length"));
        successExpect("P.X", new FieldAccessNode(null, new ReferenceNode(null, "P"), "X"));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testDeclarations() {
        rule = grammar.statement;
        // additional test for Term declaration !
        successExpect("var X: Term = #like", new VarDeclarationNode(null,
            "X", new SimpleTypeNode(null, "Term"), new TermNode(null, "#like")));

        //------------------------------------------------------------------------------------------
        successExpect("var X: Int = 1", new VarDeclarationNode(null,
            "X", new SimpleTypeNode(null, "Int"), intlit(1)));

        successExpect("struct P {}", new StructDeclarationNode(null, "P", asList()));

        successExpect("struct P { var X: Int; var Y: Int }",
            new StructDeclarationNode(null, "P", asList(
                new FieldDeclarationNode(null, "X", new SimpleTypeNode(null, "Int")),
                new FieldDeclarationNode(null, "Y", new SimpleTypeNode(null, "Int")))));

        successExpect("fun f (X: Int): Int { return 1 }",
            new FunDeclarationNode(null, "f",
                asList(new ParameterNode(null, "X", new SimpleTypeNode(null, "Int"))),
                new SimpleTypeNode(null, "Int"),
                new BlockNode(null, asList(new ReturnNode(null, intlit(1))))));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testStatements() {
        rule = grammar.statement;

        successExpect("return", new ReturnNode(null, null));
        successExpect("return 1", new ReturnNode(null, intlit(1)));
        successExpect("print(1)", new ExpressionStatementNode(null,
            new FunCallNode(null, new ReferenceNode(null, "print"), asList(intlit(1)))));
        successExpect("{ return }", new BlockNode(null, asList(new ReturnNode(null, null))));


        successExpect("if true return 1 else return 2", new IfNode(null, new ReferenceNode(null, "true"),
            new ReturnNode(null, intlit(1)),
            new ReturnNode(null, intlit(2))));

        successExpect("if false return 1 else if true return 2 else return 3 ",
            new IfNode(null, new ReferenceNode(null, "false"),
                new ReturnNode(null, intlit(1)),
                new IfNode(null, new ReferenceNode(null, "true"),
                    new ReturnNode(null, intlit(2)),
                    new ReturnNode(null, intlit(3)))));

        successExpect("while 1 < 2 { return } ", new WhileNode(null,
            new BinaryExpressionNode(null, intlit(1), LOWER, intlit(2)),
            new BlockNode(null, asList(new ReturnNode(null, null)))));
    }

    @Test public void testGenerics() {
        rule = grammar.fun_decl;

        successExpect("template<T> testFloat(x:Float):Float { return x }", new FunDeclarationNode(
                null, new GenericDeclarationNode(null, "T"),
                "testFloat",
                Collections.singletonList(new ParameterNode(null, "x", new SimpleTypeNode(null, "Float"))),
                new SimpleTypeNode(null, "Float"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null, "x"))))
            )
        );

        successExpect("template<T> testString(str:T):String { return str}", new FunDeclarationNode(
                null, new GenericDeclarationNode(null, "T"),
                "testString",
                Collections.singletonList(new ParameterNode(null, "str", new SimpleTypeNode(null, "T"))),
                new SimpleTypeNode(null, "String"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null, "str"))))
            )
        );

        successExpect("template<T>  testTemplate(x:T):T { return x }", new FunDeclarationNode(
                null,  new GenericDeclarationNode(null, "T"),
                "testTemplate",
                Collections.singletonList(new ParameterNode(null, "x",
                    new SimpleTypeNode(null, "T"))), new SimpleTypeNode(null, "T"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null, "x"))))
            )
        );

        rule = grammar.suffix_expression;

        successExpect("genericInt<Int>(11, 10)",
            new FunCallNode(null,
                new SimpleTypeNode(null, "Int"),
                new ReferenceNode(null, "genericInt"),
                asList(intlit(11), intlit(10))));

        successExpect("genericFloat<Float>(12.0, 22.0)",
            new FunCallNode(null,
                new SimpleTypeNode(null, "Float"),
                new ReferenceNode(null, "genericFloat"),
                asList(floatlit(12), floatlit(22))));

        successExpect("genericString<String>(\"Hello\", \"Java\")",
            new FunCallNode(null,
                new SimpleTypeNode(null, "String"),
                new ReferenceNode(null, "genericString"),
                asList(stringlit("Hello"), stringlit("Java"))));
    }

    // ---------------------------------------------------------------------------------------------
}

import norswap.autumn.AutumnTestFixture;
import norswap.autumn.positions.LineMapString;
import norswap.sigh.SemanticAnalysis;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.*;
import norswap.uranium.Reactor;
import norswap.uranium.UraniumTestFixture;
import norswap.utils.visitors.Walker;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;

/**
 * NOTE(norswap): These tests were derived from the {@link InterpreterTests} and don't test anything
 * more, but show how to idiomatically test semantic analysis. using {@link UraniumTestFixture}.
 */
public final class SemanticAnalysisTests extends UraniumTestFixture
{
    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final AutumnTestFixture autumnFixture = new AutumnTestFixture();

    {
        autumnFixture.rule = grammar.root();
        autumnFixture.runTwice = false;
        autumnFixture.bottomClass = this.getClass();
    }

    private String input;

    @Override protected Object parse (String input) {
        this.input = input;
        return autumnFixture.success(input).topValue();
    }

    @Override protected String astNodeToString (Object ast) {
        LineMapString map = new LineMapString("<test>", input);
        return ast.toString() + " (" + ((SighNode) ast).span.startString(map) + ")";
    }

    // ---------------------------------------------------------------------------------------------

    @Override protected void configureSemanticAnalysis (Reactor reactor, Object ast) {
        Walker<SighNode> walker = SemanticAnalysis.createWalker(reactor);
        walker.walk(((SighNode) ast));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testLiteralsAndUnary() {
        successInput("return 42");
        successInput("return 42.0");
        successInput("return \"hello\"");
        successInput("return (42)");
        successInput("return [1, 2, 3]");
        successInput("return true");
        successInput("return false");
        successInput("return null");
        successInput("return !false");
        successInput("return !true");
        successInput("return !!true");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testNumericBinary() {
        successInput("return 1 + 2");
        successInput("return 2 - 1");
        successInput("return 2 * 3");
        successInput("return 2 / 3");
        successInput("return 3 / 2");
        successInput("return 2 % 3");
        successInput("return 3 % 2");

        successInput("return 1.0 + 2.0");
        successInput("return 2.0 - 1.0");
        successInput("return 2.0 * 3.0");
        successInput("return 2.0 / 3.0");
        successInput("return 3.0 / 2.0");
        successInput("return 2.0 % 3.0");
        successInput("return 3.0 % 2.0");

        successInput("return 1 + 2.0");
        successInput("return 2 - 1.0");
        successInput("return 2 * 3.0");
        successInput("return 2 / 3.0");
        successInput("return 3 / 2.0");
        successInput("return 2 % 3.0");
        successInput("return 3 % 2.0");

        successInput("return 1.0 + 2");
        successInput("return 2.0 - 1");
        successInput("return 2.0 * 3");
        successInput("return 2.0 / 3");
        successInput("return 3.0 / 2");
        successInput("return 2.0 % 3");
        successInput("return 3.0 % 2");

        failureInputWith("return 2 + true", "Trying to add Int with Bool");
        failureInputWith("return true + 2", "Trying to add Bool with Int");
        failureInputWith("return 2 + [1]", "Trying to add Int with Int[]");
        failureInputWith("return [1] + 2", "Trying to add Int[] with Int");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testOtherBinary() {
        successInput("return true && false");
        successInput("return false && true");
        successInput("return true && true");
        successInput("return true || false");
        successInput("return false || true");
        successInput("return false || false");
        //XOR operation
        successInput("return false ^ true");

        failureInputWith("return false || 1",
            "Attempting to perform binary logic on non-boolean type: Int");
        failureInputWith("return 2 || true",
            "Attempting to perform binary logic on non-boolean type: Int");
        //XOR operation
        failureInputWith("return 1^2",
            "Attempting to perform binary logic on non-boolean type: Int");

        successInput("return 1 + \"a\"");
        successInput("return \"a\" + 1");
        successInput("return \"a\" + true");

        successInput("return 1 == 1");
        successInput("return 1 == 2");
        successInput("return 1.0 == 1.0");
        successInput("return 1.0 == 2.0");
        successInput("return true == true");
        successInput("return false == false");
        successInput("return true == false");
        successInput("return 1 == 1.0");

        failureInputWith("return true == 1", "Trying to compare incomparable types Bool and Int");
        failureInputWith("return 2 == false", "Trying to compare incomparable types Int and Bool");

        successInput("return \"hi\" == \"hi\"");
        successInput("return [1] == [1]");

        successInput("return 1 != 1");
        successInput("return 1 != 2");
        successInput("return 1.0 != 1.0");
        successInput("return 1.0 != 2.0");
        successInput("return true != true");
        successInput("return false != false");
        successInput("return true != false");
        successInput("return 1 != 1.0");

        failureInputWith("return true != 1", "Trying to compare incomparable types Bool and Int");
        failureInputWith("return 2 != false", "Trying to compare incomparable types Int and Bool");

        successInput("return \"hi\" != \"hi\"");
        successInput("return [1] != [1]");
    }

    // ---------------------------------------------------------------------------------------------
    // LP Semantic tests !

    @Test void testFact(){
        successInput("LP sing ( #harry )");
        successInput("LP sing ( #harry ); LP sing(#niall)");
        successInput("LP song (#Style,#taylorSwift)");
        successInput("LP feat( #drake,#rihanna, #takecare )");

        failureInputWith("LP session( august )","Could not resolve: august");
        failureInputWith("var X: String= \"term\"; LP sing( X ) ", "non term type found where term type required instead of String");
    }

    @Test void testQuery(){
        successInput("LP boy(#smtg); -? boy( #baby )");
        successInput("-? boy( #baby )");

        successInput("-? mother( #lilly, #harry)");
        successInput("var X: Term= #param; -? query( X )");

        failureInputWith(" -? present(student)","Could not resolve: student");
        failureInputWith("var X: String= \"student\"; -? present(X) ", "non term type found where term type required instead of String");
    }

    @Test void  testClause(){

        successInput("LPC animal(#a) :- dog(#a) ");

        successInput("LPC sister(#a,#b) :- mom(#a,#c) , mom(#b,#c)");

        successInput("var X :Term = #a; LPC animal(X) :- dog(X)");

        successInput("var X:Term = #a; var Y:Term = #b; LPC sibling(X,Y) :- mother(#a,X), mother(#a,Y)");

        failureInputWith("LPC animal(  puppy) :- dog( puppy)","Could not resolve: puppy");
        failureInputWith("var X :Int = 1; LPC animal(X) :- dog(X)","non term type found where term type required instead of Int");


    }

    @Test public void testVarDecl() {

        successInput("var X: Term = #like; return X");
        successInput("var X: Term = #lilia; LP sing( X )  ");

        //--------------------------------------------------------------------
        successInput("var x: Int = 1; return x");
        successInput("var x: Float = 2.0; return x");
        successInput("var x: Int = 0; return x = 3");
        successInput("var x: String = \"tru\"; return x = \"S\"");

        failureInputWith("var x: Int = true", "expected Int but got Bool");
        failureInputWith("return x + 1", "Could not resolve: x");
        failureInputWith("return x + 1; var x: Int = 2", "Variable used before declaration: x");

        // implicit conversions
        successInput("var x: Float = 1 ; x = 2");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testRootAndBlock () {
        successInput("return");
        successInput("return 1");
        successInput("return 1; return 2");

        successInput("print(\"a\")");
        successInput("print(\"a\" + 1)");
        successInput("print(\"a\"); print(\"b\")");

        successInput("{ print(\"a\"); print(\"b\") }");

        successInput(
            "var x: Int = 1;" +
            "{ print(\"\" + x); var x: Int = 2; print(\"\" + x) }" +
            "print(\"\" + x)");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testCalls() {
        successInput(
            "fun add (a: Int, b: Int): Int { return a + b } " +
            "return add(4, 7)");

        successInput(
            "struct Point { var x: Int; var y: Int }" +
            "return $Point(1, 2)");

        successInput("var str: String = null; return print(str + 1)");

        failureInputWith("return print(1)", "argument 0: expected String but got Int");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayStructAccess() {
        successInput("return [1][0]");
        successInput("return [1.0][0]");
        successInput("return [1, 2][1]");

        failureInputWith("return [1][true]", "Indexing an array using a non-Int-valued expression");

        // TODO make this legal?
        // successInput("[].length", 0L);

        successInput("return [1].length");
        successInput("return [1, 2].length");

        successInput("var array: Int[] = null; return array[0]");
        successInput("var array: Int[] = null; return array.length");

        successInput("var x: Int[] = [0, 1]; x[0] = 3; return x[0]");
        successInput("var x: Int[] = []; x[0] = 3; return x[0]");
        successInput("var x: Int[] = null; x[0] = 3");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "return $P(1, 2).y");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "var p: P = null;" +
            "return p.y");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "var p: P = $P(1, 2);" +
            "p.y = 42;" +
            "return p.y");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "var p: P = null;" +
            "p.y = 42");

        failureInputWith(
            "struct P { var x: Int; var y: Int }" +
            "return $P(1, true)",
            "argument 1: expected Int but got Bool");

        failureInputWith(
            "struct P { var x: Int; var y: Int }" +
            "return $P(1, 2).z",
            "Trying to access missing field z on struct P");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testIfWhileFor () {
        successInput("if (true) return 1 else return 2");
        successInput("if (false) return 1 else return 2");
        successInput("if (false) return 1 else if (true) return 2 else return 3 ");
        successInput("if (false) return 1 else if (false) return 2 else return 3 ");

        successInput("var i: Int = 0; while (i < 3) { print(\"\" + i); i = i + 1 } ");

        successInput("for ( var x : Int=0 : x < 3 : ++ x) { print(\"\" + x); x = x + 1 } ");

        failureInputWith("if 1 return 1",
            "If statement with a non-boolean condition of type: Int");
        // Added Test for for loop
        failureInputWith("for ( var x : Float=0 : x < 3 : ++ x) { return 2 }",
            "Trying to increment type: Float","Intialization variable is non-integer:Float");

        failureInputWith("while 1 return 1",
            "While statement with a non-boolean condition of type: Int");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testInference() {
        successInput("var array: Int[] = []");
        successInput("var array: String[] = []");
        successInput("fun use_array (array: Int[]) {} ; use_array([])");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testTypeAsValues() {
        successInput("struct S{} ; return \"\"+ S");
        successInput("struct S{} ; var type: Type = S ; return \"\"+ type");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testUnconditionalReturn()
    {
        successInput("fun f(): Int { if (true) return 1 else return 2 } ; return f()");

        // TODO: would be nice if this pinpointed the if-statement as missing the return,
        //   not the whole function declaration
        failureInputWith("fun f(): Int { if (true) return 1 } ; return f()",
            "Missing return in function");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testGenericOperations() {

        /* Float Operations*/

        successInput(
            "template<T> floatSum (x: T, y: T): T { return x + y } " +
                "return floatSum<Float>(11.0, 12.0)"
        );

        successInput(
            "template<T> floatMult (x: T, y: T): T { return x * y } " +
                "return floatMult<Float>(13.0, 12.0)"
        );

        /* Integer Operations*/

        successInput(
            "template<T> intSum (x: T, y: T): T { return x + y } " +
                "return intSum<Int>(22, 12)"
        );

        successInput(
            "template<T> intMult (x: T, y: T): T { return x * y } " +
                "return intMult<Int>(22, 12)"
        );

        /* String Operations*/
        successInput(
            "template<T> stringConcat (x: T, y: T): T { return x + y } " +
                "return stringConcat<String>(\"Hello\", \"Java\")"
        );

        /* Testing the Failure Scenarios */

        failureInputWith(
            "template<T>  failStringOperation (a: T, b: T): T { return a * b } return failStringOperation<String>(\"Hello\", \"Java\")",
            "Cannot used multiply operation in strings");


        failureInputWith(
            "template<A>  definitionError(x: T, y: T): T { return x * y } ",
            "could not resolve: T","T should be used as Template Parameter instead of A");

        failureInputWith(
            "template<T>  testErr(x: T, y: T): T { return x - y } " +
                "return testErr<Int>(2)",
            "wrong number of arguments, expected 2 but got 1");


        failureInputWith(
            "template<T>  argTypeError(x: T, y: T): T { return x + y } " +
                "return argTypeError<String>(2, 2)",
            "Template Error: Provided argument[0] Int type and required template parameter T is String Type",
            "Template Error: Provided argument[1] Int type and required template parameter T is String Type");


        failureInputWith(
            "template<T>  testError (x: T, y: T): T { return x + y }" +
                " return testError<String>(10.0)",
            "wrong number of arguments, expected 2 but got 1",
            "Template Error: Provided argument[0] Float type and required template parameter T is String Type"
        );

    }
}

package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;

//
// an atom is a predicate symbol with the right number of terms ex song(22, taylor swift)

public class AtomsNode extends ExpressionNode{

    public final String name;
    public final List<ExpressionNode> terms;

    public AtomsNode (Span span,Object name,Object terms) {
        super(span);
        this.name=Util.cast(name, String.class);
        this.terms=Util.cast(terms, List.class);
    }

    @Override
    public String contents () {   return name; }
    public String name() {return name;}

    //extract from ArrayLiteralNode.java
    /**  @Override public String contents ()
    {
    if (components.size() == 0)
    return "[]";

    int budget = contentsBudget() - 2; // 2 == "[]".length()
    StringBuilder b = new StringBuilder("[");
    int i = 0;

    for (ExpressionNode it: components)
    {
    if (i > 0) b.append(", ");
    String contents = it.contents();
    budget -= 2 + contents.length();
    if (i == components.size() - 1) {
    if (budget < 0) break;
    } else {
    if (budget - ", ...".length() < 0) break;
    }
    b.append(contents);
    ++i;
    }

    if (i < components.size())
    b.append("...");

    return b.append(']').toString();
    }**/


}

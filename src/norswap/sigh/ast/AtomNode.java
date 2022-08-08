package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;

//
// an atom is a predicate symbol with the right number of terms ex song(22, taylor swift)

public class AtomNode extends ExpressionNode{

    public final String name;
    public final List<ExpressionNode> terms;

    public AtomNode (Span span,Object name,Object terms) {
        super(span);
        this.name=Util.cast(name, String.class);
        this.terms=Util.cast(terms, List.class);
    }

    public String name() {return name;}

    //extract from ArrayLiteralNode.java
    @Override public String contents ()
    {
    if (terms.size() == 0)
    return "[]";

    int budget = contentsBudget() - 2; // 2 == "[]".length()
    StringBuilder b = new StringBuilder("[");
    int i = 0;

    for (ExpressionNode it: terms)
    {
    if (i > 0) b.append(", ");
    String contents = it.contents();
    budget -= 2 + contents.length();
    if (i == terms.size() - 1) {
    if (budget < 0) break;
    } else {
    if (budget - ", ...".length() < 0) break;
    }
    b.append(contents);
    ++i;
    }

    if (i < terms.size())
    b.append("...");

    return b.append(']').toString();
    }


}

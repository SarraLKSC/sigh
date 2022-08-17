package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;

public class QueryDeclarationNode extends DeclarationNode{
    public final String name;
    public final List<ExpressionNode> terms;
    public QueryDeclarationNode (Span span, Object name,Object terms) {
        super(span);
        this.name= Util.cast(name,String.class);
        this.terms= Util.cast(terms,List.class);

    }

    @Override
    public String name () {     return name; }

    @Override
    public String declaredThing () {
        return "Query ";
    }

    @Override
    public String contents () { return "Query "+ name; }
}
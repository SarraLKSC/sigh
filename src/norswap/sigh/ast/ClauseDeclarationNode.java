package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;


// a clause has the form A :- B, C,D... where A,B,C and D are atoms
public class ClauseDeclarationNode extends DeclarationNode {

    public final String name;
    public final List<ExpressionNode> terms;

    public ClauseDeclarationNode (Span span, Object name, Object terms) {
        super(span);
        this.name= Util.cast(name, String.class);
        this.terms=Util.cast(terms, List.class);
    }

    @Override
    public String name () {
        return name;
    }

    @Override
    public String declaredThing () {
        return null;
    }

    @Override
    public String contents () {
        return name;
    }
}

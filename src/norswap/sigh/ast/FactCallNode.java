package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;

public class FactCallNode extends ExpressionNode
{
        public final ExpressionNode fact;
        public final List<ExpressionNode> terms;


    public FactCallNode (Span span,Object fact, Object terms) {
        super(span);
        this.fact= Util.cast(fact,ExpressionNode.class);
        this.terms= Util.cast(terms, List.class);
    }

    @Override
    public String contents () {
        String args = terms.size() == 0 ? "()" : "(...)";
        return fact.contents()+args;
    }
}

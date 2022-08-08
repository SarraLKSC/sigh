package norswap.sigh.ast;

import norswap.autumn.positions.Span;

public class FactCallNode extends ExpressionNode{


    public FactCallNode (Span span) {
        super(span);
    }

    @Override
    public String contents () {
        return null;
    }
}

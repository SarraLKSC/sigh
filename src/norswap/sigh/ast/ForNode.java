package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class ForNode extends StatementNode
{
    public final DeclarationNode initialization;
    public final ExpressionNode condition;
    public final ExpressionNode indec;
    public final StatementNode body;

    public ForNode (Span span,Object initial, Object condition,Object indec, Object body) {
        super(span);
        this.initialization = Util.cast(initial, DeclarationNode.class);
        this.condition = Util.cast(condition, ExpressionNode.class);
        this.indec = Util.cast(indec, ExpressionNode.class);
        this.body = Util.cast(body, StatementNode.class);
    }

    @Override public String contents ()
    {
        String candidate = String.format("for %s ...", condition.contents());

        return candidate.length() <= contentsBudget()
            ? candidate
            : "for (?) ...";
    }
}
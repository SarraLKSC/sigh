package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.HashMap;
import java.util.List;

public class FunCallNode extends ExpressionNode
{
    public final ExpressionNode function;
    public final List<ExpressionNode> arguments;
    public TypeNode expectedReturnType;
    public HashMap<String, TypeNode> mapTtoType;

    @SuppressWarnings("unchecked")
    public FunCallNode (Span span, Object function, Object arguments) {
        super(span);
        this.function = Util.cast(function, ExpressionNode.class);
        this.arguments = Util.cast(arguments, List.class);
    }

    @SuppressWarnings("unchecked")
    public FunCallNode(Span span, Object optionalArg, Object function, Object arguments) {
        super(span);
        this.expectedReturnType = Util.cast(optionalArg, TypeNode.class);
        this.function = Util.cast(function, ExpressionNode.class);
        this.arguments = Util.cast(arguments, List.class);
        this.mapTtoType = new HashMap<>();

    }

    @Override public String contents ()
    {
        String args = arguments.size() == 0 ? "()" : "(...)";
        return function.contents() + args;
    }
}

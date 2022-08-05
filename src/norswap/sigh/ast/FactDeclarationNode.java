package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;

public class FactDeclarationNode extends DeclarationNode{

    public final String name;
    public final List<TermNode> terms;

    public FactDeclarationNode (Span span, Object name, Object terms) {
        super(span);
        this.name = Util.cast(name, String.class);
        this.terms = Util.cast(terms, List.class);
    }


    @Override
    public String name () {
        return name;
    }

    @Override
    public String declaredThing () {
        return "rule";
    }

    @Override
    public String contents () {
        return "rule " + name ;
    }

}
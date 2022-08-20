package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.sigh.types.Type;
import norswap.utils.Util;

public final class GenericDeclarationNode extends DeclarationNode{
    public final String name;
    public Type type;

    public GenericDeclarationNode(Span span, Object name) {
        super(span);
        this.name = Util.cast(name, String.class);
        this.type = null;
    }

    @Override public String contents () {
        return name;
    }

    @Override public String name() {
        return name;
    }

    @Override public String declaredThing() {
        return "template";
    }
}

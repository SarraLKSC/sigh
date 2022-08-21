package norswap.sigh.types;

import norswap.sigh.ast.GenericDeclarationNode;

public final class GenericType extends Type
{
    public final GenericDeclarationNode node;

    public GenericType(GenericDeclarationNode node) {
        this.node = node;
    }

    @Override public String name() {
        return node.name();
    }

    @Override public boolean isPrimitive () {
        return false;
    }

}

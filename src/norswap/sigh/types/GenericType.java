package norswap.sigh.types;

import norswap.sigh.ast.GenericDeclarationNode;

public final class GenericType extends Type
{
    public final GenericDeclarationNode node;

    public GenericType(GenericDeclarationNode node) {
        this.node = node;
    }

    public Type checkType() {
        return this.node.type != null ? this.node.type : this;
    }

    @Override public String name() {
        return node.name();
    }

    @Override public boolean isPrimitive () {
        return false;
    }

}

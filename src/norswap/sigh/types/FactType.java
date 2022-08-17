package norswap.sigh.types;

import norswap.sigh.ast.FactDeclarationNode;

public class FactType extends Type{
    public final FactDeclarationNode node;

    public FactType(FactDeclarationNode node) {this.node=node;}

    @Override public String name () {
        return node.name;
    }

    @Override public boolean equals (Object o) {
        return this == o || o instanceof FactType && this.node == ((FactType) o).node;
    }

    public int hashCode () {return node.hashCode();}
}

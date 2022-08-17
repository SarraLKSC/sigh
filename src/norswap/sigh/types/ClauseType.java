package norswap.sigh.types;

import norswap.sigh.ast.ClauseDeclarationNode;

public class ClauseType extends Type{
 public final ClauseDeclarationNode node;
 public ClauseType ( ClauseDeclarationNode node) {this.node=node; }

    @Override
    public String name () {
        return node.name.name();
    }
}

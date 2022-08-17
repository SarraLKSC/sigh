package norswap.sigh.types;

import norswap.sigh.ast.QueryDeclarationNode;

public class QueryType extends  Type{
    public final QueryDeclarationNode node;

    public QueryType(QueryDeclarationNode node){this.node=node;}
    @Override
    public String name () {
        return node.name;
    }
}

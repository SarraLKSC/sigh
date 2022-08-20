package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;

public class QueryDeclarationNode extends DeclarationNode{
  /**  public final String name;
    public final List<ExpressionNode> terms;
    public QueryDeclarationNode (Span span, Object name,Object terms) {
        super(span);
        this.name= Util.cast(name,String.class);
        this.terms= Util.cast(terms,List.class);

    }**/
  public final AtomNode atom;
  public QueryDeclarationNode(Span span, Object node){
      super(span);
      this.atom=Util.cast(node,AtomNode.class);
  }

    @Override
    public String name () {     return atom.name(); }

    @Override
    public String declaredThing () {
        return "Query ";
    }

    @Override
    public String contents () { return "Query "+ atom.name; }
}

package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;


// a clause has the form A :- B, C,D... where A,B,C and D are atoms
public class ClauseDeclarationNode extends DeclarationNode {

    public final String name;
    public final List<ExpressionNode> left_terms;
    public final List<AtomNode> right_atoms;

    public ClauseDeclarationNode (Span span, Object name, Object left_terms, Object right_atoms) {
        super(span);
        this.name= Util.cast(name, String.class);
        this.left_terms=Util.cast(left_terms, List.class);
        this.right_atoms=Util.cast(right_atoms,List.class);
    }


    @Override
    public String name () {
        return name;
    }
    @Override
    public String declaredThing () {
        String[] tmp = new String[left_terms.size()];
        for(int i = 0; i<tmp.length; i++){
            ExpressionNode atom = left_terms.get(i);
            if(atom instanceof TermNode)
                tmp[i] = ((TermNode) atom).name;
            else if (atom instanceof ReferenceNode)
                tmp[i] = ((ReferenceNode) atom).name;
        }
        return "(" + String.join(", ", tmp) + ")";
    }

    @Override
    public String contents () {
        return "fact " + name;
    }
}

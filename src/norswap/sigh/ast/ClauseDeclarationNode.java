package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;


// a clause has the form A :- B, C,D... where A,B,C and D are atoms
public class ClauseDeclarationNode extends DeclarationNode {

    public final AtomNode left_atom;
    public final List<AtomNode> right_atoms;

    public ClauseDeclarationNode (Span span, Object left_atom, Object right_atoms) {
        super(span);
        this.left_atom= Util.cast(left_atom, AtomNode.class);
        this.right_atoms=Util.cast(right_atoms,List.class);
    }


    @Override
    public String name () {     return left_atom.name; }
    @Override
    public String declaredThing () {
        return "rule";
    }

    @Override
    public String contents () {
        return "rule " + left_atom.name;
    }
}

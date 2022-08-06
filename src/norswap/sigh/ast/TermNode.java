package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

// a term is a symbol that is representative of a predicate, variable...
// ex: song, 22 and taylor swift are terms in the song(22,taylor swift) fact

public class TermNode extends ExpressionNode {

    public final String name;

    public TermNode (Span span, Object name) {
        super(span);
        this.name = Util.cast(name, String.class);
       // System.out.println("yo inside term constructor: "+name);
    }

    @Override
    public String contents () {
        return name;
    }
}


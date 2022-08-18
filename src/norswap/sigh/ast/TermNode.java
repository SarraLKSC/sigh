package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

// a term is a symbol that is representative of a predicate, variable... (we'll use it as a primitive type)
// ex: song, 22 and taylor swift are terms in the song(22,taylor swift) fact

public class TermNode extends ExpressionNode {

    public final String value;

    public TermNode (Span span, Object value) {
        super(span);
        this.value = Util.cast(value, String.class);
       // System.out.println("yo inside term constructor: "+name);
    }
    public String value(){return value;}
    @Override
    public String contents () {
        return value;
    }
}


package norswap.sigh.ast;

public enum UnaryOperator
{
    NOT("!"),
    INCRE("++"),
    DECRE("--");

    public final String string;

    UnaryOperator (String string) {
        this.string = string;
    }
}

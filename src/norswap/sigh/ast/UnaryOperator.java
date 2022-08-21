package norswap.sigh.ast;

public enum UnaryOperator
{
    NOT("!"),
    INCRE("++");

    public final String string;

    UnaryOperator (String string) {
        this.string = string;
    }
}

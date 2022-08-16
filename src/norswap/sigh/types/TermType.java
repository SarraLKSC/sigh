package norswap.sigh.types;

public class TermType extends Type {

    public static final TermType INSTANCE = new TermType();

    private TermType(){};

    @Override
    public boolean isPrimitive () {
        return true;
    }

    @Override
    public String name () {
        return "Term";
    }
}

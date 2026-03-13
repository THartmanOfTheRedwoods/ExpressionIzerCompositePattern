import edu.redwoods.*;

public class Main {
    public static void main(String[] args) {
        // Example: (3 + 4) * x + 0   should simplify to 7 * x
        Expression three = new Constant(3);
        Expression four = new Constant(4);
        Expression add1 = new NaryExpression(Operator.ADD, three, four);
        Expression x = new Variable("x");
        Expression mul = new NaryExpression(Operator.MUL, add1, x);
        Expression zero = new Constant(0);
        Expression root = new NaryExpression(Operator.ADD, mul, zero);

        System.out.println("Original: " + root);
        Expression simplified = root.simplify();
        System.out.println("Simplified: " + simplified);
        System.out.println();

        // More tests
        test("x * 1 + 0", new NaryExpression(Operator.ADD,
                new NaryExpression(Operator.MUL, new Variable("x"), new Constant(1)),
                new Constant(0)));

        test("x - x", new BinaryExpression(Operator.SUB, new Variable("x"), new Variable("x")));

        test("0 / x", new BinaryExpression(Operator.DIV, new Constant(0), new Variable("x")));

        test("x / x", new BinaryExpression(Operator.DIV, new Variable("x"), new Variable("x")));

        test("(2+3) * (4-4)", new NaryExpression(Operator.MUL,
                new NaryExpression(Operator.ADD, new Constant(2), new Constant(3)),
                new BinaryExpression(Operator.SUB, new Constant(4), new Constant(4))));

        test("0/0", new BinaryExpression(Operator.DIV, new Constant(0), new Constant(0)));

        // Test distribution with growth check (should not apply if it makes things bigger)
        // (x+1)*2  -> 2*x + 2  (size: original 3 nodes? Actually (x+1) size 3, *2 makes 4; expanded: (2*x)+(2*1) = 5 nodes, then simplified to 2*x+2 maybe 5 nodes? Actually after constant folding 2*1->2, still 5 nodes. So new size 5 > original 4, so rule won't apply. Good.)
        Expression distTest = new NaryExpression(Operator.MUL,
                new NaryExpression(Operator.ADD, new Variable("x"), new Constant(1)),
                new Constant(2));
        System.out.println("Distribution test (should not expand): " + distTest);
        System.out.println("Simplified: " + distTest.simplify());
        System.out.println();

        // Test power rules
        Expression powerTest = new BinaryExpression(Operator.POW,
                new Variable("x"),
                new Constant(1));
        System.out.println("x^1: " + powerTest + " -> " + powerTest.simplify());

        Expression powerMul = new NaryExpression(Operator.MUL,
                new BinaryExpression(Operator.POW, new Variable("x"), new Constant(2)),
                new BinaryExpression(Operator.POW, new Variable("x"), new Constant(3)));
        System.out.println("x^2 * x^3: " + powerMul + " -> " + powerMul.simplify());
    }

    private static void test(String description, Expression expr) {
        System.out.println(description + " : " + expr);
        System.out.println("  simplifies to : " + expr.simplify());
        System.out.println();
    }
}
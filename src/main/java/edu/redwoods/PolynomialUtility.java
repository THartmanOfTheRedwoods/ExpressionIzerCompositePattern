package edu.redwoods;

import java.util.Optional;

public class PolynomialUtility {
    /** Extracts coefficient: 5*x -> 5, x -> 1, Constant(10) -> 10 */
    public static double getCoefficient(Expression e) {
        if (e instanceof Constant) return ((Constant) e).getValue();
        if (e instanceof BinaryExpression && ((BinaryExpression) e).getOp() == Operator.MUL) {
            if (((BinaryExpression) e).getLeft() instanceof Constant)
                return ((Constant) ((BinaryExpression) e).getLeft()).getValue();
        }
        return 1.0;
    }

    /** Extracts base: 5*x -> x, x -> x, Constant(10) -> 1 (unit) */
    public static Expression getBase(Expression e) {
        if (e instanceof Constant) return new Constant(1);
        if (e instanceof BinaryExpression && ((BinaryExpression) e).getOp() == Operator.MUL) {
            if (((BinaryExpression) e).getLeft() instanceof Constant)
                return ((BinaryExpression) e).getRight();
        }
        return e;
    }

    /** * Robust GCF: Handles constants, variables, and shared sub-trees.
     * E.g. GCF(2*(x+1), 4*(x+1)) -> 2*(x+1)
     */
    public static Optional<Expression> getGCF(Expression l, Expression r) {
        if (l.equals(r)) return Optional.of(l);

        // GCD of two constants
        if (l instanceof Constant && r instanceof Constant) {
            return Optional.of(new Constant(gcd((long)((Constant)l).getValue(), (long)((Constant)r).getValue())));
        }

        // Case: MUL(c1, P) and MUL(c2, P) -> GCF(c1, c2) * P
        if (l instanceof BinaryExpression && ((BinaryExpression)l).getOp() == Operator.MUL &&
                r instanceof BinaryExpression && ((BinaryExpression)r).getOp() == Operator.MUL) {
            BinaryExpression bl = (BinaryExpression) l;
            BinaryExpression br = (BinaryExpression) r;
            if (bl.getRight().equals(br.getRight())) {
                Optional<Expression> coeffGcf = getGCF(bl.getLeft(), br.getLeft());
                return coeffGcf.map(c -> new BinaryExpression(Operator.MUL, c, bl.getRight()));
            }
        }

        // Case: MUL(c, P) and P -> GCF(c, 1) * P -> 1 * P
        if (l instanceof BinaryExpression && ((BinaryExpression)l).getOp() == Operator.MUL && ((BinaryExpression)l).getRight().equals(r)) return Optional.of(r);
        if (r instanceof BinaryExpression && ((BinaryExpression)r).getOp() == Operator.MUL && ((BinaryExpression)r).getRight().equals(l)) return Optional.of(l);

        return Optional.empty();
    }

    private static long gcd(long a, long b) {
        return b == 0 ? Math.abs(a) : gcd(b, a % b);
    }
}
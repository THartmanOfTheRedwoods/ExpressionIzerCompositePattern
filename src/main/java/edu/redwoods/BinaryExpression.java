package edu.redwoods;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Binary Composite Expression for non-associative, non-commutative operators:
 * SUB, DIV, and POW.
 *
 * COMPOSITE PATTERN ROLE
 *   This is still a Composite node — it holds two child Expressions.
 *   It no longer handles ADD or MUL; those are owned by NaryExpression.
 *
 * SUB HANDLING
 *   During simplify(), SUB is converted to an NaryExpression(ADD) + negation
 *   so that all additive cancellation is handled by the N-ary engine in one
 *   pass.  The BinaryExpression(SUB) node itself never survives simplification.
 *
 * DIV HANDLING
 *   Standard identity reductions: 0/x=0, x/1=x, x/x=1, constant folding.
 *   Non-reducible forms are kept as BinaryExpression(DIV).
 *
 * POW HANDLING
 *   Identity reductions: x^0=1, x^1=x, 0^x=0, 1^x=1, constant folding,
 *   and the power-of-a-power rule (x^a)^b → x^(a·b).
 */
public class BinaryExpression implements Expression {

    private final Operator   op;
    private final Expression left;
    private final Expression right;

    public BinaryExpression(Operator op, Expression left, Expression right) {
        this.op    = op;
        this.left  = left;
        this.right = right;
    }

    public Operator   getOp()    { return op; }
    public Expression getLeft()  { return left; }
    public Expression getRight() { return right; }

    // -----------------------------------------------------------------------
    //  Composite Pattern: simplify()
    // -----------------------------------------------------------------------

    @Override
    public Expression simplify() {
        // Post-order: simplify both children first.
        Expression sl = left.simplify();
        Expression sr = right.simplify();

        switch (op) {
            case SUB: return simplifySub(sl, sr);
            case DIV: return simplifyDiv(sl, sr);
            case POW: return simplifyPow(sl, sr);
            default:
                throw new IllegalStateException(
                        "BinaryExpression does not handle " + op
                                + ". Use NaryExpression for ADD / MUL.");
        }
    }

    // -----------------------------------------------------------------------
    //  SUB: convert to ADD + negation → hand off to NaryExpression engine
    // -----------------------------------------------------------------------

    private static Expression simplifySub(Expression l, Expression r) {
        // Fast-path identities before building a new node
        if (r instanceof Constant && ((Constant) r).getValue() == 0.0) return l;
        if (l.equals(r)) return new Constant(0);

        // a − b  ≡  a + (−b)
        // NaryExpression.simplify() will flatten, group like terms, and cancel.
        return new NaryExpression(Operator.ADD, l, NaryExpression.negate(r)).simplify();
    }

    // -----------------------------------------------------------------------
    //  DIV: constant folding and identity rules
    // -----------------------------------------------------------------------

    private static Expression simplifyDiv(Expression l, Expression r) {
        // Constant / constant
        if (l instanceof Constant && r instanceof Constant) {
            double a = ((Constant) l).getValue();
            double b = ((Constant) r).getValue();
            if (b != 0.0) return new Constant(a / b);
        }
        // x / 1 = x
        if (r instanceof Constant && ((Constant) r).getValue() == 1.0) return l;
        // x / -1 = -x
        if (r instanceof Constant && ((Constant) r).getValue() == -1.0)
            return NaryExpression.negate(l);
        // 0 / x = 0  (guard: x ≠ 0)
        if (l instanceof Constant && ((Constant) l).getValue() == 0.0
                && !(r instanceof Constant && ((Constant) r).getValue() == 0.0))
            return new Constant(0);
        // x / x = 1  (guard: x ≠ 0)
        if (l.equals(r)
                && !(r instanceof Constant && ((Constant) r).getValue() == 0.0))
            return new Constant(1);

        return new BinaryExpression(Operator.DIV, l, r);
    }

    // -----------------------------------------------------------------------
    //  POW: identity rules, constant folding, power-of-a-power
    // -----------------------------------------------------------------------

    private static Expression simplifyPow(Expression base, Expression exp) {
        // Both constants → fold
        if (base instanceof Constant && exp instanceof Constant) {
            double b = ((Constant) base).getValue();
            double e = ((Constant) exp).getValue();
            return new Constant(Math.pow(b, e));
        }
        // Exponent identities
        if (exp instanceof Constant) {
            double e = ((Constant) exp).getValue();
            if (e == 0.0) return new Constant(1);   // x^0 = 1
            if (e == 1.0) return base;               // x^1 = x
        }
        // Base identities
        if (base instanceof Constant) {
            double b = ((Constant) base).getValue();
            if (b == 0.0) return new Constant(0);   // 0^x = 0  (x ≠ 0 guarded by fold above)
            if (b == 1.0) return new Constant(1);   // 1^x = 1
        }
        // Expand small integer powers: (a + b)^n -> (a + b) * (a + b) ...
        if (exp instanceof Constant && base instanceof NaryExpression && ((NaryExpression)base).getOp() == Operator.ADD) {
            double e = ((Constant) exp).getValue();
            // Only expand if it's a positive integer exponent (e.g., ^2, ^3)
            if (e > 1 && e == Math.floor(e)) {
                List<Expression> expandedFactors = new ArrayList<>();
                for (int i = 0; i < (int) e; i++) {
                    expandedFactors.add(base);
                }
                // Hand it over to the MUL engine which already knows how to FOIL!
                return new NaryExpression(Operator.MUL, expandedFactors).simplify();
            }
        }
        // Power-of-a-power: (x^a)^b → x^(a·b)
        if (base instanceof BinaryExpression) {
            BinaryExpression inner = (BinaryExpression) base;
            if (inner.getOp() == Operator.POW) {
                Expression newExp =
                        new NaryExpression(Operator.MUL, inner.getRight(), exp).simplify();
                return new BinaryExpression(Operator.POW, inner.getLeft(), newExp).simplify();
            }
        }
        return new BinaryExpression(Operator.POW, base, exp);
    }

    // -----------------------------------------------------------------------
    //  size / toString / equals / hashCode
    // -----------------------------------------------------------------------

    @Override
    public int size() {
        return 1 + left.size() + right.size();
    }

    @Override
    public String toString() {
        return "(" + left + " " + op.getSymbol() + " " + right + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BinaryExpression)) return false;
        BinaryExpression that = (BinaryExpression) o;
        return op == that.op
                && Objects.equals(left,  that.left)
                && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(op, left, right);
    }
}
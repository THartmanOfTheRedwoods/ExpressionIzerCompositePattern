package edu.redwoods;

import java.util.Objects;
import java.util.Optional;

// Composite Binary edu.redwoods.Expression
public class BinaryExpression implements Expression {
    private final Operator op;
    private final Expression left;
    private final Expression right;

    public BinaryExpression(Operator op, Expression left, Expression right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }

    public Operator getOp() {
        return op;
    }

    public Expression getLeft() {
        return left;
    }

    public Expression getRight() {
        return right;
    }

    @Override
    public Expression simplify() {
        // Post‑order: simplify children first
        Expression simplifiedLeft = left.simplify();
        Expression simplifiedRight = right.simplify();

        // Try to apply a simplification rule
        for (Rule rule : SimplificationRules.getRules()) {
            Optional<Expression> result = rule.apply(op, simplifiedLeft, simplifiedRight);
            if (result.isPresent()) {
                // Recursively simplify the result (may enable further rules)
                return result.get().simplify();
            }
        }

        // No rule applied: return a new node with the (already simplified) children
        return new BinaryExpression(op, simplifiedLeft, simplifiedRight);
    }

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
        if (o == null || getClass() != o.getClass()) return false;
        BinaryExpression that = (BinaryExpression) o;
        return op == that.op &&
                Objects.equals(left, that.left) &&
                Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(op, left, right);
    }
}

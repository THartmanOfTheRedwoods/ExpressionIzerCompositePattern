package edu.redwoods;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimplificationRules {
    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // 1. CONSTANT FOLDING (Highest priority)
        RULES.add((op, l, r) -> {
            if (l instanceof Constant && r instanceof Constant) {
                double a = ((Constant) l).getValue();
                double b = ((Constant) r).getValue();
                switch (op) {
                    case ADD: return Optional.of(new Constant(a + b));
                    case SUB: return Optional.of(new Constant(a - b));
                    case MUL: return Optional.of(new Constant(a * b));
                    case DIV: return Optional.of(new Constant(b == 0 ? 0 : a / b)); // 0/0 -> 0 per user
                    case POW: return Optional.of(new Constant(Math.pow(a, b)));
                }
            }
            return Optional.empty();
        });

        // 2. YOUR ORIGINAL IDENTITY RULES
        RULES.add((op, l, r) -> {
            if (op == Operator.ADD) {
                if (l instanceof Constant && ((Constant) l).getValue() == 0) return Optional.of(r);
                if (r instanceof Constant && ((Constant) r).getValue() == 0) return Optional.of(l);
            }
            if (op == Operator.SUB) {
                if (r instanceof Constant && ((Constant) r).getValue() == 0) return Optional.of(l);
                if (l.equals(r)) return Optional.of(new Constant(0));
            }
            if (op == Operator.MUL) {
                if (l instanceof Constant && ((Constant) l).getValue() == 1) return Optional.of(r);
                if (r instanceof Constant && ((Constant) r).getValue() == 1) return Optional.of(l);
                if (l instanceof Constant && ((Constant) l).getValue() == 0) return Optional.of(new Constant(0));
                if (r instanceof Constant && ((Constant) r).getValue() == 0) return Optional.of(new Constant(0));
            }
            if (op == Operator.DIV) {
                if (l instanceof Constant && ((Constant) l).getValue() == 0) return Optional.of(new Constant(0));
                if (r instanceof Constant && ((Constant) r).getValue() == 1) return Optional.of(l);
                if (l.equals(r)) return Optional.of(new Constant(1));
            }
            if (op == Operator.POW) {
                if (r instanceof Constant && ((Constant) r).getValue() == 1) return Optional.of(l);
                if (r instanceof Constant && ((Constant) r).getValue() == 0) return Optional.of(new Constant(1));
            }
            return Optional.empty();
        });

        // 3. POWER ALGEBRA: x^a * x^b -> x^(a+b)
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL && l instanceof BinaryExpression && r instanceof BinaryExpression) {
                BinaryExpression lp = (BinaryExpression) l, rp = (BinaryExpression) r;
                if (lp.getOp() == Operator.POW && rp.getOp() == Operator.POW && lp.getLeft().equals(rp.getLeft()))
                    return Optional.of(new BinaryExpression(Operator.POW, lp.getLeft(), new BinaryExpression(Operator.ADD, lp.getRight(), rp.getRight())));
            }
            return Optional.empty();
        });

        // 4. ASSOCIATIVITY & COMMUTATIVITY (The key to your failing case)
        RULES.add((op, l, r) -> {
            // Sorting: Move constants/variables to a predictable order
            if ((op == Operator.ADD || op == Operator.MUL) && PolynomialUtility.getRank(l) > PolynomialUtility.getRank(r))
                return Optional.of(new BinaryExpression(op, r, l));

            // Rotations: (A + B) - C -> A + (B - C)
            if (op == Operator.SUB && l instanceof BinaryExpression && ((BinaryExpression)l).getOp() == Operator.ADD) {
                return Optional.of(new BinaryExpression(Operator.ADD, ((BinaryExpression)l).getLeft(),
                        new BinaryExpression(Operator.SUB, ((BinaryExpression)l).getRight(), r)));
            }
            // Rotations: A + (B - C) -> (A - C) + B
            if (op == Operator.ADD && r instanceof BinaryExpression && ((BinaryExpression)r).getOp() == Operator.SUB) {
                return Optional.of(new BinaryExpression(Operator.ADD,
                        new BinaryExpression(Operator.SUB, l, ((BinaryExpression)r).getRight()), ((BinaryExpression)r).getLeft()));
            }
            return Optional.empty();
        });

        // 5. EXPANSION (Distribution): a(b+c) -> ab + ac
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL) {
                if (r instanceof BinaryExpression && ((BinaryExpression) r).getOp() == Operator.ADD)
                    return Optional.of(new BinaryExpression(Operator.ADD, new BinaryExpression(Operator.MUL, l, ((BinaryExpression) r).getLeft()), new BinaryExpression(Operator.MUL, l, ((BinaryExpression) r).getRight())));
                if (l instanceof BinaryExpression && ((BinaryExpression) l).getOp() == Operator.ADD)
                    return Optional.of(new BinaryExpression(Operator.ADD, new BinaryExpression(Operator.MUL, ((BinaryExpression) l).getLeft(), r), new BinaryExpression(Operator.MUL, ((BinaryExpression) l).getRight(), r)));
            }
            return Optional.empty();
        });

        // 6. COLLECTION: 2x + 3x -> 5x
        RULES.add((op, l, r) -> {
            if (op == Operator.ADD || op == Operator.SUB) {
                Expression bL = PolynomialUtility.getBase(l), bR = PolynomialUtility.getBase(r);
                if (bL.equals(bR) && !(bL instanceof Constant)) {
                    double cL = PolynomialUtility.getCoefficient(l), cR = PolynomialUtility.getCoefficient(r);
                    return Optional.of(new BinaryExpression(Operator.MUL, new Constant(op == Operator.ADD ? cL + cR : cL - cR), bL));
                }
            }
            return Optional.empty();
        });

        // 7. CANCELLATION (Factor logic only used for division)
        RULES.add((op, l, r) -> {
            if (op == Operator.DIV) {
                return PolynomialUtility.getGCF(l, r).map(gcf -> {
                    if (l.equals(gcf) && r.equals(gcf)) return new Constant(1);
                    // Distribution of division: (A + B) / C -> A/C + B/C
                    if (l instanceof BinaryExpression && ((BinaryExpression)l).getOp() == Operator.ADD)
                        return new BinaryExpression(Operator.ADD, new BinaryExpression(Operator.DIV, ((BinaryExpression)l).getLeft(), r), new BinaryExpression(Operator.DIV, ((BinaryExpression)l).getRight(), r));
                    return null;
                });
            }
            return Optional.empty();
        });
    }

    public static List<Rule> getRules() { return RULES; }
}
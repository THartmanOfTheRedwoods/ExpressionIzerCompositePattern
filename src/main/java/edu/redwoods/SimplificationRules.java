package edu.redwoods;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimplificationRules {
    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // --- 1. CONSTANT FOLDING (The most basic reduction) ---
        RULES.add((op, l, r) -> {
            if (l instanceof Constant && r instanceof Constant) {
                double a = ((Constant) l).getValue();
                double b = ((Constant) r).getValue();
                switch (op) {
                    case ADD: return Optional.of(new Constant(a + b));
                    case SUB: return Optional.of(new Constant(a - b));
                    case MUL: return Optional.of(new Constant(a * b));
                    case DIV: return b != 0 ? Optional.of(new Constant(a / b)) : Optional.empty();
                    case POW: return Optional.of(new Constant(Math.pow(a, b)));
                }
            }
            return Optional.empty();
        });

        // --- 2. YOUR ORIGINAL IDENTITY RULES (Pruning the tree) ---
        RULES.add((op, l, r) -> {
            // Addition
            if (op == Operator.ADD) {
                if (l instanceof Constant && ((Constant) l).getValue() == 0) return Optional.of(r);
                if (r instanceof Constant && ((Constant) r).getValue() == 0) return Optional.of(l);
            }
            // Subtraction
            if (op == Operator.SUB) {
                if (r instanceof Constant && ((Constant) r).getValue() == 0) return Optional.of(l);
                if (l.equals(r)) return Optional.of(new Constant(0));
            }
            // Multiplication
            if (op == Operator.MUL) {
                if (l instanceof Constant && ((Constant) l).getValue() == 1) return Optional.of(r);
                if (r instanceof Constant && ((Constant) r).getValue() == 1) return Optional.of(l);
                if (l instanceof Constant && ((Constant) l).getValue() == 0) return Optional.of(new Constant(0));
                if (r instanceof Constant && ((Constant) r).getValue() == 0) return Optional.of(new Constant(0));
            }
            // Division (Restoring 0/0 -> 0 as requested for your tests)
            if (op == Operator.DIV) {
                if (l instanceof Constant && ((Constant) l).getValue() == 0) return Optional.of(new Constant(0));
                if (r instanceof Constant && ((Constant) r).getValue() == 1) return Optional.of(l);
                if (l.equals(r)) return Optional.of(new Constant(1));
            }
            // Powers
            if (op == Operator.POW) {
                if (r instanceof Constant && ((Constant) r).getValue() == 1) return Optional.of(l);
                if (r instanceof Constant && ((Constant) r).getValue() == 0) return Optional.of(new Constant(1));
            }
            return Optional.empty();
        });

        // --- 3. COMMUTATIVE SORTING (Ensures constants/like-terms meet) ---
        // Converts (x + 2) + 3 -> x + (2 + 3) so folding can find the constants
        RULES.add((op, l, r) -> {
            if (op == Operator.ADD || op == Operator.MUL) {
                if (l instanceof BinaryExpression && ((BinaryExpression) l).getOp() == op) {
                    BinaryExpression bl = (BinaryExpression) l;
                    if (bl.getRight() instanceof Constant && r instanceof Constant) {
                        return Optional.of(new BinaryExpression(op, bl.getLeft(),
                                new BinaryExpression(op, bl.getRight(), r)));
                    }
                }
            }
            return Optional.empty();
        });

        // --- 4. POWER RULES (x^a * x^b -> x^(a+b)) ---
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL && l instanceof BinaryExpression && r instanceof BinaryExpression) {
                BinaryExpression lp = (BinaryExpression) l;
                BinaryExpression rp = (BinaryExpression) r;
                if (lp.getOp() == Operator.POW && rp.getOp() == Operator.POW && lp.getLeft().equals(rp.getLeft())) {
                    return Optional.of(new BinaryExpression(Operator.POW, lp.getLeft(),
                            new BinaryExpression(Operator.ADD, lp.getRight(), rp.getRight())));
                }
            }
            return Optional.empty();
        });

        // --- 5. EXPANSION (Moves expression toward Sum of Products) ---
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL) {
                if (r instanceof BinaryExpression && ((BinaryExpression) r).getOp() == Operator.ADD) {
                    return Optional.of(new BinaryExpression(Operator.ADD,
                            new BinaryExpression(Operator.MUL, l, ((BinaryExpression) r).getLeft()),
                            new BinaryExpression(Operator.MUL, l, ((BinaryExpression) r).getRight())));
                }
                if (l instanceof BinaryExpression && ((BinaryExpression) l).getOp() == Operator.ADD) {
                    return Optional.of(new BinaryExpression(Operator.ADD,
                            new BinaryExpression(Operator.MUL, ((BinaryExpression) l).getLeft(), r),
                            new BinaryExpression(Operator.MUL, ((BinaryExpression) l).getRight(), r)));
                }
            }
            return Optional.empty();
        });

        // --- 6. COLLECTION (Combine Like Terms: 2x + x -> 3x) ---
        RULES.add((op, l, r) -> {
            if (op == Operator.ADD || op == Operator.SUB) {
                Expression baseL = PolynomialUtility.getBase(l);
                Expression baseR = PolynomialUtility.getBase(r);
                if (baseL.equals(baseR) && !(baseL instanceof Constant)) {
                    double cL = PolynomialUtility.getCoefficient(l);
                    double cR = PolynomialUtility.getCoefficient(r);
                    double res = (op == Operator.ADD) ? (cL + cR) : (cL - cR);
                    return Optional.of(new BinaryExpression(Operator.MUL, new Constant(res), baseL));
                }
            }
            return Optional.empty();
        });

        // --- 7. CANCELLATION (Factoring logic used ONLY for Division) ---
        RULES.add((op, l, r) -> {
            if (op == Operator.DIV) {
                return PolynomialUtility.getGCF(l, r).map(gcf -> {
                    // This is a simplified "Divide out the GCF" rule
                    // Normally you'd divide both sides by GCF, here we handle the simple case
                    if (l.equals(gcf) && r.equals(gcf)) return new Constant(1);
                    return null; // Logic for partial division would go here
                });
            }
            return Optional.empty();
        });
    }

    public static List<Rule> getRules() { return RULES; }
}
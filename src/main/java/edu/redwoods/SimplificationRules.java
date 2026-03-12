package edu.redwoods;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimplificationRules {
    private static final List<Rule> RULES = new ArrayList<>();

    // Helpers rewritten for Java 11 (explicit casting)
    private static Expression getBase(Expression e) {
        if (e instanceof BinaryExpression) {
            BinaryExpression be = (BinaryExpression) e;
            if (be.getOp() == Operator.POW) {
                return be.getLeft();
            }
        }
        return e;
    }

    private static Expression getExp(Expression e) {
        if (e instanceof BinaryExpression) {
            BinaryExpression be = (BinaryExpression) e;
            if (be.getOp() == Operator.POW) {
                return be.getRight();
            }
        }
        return new Constant(1);
    }

    static {
        // 1. CONSTANT FOLDING
        RULES.add((op, l, r) -> {
            if (l instanceof Constant && r instanceof Constant) {
                Constant lc = (Constant) l;
                Constant rc = (Constant) r;
                double a = lc.getValue();
                double b = rc.getValue();
                switch (op) {
                    case ADD: return Optional.of(new Constant(a + b));
                    case SUB: return Optional.of(new Constant(a - b));
                    case MUL: return Optional.of(new Constant(a * b));
                    case DIV: if (b != 0) return Optional.of(new Constant(a / b)); break;
                    case POW: return Optional.of(new Constant(Math.pow(a, b)));
                }
            }
            return Optional.empty();
        });

        // 2. IDENTITIES AND ZEROES
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
                if (l instanceof Constant) {
                    Constant cl = (Constant) l;
                    if (cl.getValue() == 0) return Optional.of(new Constant(0));
                    if (cl.getValue() == 1) return Optional.of(r);
                }
                if (r instanceof Constant) {
                    Constant cr = (Constant) r;
                    if (cr.getValue() == 0) return Optional.of(new Constant(0));
                    if (cr.getValue() == 1) return Optional.of(l);
                }
            }
            return Optional.empty();
        });

        // 3. DISTRIBUTION (MUL over ADD/SUB)
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL) {
                // a * (b +/- c)
                if (r instanceof BinaryExpression) {
                    BinaryExpression br = (BinaryExpression) r;
                    if (br.getOp() == Operator.ADD || br.getOp() == Operator.SUB) {
                        return Optional.of(new BinaryExpression(br.getOp(),
                                new BinaryExpression(Operator.MUL, l, br.getLeft()),
                                new BinaryExpression(Operator.MUL, l, br.getRight())));
                    }
                }
                // (a +/- b) * c
                if (l instanceof BinaryExpression) {
                    BinaryExpression bl = (BinaryExpression) l;
                    if (bl.getOp() == Operator.ADD || bl.getOp() == Operator.SUB) {
                        return Optional.of(new BinaryExpression(bl.getOp(),
                                new BinaryExpression(Operator.MUL, bl.getLeft(), r),
                                new BinaryExpression(Operator.MUL, bl.getRight(), r)));
                    }
                }
            }
            return Optional.empty();
        });

        // 4. GENERALIZED POWER RULES (x^a * x^b -> x^(a+b))
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL) {
                Expression baseL = getBase(l);
                Expression baseR = getBase(r);
                if (baseL.equals(baseR)) {
                    return Optional.of(new BinaryExpression(Operator.POW, baseL,
                            new BinaryExpression(Operator.ADD, getExp(l), getExp(r))));
                }
            }
            // (x^a)^b -> x^(a*b)
            if (op == Operator.POW && l instanceof BinaryExpression) {
                BinaryExpression bl = (BinaryExpression) l;
                if (bl.getOp() == Operator.POW) {
                    return Optional.of(new BinaryExpression(Operator.POW, bl.getLeft(),
                            new BinaryExpression(Operator.MUL, bl.getRight(), r)));
                }
            }
            return Optional.empty();
        });

        // 5. CAS COLLECTION & CANCELLATION
        RULES.add((op, l, r) -> {
            if (op == Operator.SUB) {
                // (A + B) - A -> B
                if (l instanceof BinaryExpression) {
                    BinaryExpression bl = (BinaryExpression) l;
                    if (bl.getOp() == Operator.ADD) {
                        if (bl.getLeft().equals(r)) return Optional.of(bl.getRight());
                        if (bl.getRight().equals(r)) return Optional.of(bl.getLeft());
                    }
                }
                // A - (B + C) -> (A - B) - C
                if (r instanceof BinaryExpression) {
                    BinaryExpression br = (BinaryExpression) r;
                    if (br.getOp() == Operator.ADD) {
                        return Optional.of(new BinaryExpression(Operator.SUB,
                                new BinaryExpression(Operator.SUB, l, br.getLeft()), br.getRight()));
                    }
                }
            }
            if (op == Operator.ADD) {
                // (A - B) + B -> A
                if (l instanceof BinaryExpression) {
                    BinaryExpression bl = (BinaryExpression) l;
                    if (bl.getOp() == Operator.SUB && bl.getRight().equals(r)) {
                        return Optional.of(bl.getLeft());
                    }
                }
            }
            return Optional.empty();
        });
    }

    public static List<Rule> getRules() { return RULES; }
}
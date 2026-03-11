package edu.redwoods;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimplificationRules {
    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // 1. CONSTANT FOLDING (always safe and reduces size)
        RULES.add((op, l, r) -> {
            if (l instanceof Constant && r instanceof Constant) {
                double a = ((Constant) l).getValue();
                double b = ((Constant) r).getValue();
                switch (op) {
                    case ADD: return Optional.of(new Constant(a + b));
                    case SUB: return Optional.of(new Constant(a - b));
                    case MUL: return Optional.of(new Constant(a * b));
                    case DIV:
                        if (b != 0) return Optional.of(new Constant(a / b));
                        break;
                    case POW:
                        // Handle integer powers simply, but avoid complex cases
                        return Optional.of(new Constant(Math.pow(a, b)));
                }
            }
            return Optional.empty();
        });

        // 2. IDENTITY AND ZERO RULES (including new ones)

        // Addition: 0 + X -> X, X + 0 -> X  (already present, but included for completeness)
        RULES.add((op, l, r) -> {
            if (op == Operator.ADD) {
                if (l instanceof Constant && ((Constant) l).getValue() == 0) return Optional.of(r);
                if (r instanceof Constant && ((Constant) r).getValue() == 0) return Optional.of(l);
            }
            return Optional.empty();
        });

        // Subtraction: X - 0 -> X
        RULES.add((op, l, r) -> {
            if (op == Operator.SUB && r instanceof Constant && ((Constant) r).getValue() == 0)
                return Optional.of(l);
            return Optional.empty();
        });

        // Subtraction: X - X -> 0
        RULES.add((op, l, r) -> {
            if (op == Operator.SUB && l.equals(r))
                return Optional.of(new Constant(0));
            return Optional.empty();
        });

        // Multiplication: 0 * X -> 0, X * 0 -> 0
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL) {
                if (l instanceof Constant && ((Constant) l).getValue() == 0) return Optional.of(new Constant(0));
                if (r instanceof Constant && ((Constant) r).getValue() == 0) return Optional.of(new Constant(0));
            }
            return Optional.empty();
        });

        // Multiplication: 1 * X -> X, X * 1 -> X
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL) {
                if (l instanceof Constant && ((Constant) l).getValue() == 1) return Optional.of(r);
                if (r instanceof Constant && ((Constant) r).getValue() == 1) return Optional.of(l);
            }
            return Optional.empty();
        });

        // Multiplication: -1 * X -> (0 - X)  (represent as 0-X, which may simplify further)
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL) {
                if (l instanceof Constant && ((Constant) l).getValue() == -1)
                    return Optional.of(new BinaryExpression(Operator.SUB, new Constant(0), r));
                if (r instanceof Constant && ((Constant) r).getValue() == -1)
                    return Optional.of(new BinaryExpression(Operator.SUB, new Constant(0), l));
            }
            return Optional.empty();
        });

        // Division: X / 1 -> X
        RULES.add((op, l, r) -> {
            if (op == Operator.DIV && r instanceof Constant && ((Constant) r).getValue() == 1)
                return Optional.of(l);
            return Optional.empty();
        });

        // Division: 0 / X -> 0  (X not zero)
        RULES.add((op, l, r) -> {
            if (op == Operator.DIV && l instanceof Constant && ((Constant) l).getValue() == 0) {
                if (r instanceof Constant && ((Constant) r).getValue() == 0)
                    return Optional.empty(); // 0/0 remains
                return Optional.of(new Constant(0));
            }
            return Optional.empty();
        });

        // Division: X / X -> 1  (X not zero)
        RULES.add((op, l, r) -> {
            if (op == Operator.DIV && l.equals(r)) {
                if (l instanceof Constant && ((Constant) l).getValue() == 0)
                    return Optional.empty(); // 0/0 remains
                return Optional.of(new Constant(1));
            }
            return Optional.empty();
        });

        // 3. RULES INVOLVING NEGATIVES (using 0 - X pattern)
        // x - (0 - y) -> x + y
        RULES.add((op, l, r) -> {
            if (op == Operator.SUB && r instanceof BinaryExpression) {
                BinaryExpression br = (BinaryExpression) r;
                if (br.getOp() == Operator.SUB &&
                        br.getLeft() instanceof Constant && ((Constant) br.getLeft()).getValue() == 0) {
                    // r is (0 - y)
                    return Optional.of(new BinaryExpression(Operator.ADD, l, br.getRight()));
                }
            }
            return Optional.empty();
        });

        // (0 - x) * y -> 0 - (x * y)   (i.e., -(x*y))
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL) {
                // left is (0 - x)
                if (l instanceof BinaryExpression && ((BinaryExpression) l).getOp() == Operator.SUB) {
                    BinaryExpression bl = (BinaryExpression) l;
                    if (bl.getLeft() instanceof Constant && ((Constant) bl.getLeft()).getValue() == 0) {
                        // (0 - x) * r  ->  0 - (x * r)
                        Expression x = bl.getRight();
                        return Optional.of(new BinaryExpression(Operator.SUB, new Constant(0),
                                new BinaryExpression(Operator.MUL, x, r)));
                    }
                }
                // right is (0 - y)
                if (r instanceof BinaryExpression && ((BinaryExpression) r).getOp() == Operator.SUB) {
                    BinaryExpression br = (BinaryExpression) r;
                    if (br.getLeft() instanceof Constant && ((Constant) br.getLeft()).getValue() == 0) {
                        // l * (0 - y) -> 0 - (l * y)
                        Expression y = br.getRight();
                        return Optional.of(new BinaryExpression(Operator.SUB, new Constant(0),
                                new BinaryExpression(Operator.MUL, l, y)));
                    }
                }
            }
            return Optional.empty();
        });

        // 4. POWER RULES (requires POW operator)
        // x^0 -> 1 (if x != 0)
        RULES.add((op, l, r) -> {
            if (op == Operator.POW && r instanceof Constant && ((Constant) r).getValue() == 0) {
                if (l instanceof Constant && ((Constant) l).getValue() == 0)
                    return Optional.empty(); // 0^0 undefined
                return Optional.of(new Constant(1));
            }
            return Optional.empty();
        });

        // x^1 -> x
        RULES.add((op, l, r) -> {
            if (op == Operator.POW && r instanceof Constant && ((Constant) r).getValue() == 1)
                return Optional.of(l);
            return Optional.empty();
        });

        // 1^x -> 1
        RULES.add((op, l, r) -> {
            if (op == Operator.POW && l instanceof Constant && ((Constant) l).getValue() == 1)
                return Optional.of(new Constant(1));
            return Optional.empty();
        });

        // 0^x -> 0 (if x > 0, but we can't easily check positivity; assume integer constant)
        RULES.add((op, l, r) -> {
            if (op == Operator.POW && l instanceof Constant && ((Constant) l).getValue() == 0) {
                if (r instanceof Constant) {
                    double exp = ((Constant) r).getValue();
                    if (exp > 0) return Optional.of(new Constant(0));
                }
            }
            return Optional.empty();
        });

        // (a^b) * (a^c) -> a^(b+c)  (requires same base)
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL) {
                // left is a^b, right is a^c
                if (l instanceof BinaryExpression && ((BinaryExpression) l).getOp() == Operator.POW &&
                        r instanceof BinaryExpression && ((BinaryExpression) r).getOp() == Operator.POW) {
                    BinaryExpression leftPow = (BinaryExpression) l;
                    BinaryExpression rightPow = (BinaryExpression) r;
                    if (leftPow.getLeft().equals(rightPow.getLeft())) {
                        Expression base = leftPow.getLeft();
                        Expression exp1 = leftPow.getRight();
                        Expression exp2 = rightPow.getRight();
                        // Combine exponents: a^(b+c)
                        return Optional.of(new BinaryExpression(Operator.POW, base,
                                new BinaryExpression(Operator.ADD, exp1, exp2)));
                    }
                }
            }
            return Optional.empty();
        });

        // (a^b)^c -> a^(b*c)
        RULES.add((op, l, r) -> {
            if (op == Operator.POW && l instanceof BinaryExpression && ((BinaryExpression) l).getOp() == Operator.POW) {
                BinaryExpression innerPow = (BinaryExpression) l;
                Expression base = innerPow.getLeft();
                Expression innerExp = innerPow.getRight();
                // (base^innerExp)^r  -> base^(innerExp * r)
                return Optional.of(new BinaryExpression(Operator.POW, base,
                        new BinaryExpression(Operator.MUL, innerExp, r)));
            }
            return Optional.empty();
        });

        // 5. DISTRIBUTION (a * (b + c) -> a*b + a*c) with growth detection
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL) {
                // Case: l * (r1 + r2)
                if (r instanceof BinaryExpression && ((BinaryExpression) r).getOp() == Operator.ADD) {
                    BinaryExpression add = (BinaryExpression) r;
                    Expression a = l;
                    Expression b = add.getLeft();
                    Expression c = add.getRight();
                    Expression expanded = new BinaryExpression(Operator.ADD,
                            new BinaryExpression(Operator.MUL, a, b),
                            new BinaryExpression(Operator.MUL, a, c));
                    // Check growth: only apply if the expanded+simplified size <= original size
                    // We need to temporarily simplify the expanded expression to see net effect.
                    Expression simplifiedExpanded = expanded.simplify(); // recursive simplify
                    int originalSize = new BinaryExpression(Operator.MUL, l, r).size();
                    int newSize = simplifiedExpanded.size();
                    if (newSize <= originalSize) {
                        return Optional.of(simplifiedExpanded);
                    }
                }
                // Case: (a + b) * c
                if (l instanceof BinaryExpression && ((BinaryExpression) l).getOp() == Operator.ADD) {
                    BinaryExpression add = (BinaryExpression) l;
                    Expression a = add.getLeft();
                    Expression b = add.getRight();
                    Expression c = r;
                    Expression expanded = new BinaryExpression(Operator.ADD,
                            new BinaryExpression(Operator.MUL, a, c),
                            new BinaryExpression(Operator.MUL, b, c));
                    Expression simplifiedExpanded = expanded.simplify();
                    int originalSize = new BinaryExpression(Operator.MUL, l, r).size();
                    int newSize = simplifiedExpanded.size();
                    if (newSize <= originalSize) {
                        return Optional.of(simplifiedExpanded);
                    }
                }
            }
            return Optional.empty();
        });

        // 6. COMMUTATIVE REORDERING to bring constants together (enables constant folding)
        // For addition: (X + C1) + C2  ->  X + (C1 + C2)  (where C1, C2 are constants)
        RULES.add((op, l, r) -> {
            if (op == Operator.ADD) {
                // pattern: (X + C1) + C2
                if (l instanceof BinaryExpression && ((BinaryExpression) l).getOp() == Operator.ADD) {
                    BinaryExpression leftAdd = (BinaryExpression) l;
                    Expression leftLeft = leftAdd.getLeft();
                    Expression leftRight = leftAdd.getRight();
                    if (leftRight instanceof Constant && r instanceof Constant) {
                        // leftRight = C1, r = C2
                        double c1 = ((Constant) leftRight).getValue();
                        double c2 = ((Constant) r).getValue();
                        // return X + (C1 + C2)
                        return Optional.of(new BinaryExpression(Operator.ADD, leftLeft,
                                new Constant(c1 + c2)));
                    }
                }
                // similarly, C1 + (C2 + X)  etc. can be added, but this demonstrates the idea.
            }
            return Optional.empty();
        });

        // For multiplication: (X * C1) * C2  ->  X * (C1 * C2)
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL) {
                if (l instanceof BinaryExpression && ((BinaryExpression) l).getOp() == Operator.MUL) {
                    BinaryExpression leftMul = (BinaryExpression) l;
                    Expression leftLeft = leftMul.getLeft();
                    Expression leftRight = leftMul.getRight();
                    if (leftRight instanceof Constant && r instanceof Constant) {
                        double c1 = ((Constant) leftRight).getValue();
                        double c2 = ((Constant) r).getValue();
                        return Optional.of(new BinaryExpression(Operator.MUL, leftLeft,
                                new Constant(c1 * c2)));
                    }
                }
            }
            return Optional.empty();
        });
    }

    public static List<Rule> getRules() {
        return RULES;
    }
}
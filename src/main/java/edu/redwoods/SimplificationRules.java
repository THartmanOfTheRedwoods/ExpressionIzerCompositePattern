package edu.redwoods;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimplificationRules {
    private static final List<Rule> RULES = new ArrayList<>();

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    /** Returns the base of a POW expression, or the expression itself. */
    private static Expression getBase(Expression e) {
        if (e instanceof BinaryExpression) {
            BinaryExpression be = (BinaryExpression) e;
            if (be.getOp() == Operator.POW) return be.getLeft();
        }
        return e;
    }

    /** Returns the exponent of a POW expression, or 1 if not a POW. */
    private static Expression getExp(Expression e) {
        if (e instanceof BinaryExpression) {
            BinaryExpression be = (BinaryExpression) e;
            if (be.getOp() == Operator.POW) return be.getRight();
        }
        return new Constant(1);
    }

    /**
     * Strips a leading numeric coefficient from a product, returning the
     * symbolic (non-constant) factor.  Used by the coefficient-aware MUL rule.
     * Examples:  3*x → x | x*3 → x | x^2 → x^2 | x → x
     */
    private static double extractCoeff(Expression e) {
        if (e instanceof BinaryExpression) {
            BinaryExpression be = (BinaryExpression) e;
            if (be.getOp() == Operator.MUL) {
                if (be.getLeft()  instanceof Constant) return ((Constant) be.getLeft()).getValue();
                if (be.getRight() instanceof Constant) return ((Constant) be.getRight()).getValue();
            }
        }
        return 1.0;
    }

    /** Companion to extractCoeff — returns the non-constant factor (or e itself). */
    private static Expression extractSymbol(Expression e) {
        if (e instanceof BinaryExpression) {
            BinaryExpression be = (BinaryExpression) e;
            if (be.getOp() == Operator.MUL) {
                if (be.getLeft()  instanceof Constant) return be.getRight();
                if (be.getRight() instanceof Constant) return be.getLeft();
            }
        }
        return e;
    }

    /**
     * Extracts the numeric coefficient from a term.
     * Examples:  3*x -> 3.0 | x*3 -> 3.0 | x -> 1.0 | x^2 -> 1.0
     */
    private static double getCoefficient(Expression e) {
        if (e instanceof Constant) return ((Constant) e).getValue();
        if (e instanceof BinaryExpression) {
            BinaryExpression be = (BinaryExpression) e;
            if (be.getOp() == Operator.MUL) {
                if (be.getLeft()  instanceof Constant) return ((Constant) be.getLeft()).getValue();
                if (be.getRight() instanceof Constant) return ((Constant) be.getRight()).getValue();
            }
        }
        return 1.0;
    }

    /**
     * Extracts the symbolic (non-constant) part of a term for like-term matching.
     * Examples:  3*x -> x | x*3 -> x | x -> x | x^2 -> x^2
     * Returns null for pure constants (no variable part to merge on).
     */
    private static Expression getTermBase(Expression e) {
        if (e instanceof Constant) return null;
        if (e instanceof BinaryExpression) {
            BinaryExpression be = (BinaryExpression) e;
            if (be.getOp() == Operator.MUL) {
                if (be.getLeft()  instanceof Constant) return be.getRight();
                if (be.getRight() instanceof Constant) return be.getLeft();
            }
        }
        return e; // Variable, x^n, etc. — the whole expression is the base
    }

    // -----------------------------------------------------------------------
    //  Rules (applied in order inside BinaryExpression.simplify)
    // -----------------------------------------------------------------------
    static {

        // ===================================================================
        // RULE 1: CONSTANT FOLDING
        //   Evaluate any binary operation on two numeric constants.
        // ===================================================================
        RULES.add((op, l, r) -> {
            if (l instanceof Constant && r instanceof Constant) {
                double a = ((Constant) l).getValue();
                double b = ((Constant) r).getValue();
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

        // ===================================================================
        // RULE 2: IDENTITIES AND ZEROES
        //   Standard algebraic identity reductions.
        //   ADDED vs original: x^0->1, x^1->x, 1^x->1, x/x->1, x/1->x
        // ===================================================================
        RULES.add((op, l, r) -> {
            // --- Addition ---
            if (op == Operator.ADD) {
                if (l instanceof Constant && ((Constant) l).getValue() == 0) return Optional.of(r);
                if (r instanceof Constant && ((Constant) r).getValue() == 0) return Optional.of(l);
            }
            // --- Subtraction ---
            if (op == Operator.SUB) {
                if (r instanceof Constant && ((Constant) r).getValue() == 0) return Optional.of(l);
                if (l.equals(r)) return Optional.of(new Constant(0));
            }
            // --- Multiplication ---
            if (op == Operator.MUL) {
                if (l instanceof Constant) {
                    double v = ((Constant) l).getValue();
                    if (v == 0) return Optional.of(new Constant(0));
                    if (v == 1) return Optional.of(r);
                }
                if (r instanceof Constant) {
                    double v = ((Constant) r).getValue();
                    if (v == 0) return Optional.of(new Constant(0));
                    if (v == 1) return Optional.of(l);
                }
            }
            // --- Division ---
            if (op == Operator.DIV) {
                // 0 / x = 0  (guard against 0/0)
                if (l instanceof Constant && ((Constant) l).getValue() == 0 && !l.equals(r))
                    return Optional.of(new Constant(0));
                // x / 1 = x
                if (r instanceof Constant && ((Constant) r).getValue() == 1)
                    return Optional.of(l);
                // x / x = 1  (guard against division by zero)
                if (l.equals(r) && !(r instanceof Constant && ((Constant) r).getValue() == 0))
                    return Optional.of(new Constant(1));
            }
            // --- Power ---
            if (op == Operator.POW) {
                if (r instanceof Constant) {
                    double exp = ((Constant) r).getValue();
                    if (exp == 0) return Optional.of(new Constant(1));   // x^0 = 1
                    if (exp == 1) return Optional.of(l);                 // x^1 = x
                }
                // 1^x = 1
                if (l instanceof Constant && ((Constant) l).getValue() == 1)
                    return Optional.of(new Constant(1));
            }
            return Optional.empty();
        });

        // ===================================================================
        // RULE 3: DISTRIBUTION  (expand first, cancel later)
        //   a * (b ± c)  ->  a*b ± a*c
        //   (a ± b) * c  ->  a*c ± b*c
        // ===================================================================
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL) {
                // a * (b ± c)
                if (r instanceof BinaryExpression) {
                    BinaryExpression br = (BinaryExpression) r;
                    if (br.getOp() == Operator.ADD || br.getOp() == Operator.SUB) {
                        return Optional.of(new BinaryExpression(br.getOp(),
                                new BinaryExpression(Operator.MUL, l, br.getLeft()),
                                new BinaryExpression(Operator.MUL, l, br.getRight())));
                    }
                }
                // (a ± b) * c
                if (l instanceof BinaryExpression) {
                    BinaryExpression bl = (BinaryExpression) l;
                    if (bl.getOp() == Operator.ADD || bl.getOp() == Operator.SUB) {
                        return Optional.of(new BinaryExpression(bl.getOp(),
                                new BinaryExpression(Operator.MUL, bl.getLeft(),  r),
                                new BinaryExpression(Operator.MUL, bl.getRight(), r)));
                    }
                }
            }
            return Optional.empty();
        });

        // ===================================================================
        // RULE 4: POWER RULES  (coefficient-aware)
        //
        // ORIGINAL PROBLEM:
        //   getBase(2*x) returns (2*x) - the MUL wrapper hides the shared x.
        //   getBase(3*x) returns (3*x) - likewise.
        //   So (2*x)*(3*x) was never recognized as same-base, producing the
        //   stuck node that caused (2x+1)(3x-1) to not fully simplify.
        //
        // FIX: strip any leading numeric coefficient first with extractCoeff /
        //   extractSymbol, then compare the symbolic part's base via getBase.
        //   This generalises the original x*x case (coefficients are both 1)
        //   and correctly handles (c1*x^a)*(c2*x^b) -> (c1·c2)*x^(a+b).
        //
        // Guard: powBaseL must not be a Constant - Rule 1 owns constant*constant.
        // ===================================================================
        RULES.add((op, l, r) -> {
            if (op == Operator.MUL) {
                double   cL    = extractCoeff(l);   Expression symL = extractSymbol(l);
                double   cR    = extractCoeff(r);   Expression symR = extractSymbol(r);
                Expression powBaseL = getBase(symL);
                Expression powBaseR = getBase(symR);
                if (powBaseL.equals(powBaseR) && !(powBaseL instanceof Constant)) {
                    double     newCoeff = cL * cR;
                    Expression newExp   = new BinaryExpression(Operator.ADD, getExp(symL), getExp(symR));
                    Expression powered  = new BinaryExpression(Operator.POW, powBaseL, newExp);
                    Expression result   = (newCoeff == 1.0)
                            ? powered
                            : new BinaryExpression(Operator.MUL, new Constant(newCoeff), powered);
                    return Optional.of(result);
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

        // ===================================================================
        // RULE 5: ASSOCIATIVITY NORMALIZATION  <- KEY BUG FIX
        //
        // WHY THIS IS NEEDED:
        //   Distribution is order-sensitive.  When we simplify (x-1)*(x+1):
        //     Rule 3 sees r=(x+1) is ADD  ->  expands to  (x-1)*x + (x-1)*1
        //     After inner simplification   ->  (x²-x) + (x-1)
        //   My previous cancellation rule knew "(A-B)+B -> A", but here B=x
        //   and the right operand is "(x-1)", not "x" - so it can't match.
        //
        //   Converting to left-associative form first brings the cancellable
        //   pair "(x²-x) + x" into adjacent position where Rule 7 fires:
        //     (x²-x) + (x-1)  ->  ((x²-x) + x) - 1  ->  x² - 1
        //
        // PATTERNS:
        //   A + (B - C)  ->  (A + B) - C
        //   A - (B - C)  ->  (A - B) + C   [distributes minus over subtraction]
        //
        // LOOP SAFETY:
        //   Both rewrites change the root operator (ADD->SUB or SUB->ADD), so
        //   the same rule cannot immediately re-fire on its own output.
        // ===================================================================
        RULES.add((op, l, r) -> {
            if (r instanceof BinaryExpression) {
                BinaryExpression br = (BinaryExpression) r;
                // A + (B - C)  ->  (A + B) - C
                if (op == Operator.ADD && br.getOp() == Operator.SUB) {
                    return Optional.of(new BinaryExpression(Operator.SUB,
                            new BinaryExpression(Operator.ADD, l, br.getLeft()),
                            br.getRight()));
                }
                // A - (B - C)  ->  (A - B) + C
                if (op == Operator.SUB && br.getOp() == Operator.SUB) {
                    return Optional.of(new BinaryExpression(Operator.ADD,
                            new BinaryExpression(Operator.SUB, l, br.getLeft()),
                            br.getRight()));
                }
            }
            return Optional.empty();
        });

        // ===================================================================
        // RULE 5.5: LIKE-TERM EXTRACTION FROM OUTER SUBTRACTION
        //
        // ORIGINAL PROBLEM:
        //   After Rule 4★ reduces (2x)*(3x) to 6x², distribution leaves:
        //     (6x² + 3x) - 2x
        //   Rule 7 checks whether 3x or 6x² equals 2x exactly — they don't.
        //   Rule 6 checks the top-level operands (6x²+3x) and 2x — the left
        //   is a compound ADD, not a bare term, so no match.
        //   The like-term pair (3x, 2x) is stuck one level too deep.
        //
        // FIX: Pattern  (A + B) - C  where termBase(B) == termBase(C)
        //      Rewrite:  A + (B - C)
        //   This pulls B and C together so Rule 6 can merge them during
        //   child-simplification of the new ADD node.
        //
        // LOOP-SAFETY PROOF (why this cannot cycle with Rule 5):
        //   • We return  ADD(A, SUB(B, C)).
        //   • BinaryExpression.simplify() recurses on children first (post-order).
        //   • SUB(B, C) is simplified by Rule 6 → single merged term T.
        //   • The outer ADD now sees ADD(A, T) where T is NOT a SUB node.
        //   • Rule 5 requires the right operand to be a SUB — it cannot fire.
        //   • A cycle is structurally impossible once Rule 6 has consumed (B-C).
        // ===================================================================
        RULES.add((op, l, r) -> {
            if (op == Operator.SUB && l instanceof BinaryExpression) {
                BinaryExpression bl = (BinaryExpression) l;
                if (bl.getOp() == Operator.ADD) {
                    Expression B = bl.getRight();
                    Expression tbB = getTermBase(B);
                    Expression tbC = getTermBase(r);
                    if (tbB != null && tbB.equals(tbC)) {
                        return Optional.of(new BinaryExpression(Operator.ADD,
                                bl.getLeft(),
                                new BinaryExpression(Operator.SUB, B, r)));
                    }
                }
            }
            return Optional.empty();
        });

        // ===================================================================
        // RULE 5.6: LIKE-TERM EXTRACTION FROM OUTER ADDITION  ← NEW
        //
        // PROBLEM THIS SOLVES:
        //   Distribution of (3x-1)*(2x+1) produces, after child-simplification:
        //     (6x² - 2x) + 3x
        //   Rule 5   requires right operand to be a SUB  — right=3x, miss.
        //   Rule 5.5 requires op to be SUB with ADD left  — op=ADD here, miss.
        //   Rule 6   requires both top-level operands to be bare terms
        //              — left=(6x²-2x) is a compound SUB, termBase=null, miss.
        //   The like-term pair (2x inside the SUB, and 3x on the right) is
        //   one level too deep for any existing rule to reach.
        //
        // FIX: Pattern  (A - B) + C  where termBase(B) == termBase(C)
        //      Rewrite:  A + (C - B)
        //   This is the exact mirror of Rule 5.5, handling SUB-left under ADD
        //   instead of ADD-left under SUB.
        //   (6x²-2x)+3x  →  6x²+(3x-2x)  →  Rule 6  →  6x²+x
        //
        // LOOP-SAFETY PROOF:
        //   • We return ADD(A, SUB(C, B)).
        //   • BinaryExpression.simplify() recurses on children first (post-order).
        //   • SUB(C, B) is simplified by Rule 6 → single merged term T.
        //   • The outer ADD now sees ADD(A, T) where T is NOT a SUB node.
        //   • Rule 5 requires right to be SUB  — T is not, so it cannot fire.
        //   • Rule 5.6 requires left to be SUB  — left=A is not (it's the quadratic
        //     term), so it cannot fire again either.
        //   • A cycle is structurally impossible once Rule 6 has consumed (C-B).
        // ===================================================================
        RULES.add((op, l, r) -> {
            if (op == Operator.ADD && l instanceof BinaryExpression) {
                BinaryExpression bl = (BinaryExpression) l;
                if (bl.getOp() == Operator.SUB) {
                    Expression B   = bl.getRight();
                    Expression tbB = getTermBase(B);
                    Expression tbC = getTermBase(r);
                    if (tbB != null && tbB.equals(tbC)) {
                        return Optional.of(new BinaryExpression(Operator.ADD,
                                bl.getLeft(),
                                new BinaryExpression(Operator.SUB, r, B)));
                    }
                }
            }
            return Optional.empty();
        });

        //   Recognizes terms with the same symbolic base and merges their
        //   numeric coefficients:
        //     n*x ± m*x  ->  (n±m)*x
        //     x   ± m*x  ->  (1±m)*x
        //
        //   This handles cases such as  2x + 3x -> 5x  and  x - x -> 0
        //   that constant-folding and cancellation alone cannot reach.
        // ===================================================================
        RULES.add((op, l, r) -> {
            if (op == Operator.ADD || op == Operator.SUB) {
                Expression baseL = getTermBase(l);
                Expression baseR = getTermBase(r);
                // Both sides must have a symbolic base, which must be the same
                if (baseL != null && baseR != null && baseL.equals(baseR)) {
                    double cL = getCoefficient(l);
                    double cR = getCoefficient(r);
                    double newCoeff = (op == Operator.ADD) ? cL + cR : cL - cR;
                    if (newCoeff == 0)  return Optional.of(new Constant(0));
                    if (newCoeff == 1)  return Optional.of(baseL);
                    if (newCoeff == -1) return Optional.of(
                            new BinaryExpression(Operator.SUB, new Constant(0), baseL));
                    return Optional.of(new BinaryExpression(Operator.MUL,
                            new Constant(newCoeff), baseL));
                }
            }
            return Optional.empty();
        });

        // ===================================================================
        // RULE 7: CAS COLLECTION & CANCELLATION
        //   Direct term cancellation, with left-association of ADD chains.
        //   ADDED vs original:
        //     (A - B) - A  ->  0 - B   (left-side cancel with SUB left)
        //     B + (A - B)  ->  A       (commutativity variant)
        // ===================================================================
        RULES.add((op, l, r) -> {
            if (op == Operator.SUB) {
                if (l instanceof BinaryExpression) {
                    BinaryExpression bl = (BinaryExpression) l;
                    // (A + B) - A  ->  B
                    // (A + B) - B  ->  A
                    if (bl.getOp() == Operator.ADD) {
                        if (bl.getLeft().equals(r))  return Optional.of(bl.getRight());
                        if (bl.getRight().equals(r)) return Optional.of(bl.getLeft());
                    }
                    // (A - B) - A  ->  0 - B  [ADDED]
                    if (bl.getOp() == Operator.SUB && bl.getLeft().equals(r)) {
                        return Optional.of(new BinaryExpression(Operator.SUB,
                                new Constant(0), bl.getRight()));
                    }
                }
                // A - (B + C)  ->  (A - B) - C   [left-associate; enables further cancellation]
                if (r instanceof BinaryExpression) {
                    BinaryExpression br = (BinaryExpression) r;
                    if (br.getOp() == Operator.ADD) {
                        return Optional.of(new BinaryExpression(Operator.SUB,
                                new BinaryExpression(Operator.SUB, l, br.getLeft()),
                                br.getRight()));
                    }
                }
            }

            if (op == Operator.ADD) {
                if (l instanceof BinaryExpression) {
                    BinaryExpression bl = (BinaryExpression) l;
                    // (A - B) + B  ->  A
                    if (bl.getOp() == Operator.SUB && bl.getRight().equals(r)) {
                        return Optional.of(bl.getLeft());
                    }
                }
                // B + (A - B)  ->  A   [commutativity variant; ADDED]
                if (r instanceof BinaryExpression) {
                    BinaryExpression br = (BinaryExpression) r;
                    if (br.getOp() == Operator.SUB && br.getRight().equals(l)) {
                        return Optional.of(br.getLeft());
                    }
                }
            }
            return Optional.empty();
        });
    }

    public static List<Rule> getRules() { return RULES; }
}
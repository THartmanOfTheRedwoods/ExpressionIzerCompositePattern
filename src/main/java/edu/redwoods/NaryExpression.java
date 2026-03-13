package edu.redwoods;

import java.util.*;

/**
 * N-ary Composite Expression for the two commutative, associative operators:
 * ADD and MUL.
 *
 * COMPOSITE PATTERN ROLE
 *   NaryExpression is the Composite node.  Constant and Variable are Leaves.
 *   BinaryExpression is retained only for POW and DIV, which are neither
 *   commutative nor associative.  SUB is handled by converting it to
 *   ADD + negation during simplification (see BinaryExpression.simplify()).
 *
 * WHY N-ARY BEATS BINARY FOR ADD / MUL
 *   A binary tree encodes left/right child order, which is a parsing artifact —
 *   not a mathematical property of addition or multiplication.  That hidden
 *   order forced the previous design to add structural-normalisation rules
 *   (5, 5.5, 5.6) to move like terms into adjacent positions before cancelling.
 *   An N-ary node holds all operands in a flat list and processes them in a
 *   single pass, making those rules unnecessary.
 *
 * simplify() ALGORITHM
 *   ADD:
 *     1. Flatten  — pull nested ADD children up (associativity)
 *     2. Absorb   — convert any BinaryExpression(SUB, a, b) child to [a, −b]
 *     3. Group    — build a Map<canonicalBase, coefficientSum> over all terms
 *     4. Sort     — higher-degree terms first (x² before x before constant)
 *     5. Emit     — drop zero-coefficient entries, return single term if only one
 *   MUL:
 *     1. Flatten  — pull nested MUL children up (associativity)
 *     2. Zero     — any Constant(0) factor ⟹ return Constant(0)
 *     3. Fold     — multiply all Constant children into one scalar
 *     4. Distribute — if any factor is an ADD, FOIL-expand and re-simplify
 *     5. Powers   — group same-base factors, sum exponents  (x²·x³ → x⁵)
 *     6. Sort     — canonical factor order (commutativity: x·y == y·x)
 *     7. Emit     — return single factor if only one remains
 */
public class NaryExpression implements Expression {

    private final Operator       op;       // ADD or MUL only
    private final List<Expression> operands; // flat, immutable

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public NaryExpression(Operator op, List<Expression> operands) {
        if (op != Operator.ADD && op != Operator.MUL)
            throw new IllegalArgumentException(
                    "NaryExpression only supports ADD and MUL; got: " + op);
        this.op       = op;
        this.operands = Collections.unmodifiableList(new ArrayList<>(operands));
    }

    /** Varargs convenience constructor — e.g. new NaryExpression(ADD, a, b, c). */
    public NaryExpression(Operator op, Expression... operands) {
        this(op, Arrays.asList(operands));
    }

    public Operator            getOp()       { return op; }
    public List<Expression>    getOperands() { return operands; }

    // -----------------------------------------------------------------------
    //  Composite Pattern: simplify()
    // -----------------------------------------------------------------------

    @Override
    public Expression simplify() {
        // Post-order: simplify every child before simplifying this node.
        List<Expression> simplified = new ArrayList<>(operands.size());
        for (Expression e : operands) simplified.add(e.simplify());
        return (op == Operator.ADD) ? simplifyAdd(simplified)
                : simplifyMul(simplified);
    }

    // =======================================================================
    //  ADD  simplification
    // =======================================================================

    private static Expression simplifyAdd(List<Expression> rawTerms) {
        // 1 & 2: flatten nested ADDs and absorb SUBs into a single flat list
        List<Expression> flat = new ArrayList<>();
        for (Expression t : rawTerms) collectAddTerms(t, flat);

        // 3: group by canonical symbolic base → sum coefficients
        double constantSum = 0.0;
        // LinkedHashMap preserves first-encounter order for deterministic output
        Map<String, Object[]> termMap = new LinkedHashMap<>();

        for (Expression t : flat) {
            if (t instanceof Constant) {
                constantSum += ((Constant) t).getValue();
            } else {
                double    coeff = extractCoeff(t);
                Expression base = extractBase(t);
                String    key   = canonKey(base);   // commutativity-safe key
                if (termMap.containsKey(key)) {
                    Object[] entry = termMap.get(key);
                    entry[1] = (double) entry[1] + coeff;
                } else {
                    termMap.put(key, new Object[]{base, coeff});
                }
            }
        }

        // 4 & 5: sort by descending degree and reconstruct
        List<Object[]> entries = new ArrayList<>(termMap.values());
        entries.sort((a, b) ->
                termDegree((Expression) b[0]) - termDegree((Expression) a[0]));

        List<Expression> result = new ArrayList<>();
        for (Object[] entry : entries) {
            double     c    = (double) entry[1];
            Expression base = (Expression) entry[0];
            if (c == 0.0) continue;          // term cancelled → discard
            result.add(buildTerm(c, base));
        }
        if (constantSum != 0.0) result.add(new Constant(constantSum));

        if (result.isEmpty())    return new Constant(0);
        if (result.size() == 1)  return result.get(0);
        return new NaryExpression(Operator.ADD, result);
    }

    /**
     * Recursively flattens nested ADD children and converts SUB(a,b) into
     * the pair [a, negate(b)], appending all resulting primitive terms to out.
     */
    private static void collectAddTerms(Expression e, List<Expression> out) {
        if (e instanceof NaryExpression) {
            NaryExpression ne = (NaryExpression) e;
            if (ne.op == Operator.ADD) {
                for (Expression child : ne.operands) collectAddTerms(child, out);
                return;
            }
        }
        if (e instanceof BinaryExpression) {
            BinaryExpression be = (BinaryExpression) e;
            if (be.getOp() == Operator.SUB) {
                collectAddTerms(be.getLeft(), out);
                out.add(negate(be.getRight()));
                return;
            }
        }
        out.add(e);
    }

    // =======================================================================
    //  MUL  simplification
    // =======================================================================

    private static Expression simplifyMul(List<Expression> rawFactors) {
        // 1: flatten nested MUL children
        List<Expression> flat = new ArrayList<>();
        for (Expression f : rawFactors) collectMulFactors(f, flat);

        // 2: zero check
        for (Expression f : flat)
            if (f instanceof Constant && ((Constant) f).getValue() == 0.0)
                return new Constant(0);

        // 3: fold all Constant children into one scalar
        double constProduct = 1.0;
        List<Expression> nonConst = new ArrayList<>();
        for (Expression f : flat) {
            if (f instanceof Constant) constProduct *= ((Constant) f).getValue();
            else                       nonConst.add(f);
        }
        if (constProduct == 0.0) return new Constant(0);

        // 4: distribute over any ADD children
        List<Expression> addFactors  = new ArrayList<>();
        List<Expression> pureFactors = new ArrayList<>();
        for (Expression f : nonConst) {
            if (isAddNode(f)) addFactors.add(f);
            else              pureFactors.add(f);
        }

        if (!addFactors.isEmpty()) {
            // FOIL-expand: cross-multiply all ADD factors
            List<Expression> terms = getAddOperands(addFactors.get(0));
            for (int i = 1; i < addFactors.size(); i++) {
                List<Expression> other   = getAddOperands(addFactors.get(i));
                List<Expression> crossed = new ArrayList<>();
                for (Expression a : terms)
                    for (Expression b : other)
                        crossed.add(new NaryExpression(Operator.MUL, a, b));
                terms = crossed;
            }
            // Attach scalar (constant · pureFactors) to each expanded term
            List<Expression> scalar = new ArrayList<>();
            if (constProduct != 1.0) scalar.add(new Constant(constProduct));
            scalar.addAll(pureFactors);
            if (!scalar.isEmpty()) {
                List<Expression> scaled = new ArrayList<>();
                for (Expression t : terms) {
                    List<Expression> f2 = new ArrayList<>(scalar);
                    f2.add(t);
                    scaled.add(new NaryExpression(Operator.MUL, f2));
                }
                terms = scaled;
            }
            // Re-simplify the expanded sum
            return new NaryExpression(Operator.ADD, terms).simplify();
        }

        // 5: group same-base factors, sum exponents  (x^a · x^b → x^(a+b))
        Map<String, Object[]> baseExp = new LinkedHashMap<>();
        for (Expression f : nonConst) {
            Expression base, exp;
            if (f instanceof BinaryExpression
                    && ((BinaryExpression) f).getOp() == Operator.POW) {
                BinaryExpression pow = (BinaryExpression) f;
                base = pow.getLeft();
                exp  = pow.getRight();
            } else {
                base = f;
                exp  = new Constant(1);
            }
            String key = repr(base);
            if (baseExp.containsKey(key)) {
                Object[] entry   = baseExp.get(key);
                Expression newExp =
                        new NaryExpression(Operator.ADD,
                                (Expression) entry[1], exp).simplify();
                entry[1] = newExp;
            } else {
                baseExp.put(key, new Object[]{base, exp});
            }
        }

        // 6 & 7: reconstruct — constant first, symbolic factors sorted canonically
        List<Expression> result = new ArrayList<>();
        if (constProduct != 1.0) result.add(new Constant(constProduct));

        List<Expression> symFactors = new ArrayList<>();
        for (Object[] entry : baseExp.values()) {
            Expression base = (Expression) entry[0];
            Expression exp  = (Expression) entry[1];
            if (isZero(exp)) continue;             // x^0 = 1, drop
            if (isOne(exp))  { symFactors.add(base); continue; }
            symFactors.add(new BinaryExpression(Operator.POW, base, exp).simplify());
        }
        // Canonical sort makes x*y == y*x in the ADD term-map key
        symFactors.sort(Comparator.comparing(NaryExpression::repr));
        result.addAll(symFactors);

        if (result.isEmpty())   return new Constant(1);
        if (result.size() == 1) return result.get(0);
        return new NaryExpression(Operator.MUL, result);
    }

    /** Flattens nested NaryExpression(MUL) children into out. */
    private static void collectMulFactors(Expression e, List<Expression> out) {
        if (e instanceof NaryExpression) {
            NaryExpression ne = (NaryExpression) e;
            if (ne.op == Operator.MUL) {
                for (Expression child : ne.operands) collectMulFactors(child, out);
                return;
            }
        }
        out.add(e);
    }

    // =======================================================================
    //  Helper utilities
    // =======================================================================

    /**
     * Extracts the leading numeric coefficient from a term.
     *   (3 * x)  → 3.0
     *   x        → 1.0
     *   (x ^ 2)  → 1.0
     */
    private static double extractCoeff(Expression e) {
        if (e instanceof NaryExpression) {
            NaryExpression ne = (NaryExpression) e;
            if (ne.op == Operator.MUL
                    && !ne.operands.isEmpty()
                    && ne.operands.get(0) instanceof Constant)
                return ((Constant) ne.operands.get(0)).getValue();
        }
        return 1.0;
    }

    /**
     * Strips the leading numeric coefficient, returning the symbolic base.
     *   (3 * x)     → x
     *   (3 * x * y) → (x * y)   [NaryExpression(MUL, [x, y])]
     *   x           → x
     */
    private static Expression extractBase(Expression e) {
        if (e instanceof NaryExpression) {
            NaryExpression ne = (NaryExpression) e;
            if (ne.op == Operator.MUL
                    && !ne.operands.isEmpty()
                    && ne.operands.get(0) instanceof Constant) {
                List<Expression> rest = ne.operands.subList(1, ne.operands.size());
                if (rest.size() == 1) return rest.get(0);
                return new NaryExpression(Operator.MUL, rest);
            }
        }
        return e;
    }

    /** Reconstructs a term from coefficient c and symbolic base. */
    private static Expression buildTerm(double c, Expression base) {
        if (c ==  1.0) return base;
        if (c == -1.0) return new NaryExpression(Operator.MUL, new Constant(-1), base);
        return new NaryExpression(Operator.MUL, new Constant(c), base);
    }

    /**
     * Negates an expression efficiently:
     *   Constant(c)          → Constant(−c)
     *   MUL(Constant(c), …)  → MUL(Constant(−c), …)   [flip the coefficient]
     *   ADD(a, b, …)         → ADD(−a, −b, …)          [distribute]
     *   other                → MUL(Constant(−1), other)
     *
     * Package-private so BinaryExpression can call it for SUB conversion.
     */
    static Expression negate(Expression e) {
        if (e instanceof Constant)
            return new Constant(-((Constant) e).getValue());

        if (e instanceof NaryExpression) {
            NaryExpression ne = (NaryExpression) e;
            if (ne.op == Operator.MUL
                    && !ne.operands.isEmpty()
                    && ne.operands.get(0) instanceof Constant) {
                double c = ((Constant) ne.operands.get(0)).getValue();
                List<Expression> ops = new ArrayList<>(ne.operands);
                ops.set(0, new Constant(-c));
                if (ops.size() == 1) return ops.get(0);
                return new NaryExpression(Operator.MUL, ops);
            }
            if (ne.op == Operator.ADD) {
                List<Expression> negated = new ArrayList<>();
                for (Expression child : ne.operands) negated.add(negate(child));
                return new NaryExpression(Operator.ADD, negated);
            }
        }
        return new NaryExpression(Operator.MUL, new Constant(-1), e);
    }

    /**
     * Canonical string key for a symbolic expression.
     * Factors of MUL are sorted alphabetically so that x·y and y·x map to
     * the same key — implementing commutativity in the term-grouping map.
     */
    private static String canonKey(Expression e) {
        if (e instanceof NaryExpression) {
            NaryExpression ne = (NaryExpression) e;
            if (ne.op == Operator.MUL) {
                List<Expression> consts = new ArrayList<>();
                List<Expression> syms   = new ArrayList<>();
                for (Expression f : ne.operands) {
                    if (f instanceof Constant) consts.add(f);
                    else                       syms.add(f);
                }
                syms.sort(Comparator.comparing(NaryExpression::repr));
                List<Expression> canonical = new ArrayList<>(consts);
                canonical.addAll(syms);
                return repr(new NaryExpression(Operator.MUL, canonical));
            }
        }
        return repr(e);
    }

    /**
     * Stable structural representation used as map keys.
     * Distinct from toString() — no special minus handling.
     */
    private static String repr(Expression e) {
        return e.toString();
    }

    /** Heuristic polynomial degree for display ordering (higher-degree terms first). */
    private static int termDegree(Expression base) {
        if (base instanceof Variable) return 1;
        if (base instanceof BinaryExpression) {
            BinaryExpression be = (BinaryExpression) base;
            if (be.getOp() == Operator.POW && be.getRight() instanceof Constant)
                return (int) Math.round(((Constant) be.getRight()).getValue());
        }
        if (base instanceof NaryExpression) {
            NaryExpression ne = (NaryExpression) base;
            if (ne.op == Operator.MUL) {
                int sum = 0;
                for (Expression f : ne.operands) sum += termDegree(f);
                return sum;
            }
        }
        return 0;
    }

    /** True if e is structurally an additive node (ADD, or SUB which becomes ADD). */
    private static boolean isAddNode(Expression e) {
        if (e instanceof NaryExpression)
            return ((NaryExpression) e).op == Operator.ADD;
        if (e instanceof BinaryExpression) {
            Operator o = ((BinaryExpression) e).getOp();
            return o == Operator.ADD || o == Operator.SUB;
        }
        return false;
    }

    /** Returns the flat list of additive operands for an ADD/SUB node. */
    private static List<Expression> getAddOperands(Expression e) {
        if (e instanceof NaryExpression
                && ((NaryExpression) e).op == Operator.ADD)
            return ((NaryExpression) e).getOperands();
        if (e instanceof BinaryExpression
                && ((BinaryExpression) e).getOp() == Operator.SUB) {
            List<Expression> ops = new ArrayList<>();
            ops.add(((BinaryExpression) e).getLeft());
            ops.add(negate(((BinaryExpression) e).getRight()));
            return ops;
        }
        return Collections.singletonList(e);
    }

    private static boolean isZero(Expression e) {
        return e instanceof Constant && ((Constant) e).getValue() == 0.0;
    }
    private static boolean isOne(Expression e) {
        return e instanceof Constant && ((Constant) e).getValue() == 1.0;
    }

    // =======================================================================
    //  Display
    // =======================================================================

    /**
     * Renders the expression in a human-readable form.
     *
     * ADD: prints negative terms as "− |term|" rather than "+ (−1 × term)".
     *   (6 * (x ^ 2)) + x − 1
     *
     * MUL: prints all factors separated by " * ".
     *   (6 * (x ^ 2))
     */
    @Override
    public String toString() {
        if (operands.isEmpty())  return "0";
        if (operands.size() == 1) return operands.get(0).toString();

        StringBuilder sb = new StringBuilder("(");
        if (op == Operator.ADD) {
            sb.append(operands.get(0).toString());
            for (int i = 1; i < operands.size(); i++) {
                Expression t = operands.get(i);
                if (isNegativeTerm(t)) {
                    sb.append(" - ").append(absDisplay(t));
                } else {
                    sb.append(" + ").append(t);
                }
            }
        } else {  // MUL
            for (int i = 0; i < operands.size(); i++) {
                if (i > 0) sb.append(" * ");
                sb.append(operands.get(i));
            }
        }
        sb.append(")");
        return sb.toString();
    }

    /** True if this term carries a negative leading coefficient. */
    private static boolean isNegativeTerm(Expression e) {
        if (e instanceof Constant)
            return ((Constant) e).getValue() < 0;
        if (e instanceof NaryExpression) {
            NaryExpression ne = (NaryExpression) e;
            if (ne.op == Operator.MUL
                    && !ne.operands.isEmpty()
                    && ne.operands.get(0) instanceof Constant)
                return ((Constant) ne.operands.get(0)).getValue() < 0;
        }
        return false;
    }

    /**
     * Returns an expression equivalent to |e| for display after a " − " sign.
     * E.g. MUL(−2, x) → MUL(2, x) so we print "− (2 * x)" not "− (−2 * x)".
     */
    private static Expression absDisplay(Expression e) {
        if (e instanceof Constant)
            return new Constant(Math.abs(((Constant) e).getValue()));
        if (e instanceof NaryExpression) {
            NaryExpression ne = (NaryExpression) e;
            if (ne.op == Operator.MUL
                    && !ne.operands.isEmpty()
                    && ne.operands.get(0) instanceof Constant) {
                double absC = Math.abs(((Constant) ne.operands.get(0)).getValue());
                List<Expression> ops = new ArrayList<>(ne.operands);
                if (absC == 1.0) {
                    ops.remove(0);
                    return (ops.size() == 1) ? ops.get(0)
                            : new NaryExpression(Operator.MUL, ops);
                }
                ops.set(0, new Constant(absC));
                return new NaryExpression(Operator.MUL, ops);
            }
        }
        return e;
    }

    // =======================================================================
    //  size / equals / hashCode
    // =======================================================================

    @Override
    public int size() {
        int s = 1;
        for (Expression e : operands) s += e.size();
        return s;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NaryExpression)) return false;
        NaryExpression that = (NaryExpression) o;
        return op == that.op && Objects.equals(operands, that.operands);
    }

    @Override
    public int hashCode() {
        return Objects.hash(op, operands);
    }
}
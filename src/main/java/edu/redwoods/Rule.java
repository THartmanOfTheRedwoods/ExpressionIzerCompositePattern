package edu.redwoods;

import java.util.Optional;

@FunctionalInterface
public interface Rule {
    Optional<Expression> apply(Operator op, Expression left, Expression right);
}

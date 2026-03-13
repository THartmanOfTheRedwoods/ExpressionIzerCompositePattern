package edu.redwoods;

public interface Expression {
    Expression simplify();
    int size();   // number of nodes in the tree (useful for growth detection)
}
package edu.redwoods;

public enum Operator {
    ADD("+"), SUB("-"), MUL("*"), DIV("/"), POW("^");

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
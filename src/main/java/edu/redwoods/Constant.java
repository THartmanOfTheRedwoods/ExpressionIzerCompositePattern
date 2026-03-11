package edu.redwoods;

// Leaf: edu.redwoods.Constant
public class Constant implements Expression {
    private final double value;

    public Constant(double value) {
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    @Override
    public Expression simplify() {
        return this;
    }

    @Override
    public int size() {
        return 1;
    }

    // toString, equals, hashCode as before...
    @Override
    public String toString() {
        if (value == (long) value)
            return String.valueOf((long) value);
        return String.valueOf(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Constant constant = (Constant) o;
        return Double.compare(constant.value, value) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }
}
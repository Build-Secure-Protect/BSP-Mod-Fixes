package com.mrgregles.bsp_mod_fixes.fix.emi;

import java.util.List;

/** The values of a JEI cycle button (F-14): a current index that steps round the list. Pure, for the tests. */
public final class CycleValues<T> {
    private final List<T> values;
    private int index;

    public CycleValues(List<T> values, T initial) {
        this.values = List.copyOf(values);
        int at = this.values.indexOf(initial);
        this.index = Math.max(at, 0);
    }

    public T value() {
        return values.isEmpty() ? null : values.get(index);
    }

    /** Steps to the next value, wrapping round, and answers it. */
    public T next() {
        if (!values.isEmpty()) {
            index = (index + 1) % values.size();
        }
        return value();
    }

    /** Selects a value; one not in the list is ignored. */
    public void set(T value) {
        int at = values.indexOf(value);
        if (at >= 0) {
            index = at;
        }
    }

    public int size() {
        return values.size();
    }
}

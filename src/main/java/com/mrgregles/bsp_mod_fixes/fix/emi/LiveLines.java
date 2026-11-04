package com.mrgregles.bsp_mod_fixes.fix.emi;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A modifiable list of lines kept index-aligned with a second list of their rendered forms (EMI's {@code tooltip}) and a
 * third list holding the plain text of the lines that have one (EMI's {@code legacyText}). JEI's {@code
 * ITooltipBuilder.getLines()} must be "directly modifiable", and Tinkers' Construct removes a line through a ListIterator
 * and then adds its own, so every write here goes to all three lists. Generic so the GameTest server can exercise it without
 * client classes: {@code L} is the line, {@code C} its rendered form, {@code T} its plain text.
 *
 * <p>The owner of the rendered list may append to it through its own methods; {@link #recordAppended} is called after each
 * such append. Should the rendered list ever change behind this list's back, {@link #sync()} realigns by identity: a
 * rendered entry this list has seen keeps its line, one it has not gets an opaque stand-in ({@code opaque}), and the line of
 * an entry that is gone is dropped. So the indexes never drift.
 */
public final class LiveLines<L, C, T> extends AbstractList<L> {
    private final List<L> lines = new ArrayList<>();
    /** The rendered entry of each line, by identity, so the view can tell what it has seen. */
    private final List<C> seen = new ArrayList<>();
    private final List<C> rendered;
    private final List<T> legacy;
    private final Function<L, C> render;
    private final Function<L, Optional<T>> textOf;
    private final Function<C, L> opaque;

    /**
     * @param rendered the owner's rendered list, which this list mirrors index for index
     * @param legacy   the owner's plain-text list: one entry, in order, per line that {@code textOf} answers
     * @param render   how a line is rendered (never empty: a line that cannot render becomes a blank one, so indexes hold)
     * @param textOf   the plain text of a line, if it has one
     * @param opaque   a stand-in line for a rendered entry this list never saw
     */
    public LiveLines(List<C> rendered, List<T> legacy, Function<L, C> render, Function<L, Optional<T>> textOf, Function<C, L> opaque) {
        this.rendered = rendered;
        this.legacy = legacy;
        this.render = render;
        this.textOf = textOf;
        this.opaque = opaque;
    }

    /** Realigns with the rendered list by identity. Called before every read and write. */
    public void sync() {
        if (sameAsSeen()) {
            return;
        }
        List<L> newLines = new ArrayList<>(rendered.size());
        List<C> newSeen = new ArrayList<>(rendered.size());
        int from = 0;
        for (C entry : rendered) {
            int at = indexOfIdentity(entry, from);
            if (at >= 0) {
                newLines.add(lines.get(at));
                from = at + 1;
            } else {
                newLines.add(opaque.apply(entry));
            }
            newSeen.add(entry);
        }
        lines.clear();
        lines.addAll(newLines);
        seen.clear();
        seen.addAll(newSeen);
    }

    /**
     * After the owner's own add: records the line if the rendered list grew by one (the owner may drop a line it cannot
     * render, in which case nothing is recorded). Anything else that changed in between is realigned first.
     */
    public void recordAppended(L line) {
        if (rendered.size() == seen.size() + 1 && sameAsSeen(seen.size())) {
            lines.add(line);
            seen.add(rendered.get(rendered.size() - 1));
            return;
        }
        boolean lastIsNew = !rendered.isEmpty() && indexOfIdentity(rendered.get(rendered.size() - 1), 0) < 0;
        sync();
        if (lastIsNew) {
            lines.set(lines.size() - 1, line);
        }
    }

    /** Appends a line whose rendered form is already known (the fluid amount probe of F-13). */
    public void seed(L line, C renderedLine) {
        sync();
        lines.add(line);
        seen.add(renderedLine);
        rendered.add(renderedLine);
        textOf.apply(line).ifPresent(legacy::add);
    }

    @Override
    public L get(int index) {
        sync();
        return lines.get(index);
    }

    @Override
    public int size() {
        sync();
        return lines.size();
    }

    @Override
    public L set(int index, L line) {
        sync();
        C c = render.apply(line);
        L old = lines.set(index, line);
        seen.set(index, c);
        rendered.set(index, c);
        int at = legacyIndexBefore(index);
        Optional<T> oldText = textOf.apply(old);
        Optional<T> newText = textOf.apply(line);
        if (oldText.isPresent() && at < legacy.size()) {
            if (newText.isPresent()) {
                legacy.set(at, newText.get());
            } else {
                legacy.remove(at);
            }
        } else if (newText.isPresent()) {
            legacy.add(Math.min(at, legacy.size()), newText.get());
        }
        return old;
    }

    @Override
    public void add(int index, L line) {
        sync();
        C c = render.apply(line);
        int at = legacyIndexBefore(index);
        lines.add(index, line);
        seen.add(index, c);
        rendered.add(index, c);
        textOf.apply(line).ifPresent(t -> legacy.add(Math.min(at, legacy.size()), t));
    }

    @Override
    public L remove(int index) {
        sync();
        int at = legacyIndexBefore(index);
        L old = lines.remove(index);
        seen.remove(index);
        rendered.remove(index);
        if (textOf.apply(old).isPresent() && at < legacy.size()) {
            legacy.remove(at);
        }
        return old;
    }

    @Override
    public void clear() {
        lines.clear();
        seen.clear();
        rendered.clear();
        legacy.clear();
    }

    /** Whether the rendered list is, entry for entry by identity, what this list has seen. */
    private boolean sameAsSeen() {
        return rendered.size() == seen.size() && sameAsSeen(seen.size());
    }

    /** Whether the first {@code n} rendered entries are the seen ones, by identity. */
    private boolean sameAsSeen(int n) {
        if (rendered.size() < n || seen.size() < n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (rendered.get(i) != seen.get(i)) {
                return false;
            }
        }
        return true;
    }

    private int indexOfIdentity(C entry, int from) {
        for (int i = from; i < seen.size(); i++) {
            if (seen.get(i) == entry) {
                return i;
            }
        }
        return -1;
    }

    /** How many lines before {@code index} have plain text: the legacy index of the line at {@code index}. */
    private int legacyIndexBefore(int index) {
        int n = 0;
        for (int i = 0; i < index && i < lines.size(); i++) {
            if (textOf.apply(lines.get(i)).isPresent()) {
                n++;
            }
        }
        return n;
    }
}

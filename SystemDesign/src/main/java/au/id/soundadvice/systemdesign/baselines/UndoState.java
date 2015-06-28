/*
 * This is free and unencumbered software released into the public domain.
 * 
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 * 
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * 
 * For more information, please refer to <http://unlicense.org/>
 */
package au.id.soundadvice.systemdesign.baselines;

import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.files.SaveTransaction;
import au.id.soundadvice.systemdesign.model.Identity;
import au.id.soundadvice.systemdesign.model.Item;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class UndoState {

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + Objects.hashCode(this.functional);
        hash = 17 * hash + Objects.hashCode(this.allocated);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final UndoState other = (UndoState) obj;
        if (!Objects.equals(this.functional, other.functional)) {
            return false;
        }
        if (!Objects.equals(this.allocated, other.allocated)) {
            return false;
        }
        return true;
    }

    /**
     * A simple tuple class for returning an updated Baseline along with an
     * updated Relation class.
     *
     * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
     * @param <T> The type of the relation
     */
    public class StateAnd<T> {

        private StateAnd(UndoState state, T relation) {
            this.state = state;
            this.relation = relation;
        }

        public UndoState getState() {
            return state;
        }

        public T getRelation() {
            return relation;
        }

        private final UndoState state;
        private final T relation;
    }

    public <T> StateAnd<T> and(T relation) {
        return new StateAnd<>(this, relation);
    }

    public static UndoState createNew() {
        return new UndoState(Baseline.empty(), Baseline.create(Identity.create()));
    }

    public static UndoState load(Directory directory) throws IOException {
        Directory functionalDirectory = directory.getParent();
        if (functionalDirectory.getIdentity().isPresent()) {
            // Subsystem design - load functional baseline as well
            Baseline functionalBaseline = Baseline.load(functionalDirectory);
            Baseline allocatedBaseline = Baseline.load(directory);
            return new UndoState(
                    functionalBaseline,
                    allocatedBaseline);
        }
        // Top-level design
        return new UndoState(Baseline.empty(), Baseline.load(directory));
    }

    public void saveTo(SaveTransaction transaction, Directory directory) throws IOException {
        if (getSystemOfInterest().isPresent()) {
            functional.saveTo(transaction, directory.getParent());
        }
        allocated.saveTo(transaction, directory);
    }

    public Baseline getFunctional() {
        return functional;
    }

    public Baseline getAllocated() {
        return allocated;
    }

    public Optional<Item> getSystemOfInterest() {
        Identity identity = allocated.getIdentity();
        return functional.getItemForIdentity(identity);
    }

    private UndoState(Baseline functional, Baseline allocated) {
        this.functional = functional;
        this.allocated = allocated;
    }
    private final Baseline functional;
    private final Baseline allocated;

    @CheckReturnValue
    public UndoState setFunctional(Baseline value) {
        if (functional == value) {
            return this;
        } else {
            return new UndoState(value, allocated);
        }
    }

    @CheckReturnValue
    public UndoState setAllocated(Baseline value) {
        if (allocated == value) {
            return this;
        } else {
            return new UndoState(functional, value);
        }
    }
}

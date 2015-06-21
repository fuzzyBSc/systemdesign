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

import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.files.SaveTransaction;
import au.id.soundadvice.systemdesign.model.Identity;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.relation.Relation;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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

    public static UndoState createNew() {
        return new UndoState(Optional.empty(), AllocatedBaseline.create(Identity.create()));
    }

    public static UndoState load(Directory directory) throws IOException {
        Directory functionalDirectory = directory.getParent();
        if (functionalDirectory.getIdentity().isPresent()) {
            // Subsystem design - load functional baseline as well
            AllocatedBaseline functionalBaseline = AllocatedBaseline.load(functionalDirectory);
            AllocatedBaseline allocatedBaseline = AllocatedBaseline.load(directory);
            Optional<Item> systemOfInterest = functionalBaseline.getStore().get(
                    allocatedBaseline.getIdentity().getUuid(), Item.class);
            if (systemOfInterest.isPresent()) {
                return new UndoState(
                        Optional.of(new FunctionalBaseline(systemOfInterest.get(), functionalBaseline)),
                        allocatedBaseline);
            }
        }
        // Top-level design
        return new UndoState(Optional.empty(), AllocatedBaseline.load(directory));
    }

    public void saveTo(SaveTransaction transaction, Directory directory) throws IOException {
        if (functional.isPresent()) {
            functional.get().saveTo(transaction, directory.getParent());
        }
        allocated.saveTo(transaction, directory);
    }

    public Optional<FunctionalBaseline> getFunctional() {
        return functional;
    }

    public AllocatedBaseline getAllocated() {
        return allocated;
    }

    public <T extends Relation> Optional<T> getAllocatedInstance(
            UUID uuid, Class<T> clazz) {
        return allocated.getStore().get(uuid, clazz);
    }

    public <T extends Relation> Optional<T> getFunctionalInstance(
            UUID uuid, Class<T> clazz) {
        if (functional.isPresent()) {
            return functional.get().getStore().get(uuid, clazz);
        } else {
            return Optional.empty();
        }
    }

    private UndoState(
            Optional<FunctionalBaseline> functional,
            AllocatedBaseline allocated) {
        this.functional = functional;
        this.allocated = allocated;
    }
    private final Optional<FunctionalBaseline> functional;
    private final AllocatedBaseline allocated;

    @CheckReturnValue
    public UndoState setFunctional(FunctionalBaseline value) {
        return new UndoState(Optional.of(value), allocated);
    }

    @CheckReturnValue
    public UndoState setAllocated(AllocatedBaseline value) {
        return new UndoState(functional, value);
    }
}

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
import java.io.IOException;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class UndoState {

    public static UndoState createNew() {
        return new UndoState(null, AllocatedBaseline.create(Identity.create()));
    }

    static UndoState newChild(Identity identity, Directory directory) throws IOException {
        Directory functionalDirectory = directory.getParent();
        if (functionalDirectory.hasIdentity()) {
            // Subsystem design - load functional baseline as well
            AllocatedBaseline functionalBaseline = AllocatedBaseline.load(functionalDirectory);
            AllocatedBaseline allocatedBaseline = AllocatedBaseline.create(identity);
            Item systemOfInterest = functionalBaseline.getStore().get(
                    allocatedBaseline.getIdentity().getUuid(), Item.class);
            return new UndoState(
                    new FunctionalBaseline(systemOfInterest, functionalBaseline),
                    allocatedBaseline);
        } else {
            // Top-level design
            return new UndoState(null, AllocatedBaseline.create(identity));
        }
    }

    public static UndoState load(Directory directory) throws IOException {
        Directory functionalDirectory = directory.getParent();
        if (functionalDirectory.hasIdentity()) {
            // Subsystem design - load functional baseline as well
            AllocatedBaseline functionalBaseline = AllocatedBaseline.load(functionalDirectory);
            AllocatedBaseline allocatedBaseline = AllocatedBaseline.load(directory);
            Item systemOfInterest = functionalBaseline.getStore().get(
                    allocatedBaseline.getIdentity().getUuid(), Item.class);
            return new UndoState(
                    new FunctionalBaseline(systemOfInterest, functionalBaseline),
                    allocatedBaseline);
        } else {
            // Top-level design
            return new UndoState(null, AllocatedBaseline.load(directory));
        }
    }

    public void saveTo(SaveTransaction transaction, Directory directory) throws IOException {
        if (functional != null) {
            functional.saveTo(transaction, directory.getParent());
        }
        allocated.saveTo(transaction, directory);
    }

    @Nullable
    public FunctionalBaseline getFunctional() {
        return functional;
    }

    public AllocatedBaseline getAllocated() {
        return allocated;
    }

    private UndoState(FunctionalBaseline functional, AllocatedBaseline allocated) {
        this.functional = functional;
        this.allocated = allocated;
    }
    private final FunctionalBaseline functional;
    private final AllocatedBaseline allocated;

    @CheckReturnValue
    public UndoState setFunctional(FunctionalBaseline value) {
        return new UndoState(value, allocated);
    }

    @CheckReturnValue
    public UndoState setAllocated(AllocatedBaseline value) {
        return new UndoState(functional, value);
    }
}

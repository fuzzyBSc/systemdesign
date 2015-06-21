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
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.relation.Relation;
import au.id.soundadvice.systemdesign.relation.RelationStore;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FunctionalBaseline {

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 67 * hash + Objects.hashCode(this.systemOfInterest);
        hash = 67 * hash + Objects.hashCode(this.context);
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
        final FunctionalBaseline other = (FunctionalBaseline) obj;
        if (!Objects.equals(this.systemOfInterest, other.systemOfInterest)) {
            return false;
        }
        if (!Objects.equals(this.context, other.context)) {
            return false;
        }
        return true;
    }

    public Item getSystemOfInterest() {
        return systemOfInterest;
    }

    public AllocatedBaseline getContext() {
        return context;
    }

    public FunctionalBaseline(Item systemOfInterest, AllocatedBaseline context) {
        this.systemOfInterest = systemOfInterest;
        this.context = context;
    }
    private final Item systemOfInterest;
    private final AllocatedBaseline context;

    void saveTo(SaveTransaction transaction, Directory directory) throws IOException {
        context.saveTo(transaction, directory);
    }

    public RelationStore getStore() {
        return context.getStore();
    }

    @CheckReturnValue
    public FunctionalBaseline add(Relation relation) {
        if (systemOfInterest.getUuid().equals(relation.getUuid())) {
            // System of interest replacement
            if (relation instanceof Item) {
                return new FunctionalBaseline((Item) relation, context.add(relation));
            } else {
                // Ignore the request
                return this;
            }
        } else {
            return new FunctionalBaseline(systemOfInterest, context.add(relation));
        }
    }

    @CheckReturnValue
    public FunctionalBaseline remove(UUID uuid) {
        if (systemOfInterest.getUuid().equals(uuid)) {
            // Ignore the request
            return this;
        } else {
            return new FunctionalBaseline(systemOfInterest, context.remove(uuid));
        }
    }

    @CheckReturnValue
    public FunctionalBaseline removeAll(Stream<UUID> toRemove) {
        AllocatedBaseline newContext = context.removeAll(toRemove);
        Optional<Item> newSystem = newContext.getStore().get(systemOfInterest.getUuid(), Item.class);
        if (newSystem.isPresent()) {
            return new FunctionalBaseline(newSystem.get(), newContext);
        } else {
            // Don't allow deletion of the system of interest
            return this;
        }
    }

    public boolean hasRelation(Relation relation) {
        return relation != null
                && getStore().get(relation.getUuid(), relation.getClass()).isPresent();
    }

    @CheckReturnValue
    public FunctionalBaseline setContext(AllocatedBaseline context) {
        Optional<Item> system = context.getStore().get(systemOfInterest.getUuid(), Item.class);
        if (system.isPresent()) {
            return new FunctionalBaseline(system.get(), context);
        } else {
            return this;
        }
    }

}

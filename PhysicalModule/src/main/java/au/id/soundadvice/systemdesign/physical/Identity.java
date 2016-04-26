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
package au.id.soundadvice.systemdesign.physical;

import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.moduleapi.relation.Reference;
import au.id.soundadvice.systemdesign.moduleapi.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.physical.beans.IdentityBean;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Identity implements Relation {

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 43 * hash + Objects.hashCode(this.identity);
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
        final Identity other = (Identity) obj;
        if (!Objects.equals(this.identity, other.identity)) {
            return false;
        }
        if (!Objects.equals(this.idPath, other.idPath)) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return true;
    }

    public static Optional<Item> getSystemOfInterest(UndoState state) {
        Optional<Identity> optionalIdentity
                = Identity.findAll(state.getAllocated()).findAny();
        return optionalIdentity.flatMap(
                identity -> state.getFunctional().get(identity.getIdentifier(), Item.class));
    }

    /**
     * Retrieve identifying information about the system of interest for this
     * baseline.
     *
     * @param baseline The baseline whose identity to find
     * @return
     */
    public static Identity find(Relations baseline) {
        return findAll(baseline).findAny().get();
    }

    /**
     * Retrieve identifying information about the system of interest for this
     * baseline.
     *
     * @param baseline The baseline whose identity to find
     * @return
     */
    public static Stream<Identity> findAll(Relations baseline) {
        return baseline.findByClass(Identity.class);
    }

    public String getName() {
        return name;
    }

    public static Identity create() {
        return new Identity(UUID.randomUUID().toString(), IDPath.empty(), "");
    }

    @Override
    public String toString() {
        if ("".equals(name)) {
            return idPath.toString();
        } else {
            return idPath.toString() + " " + name;
        }
    }

    public Identity(String identity, IDPath idPath, String name) {
        this.identity = identity;
        this.idPath = idPath;
        this.name = name;
    }

    @Override
    public String getIdentifier() {
        return identity;
    }

    public IDPath getIdPath() {
        return idPath;
    }

    public Identity(IdentityBean bean) {
        this.identity = bean.getIdentifier();
        this.idPath = IDPath.valueOfDotted(bean.getId());
        this.name = bean.getName();
    }

    private final String identity;
    private final IDPath idPath;
    private final String name;

    public IdentityBean toBean() {
        return new IdentityBean(identity, idPath.toString(), name);
    }
    private static final ReferenceFinder<Identity> FINDER
            = new ReferenceFinder<>(Identity.class);

    @Override
    public Stream<Reference> getReferences() {
        return FINDER.getReferences(this);
    }

    /**
     * Return a copy of this baseline with identity set to id.
     *
     * @param baseline The baseline to update
     * @param id The identity to set
     * @return
     */
    @CheckReturnValue
    public static Relations setIdentity(Relations baseline, Identity id) {
        Iterator<Identity> existing = findAll(baseline).iterator();
        while (existing.hasNext()) {
            baseline = baseline.remove(existing.next().getIdentifier());
        }
        return baseline.add(id);
    }

    @CheckReturnValue
    public Identity setId(IDPath value) {
        return new Identity(identity, value, name);
    }
}

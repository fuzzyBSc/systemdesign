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
package au.id.soundadvice.systemdesign.model;

import au.id.soundadvice.systemdesign.beans.BeanFactory;
import au.id.soundadvice.systemdesign.beans.IdentityBean;
import au.id.soundadvice.systemdesign.relation.Reference;
import au.id.soundadvice.systemdesign.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.relation.Relation;
import au.id.soundadvice.systemdesign.relation.RelationContext;
import java.util.Collection;
import java.util.UUID;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Identity implements BeanFactory<RelationContext, IdentityBean>, Relation {

    @Override
    public String toString() {
        return idPath.toString();
    }

    public Identity(UUID uuid, IDPath idPath) {
        this.uuid = uuid;
        this.idPath = idPath;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public IDPath getIdPath() {
        return idPath;
    }

    public Identity(IdentityBean bean) {
        this.uuid = bean.getUuid();
        this.idPath = IDPath.valueOf(bean.getId());
    }

    private final UUID uuid;
    private final IDPath idPath;

    @Override
    public IdentityBean toBean(RelationContext context) {
        return new IdentityBean(uuid, idPath.toString());
    }
    private static final ReferenceFinder<Identity> finder
            = new ReferenceFinder<>(Identity.class);

    @Override
    public Collection<Reference<?, ?>> getReferences() {
        return finder.getReferences(this);
    }

    @CheckReturnValue
    public Identity setId(IDPath value) {
        return new Identity(uuid, value);
    }
}

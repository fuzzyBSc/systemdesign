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

import au.id.soundadvice.systemdesign.beans.DictionaryBean;
import au.id.soundadvice.systemdesign.beans.BeanFactory;
import au.id.soundadvice.systemdesign.relation.Reference;
import au.id.soundadvice.systemdesign.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.relation.Relation;
import au.id.soundadvice.systemdesign.relation.RelationContext;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class DictionaryEntry implements BeanFactory<RelationContext, DictionaryBean>, Relation {

    @Override
    public UUID getUuid() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public DictionaryEntry(DictionaryBean bean) {
        this.uuid = bean.getUuid();
        this.term = bean.getTerm();
        this.definition = bean.getDefinition();
        Set<DictionaryCategory> tmpCategories = EnumSet.noneOf(DictionaryCategory.class);
        Set<String> tmpUnusedCategories = new HashSet<>();
        for (String category : bean.getCategories().split("\\s+")) {
            try {
                DictionaryCategory enumValue = DictionaryCategory.valueOf(category);
            } catch (IllegalArgumentException ex) {
                tmpUnusedCategories.add(category);
            }
        }
        if (tmpCategories.isEmpty()) {
            this.categories = Collections.emptySet();
        } else {
            this.categories = Collections.unmodifiableSet(tmpCategories);
        }
        if (tmpUnusedCategories.isEmpty()) {
            this.unusedCategories = Collections.emptySet();
        } else {
            this.unusedCategories = Collections.unmodifiableSet(tmpUnusedCategories);
        }
    }

    @Override
    public DictionaryBean toBean(RelationContext context) {
        StringBuilder builder = new StringBuilder();
        String sep = "";
        for (DictionaryCategory category : categories) {
            builder.append(sep);
            builder.append(category);
            sep = " ";
        }
        for (String category : unusedCategories) {
            builder.append(sep);
            builder.append(category);
            sep = " ";
        }
        return new DictionaryBean(uuid, term, definition, builder.toString());
    }

    private final UUID uuid;
    private final String term;
    private final String definition;
    private final Set<DictionaryCategory> categories;
    private final Set<String> unusedCategories;

    private static final ReferenceFinder<DictionaryEntry> finder
            = new ReferenceFinder<>(DictionaryEntry.class);

    @Override
    public Stream<Reference> getReferences() {
        return finder.getReferences(this);
    }
}

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
package au.id.soundadvice.systemdesign.relation;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class RelationStoreTest {

    /**
     * Test of valueOf method, of class RelationStore.
     */
    @Test
    public void testValueOf() {
        System.out.println("valueOf");
        ToRelation to = new ToRelation();
        FromRelation from = new FromRelation(to.getUuid());
        FromRelation typeMismatch = new FromRelation(from.getUuid());
        FromRelation dangling = new FromRelation(UUID.randomUUID());

        System.out.println("Mismatched types and dangling references are removed");
        RelationStore store = RelationStore.valueOf(
                Arrays.asList(to, from, typeMismatch, dangling));

        assertSame(to, from.getReference().getTarget(store));
        Collection<? extends FromRelation> reverse = store.getReverse(
                to.getUuid(), FromRelation.class);
        assertEquals(1, reverse.size());
        assertSame(from, reverse.iterator().next());
        Collection<ToRelation> toByClass = store.getByClass(ToRelation.class);
        assertEquals(1, toByClass.size());
        assertSame(to, toByClass.iterator().next());
        Collection<FromRelation> fromByClass = store.getByClass(FromRelation.class);
        assertEquals(1, fromByClass.size());
        assertSame(from, fromByClass.iterator().next());
    }

}

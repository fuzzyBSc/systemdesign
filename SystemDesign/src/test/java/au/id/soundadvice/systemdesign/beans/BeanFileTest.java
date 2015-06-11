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
package au.id.soundadvice.systemdesign.beans;

import au.id.soundadvice.systemdesign.files.BeanFile;
import au.id.soundadvice.systemdesign.files.SaveTransaction;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class BeanFileTest {

    /**
     * Test of getEntries method, of class BeanFile.
     */
    @Test
    public void testNewFile() throws IOException {
        System.out.println("newFile");
        BeanFile<ItemBean> file = BeanFile.newFile(ItemBean.class, Paths.get("/tmp/beans.csv"));
        SortedMap<UUID, ItemBean> entries = file.getEntries();
        Set<String> unmatched = new HashSet<>();
        for (int ii = 0; ii < 100; ++ii) {
            UUID uuid = UUID.randomUUID();
            String idString = Integer.toString(ii);
            entries.put(uuid, new ItemBean(uuid, idString, "Item " + ii, "This is item number " + ii, 0, 0, false));
            unmatched.add(idString);
        }
        try (SaveTransaction transaction = new SaveTransaction()) {
            file.save(transaction);
            transaction.commit();
        }

        file = BeanFile.load(ItemBean.class, Paths.get("/tmp/beans.csv"));
        for (ItemBean bean : file.getEntries().values()) {
            String id = bean.getId();
            if (unmatched.remove(id)) {
                assertEquals("Item " + id, bean.getName());
                assertEquals("This is item number " + id, bean.getDescription());
            } else {
                fail("Found " + id);
            }
        }
        assertTrue(unmatched.isEmpty());
    }

}

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
package au.id.soundadvice.systemdesign.files;

import au.id.soundadvice.systemdesign.beans.BeanFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Model the contents of a single file for loading and storing. The whole file
 * is stored in memory so there is a limit to how much can be modeled this way,
 * but the overall system breakdown should avoid this becoming a major problem.
 *
 * The main purpose of this class is to ensure that files are loaded and saved
 * consistently, including in consistent sort order to enable effective diffs
 * between file revisions.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <T> The bean type stored in this file.
 */
public class BeanFile<T extends Identifiable> {

    public static <B extends Identifiable, T extends BeanFactory<C, B>, C> void saveModel(
            SaveTransaction transaction, C context, Path csv,
            Class<B> beanClass, Collection<T> objects) throws IOException {
        BeanFile<B> file = BeanFile.newFile(beanClass, csv);
        for (T model : objects) {
            file.getEntries().put(model.getUuid(), model.toBean(context));
        }
        file.save(transaction);
    }

    public static <B extends Identifiable> void saveBeans(
            SaveTransaction transaction, Path csv,
            Class<B> beanClass, Map<UUID, B> objects) throws IOException {
        BeanFile<B> file = BeanFile.newFile(beanClass, csv);
        for (B bean : objects.values()) {
            file.getEntries().put(bean.getUuid(), bean);
        }
        file.save(transaction);
    }

    public static <B extends Identifiable> void saveBean(
            SaveTransaction transaction, Path csv, B bean) throws IOException {
        BeanFile<B> file = BeanFile.newFile((Class<B>) bean.getClass(), csv);
        file.getEntries().put(bean.getUuid(), bean);
        file.save(transaction);
    }

    public ConcurrentNavigableMap<UUID, T> getEntries() {
        return entries;
    }

    private BeanFile(Class<T> clazz, Path path, ConcurrentNavigableMap<UUID, T> entries) {
        this.clazz = clazz;
        this.path = path;
        this.entries = entries;
    }

    public static <T extends Identifiable> BeanFile<T> newFile(Class<T> clazz, Path path) {
        return new BeanFile(clazz, path, new ConcurrentSkipListMap<UUID, T>());
    }

    public static <T extends Identifiable> BeanFile<T> load(Class<T> clazz, Path path) throws IOException {
        ConcurrentNavigableMap<UUID, T> entries = new ConcurrentSkipListMap<>();
        if (Files.exists(path)) {
            try (BeanReader<T> reader = BeanReader.forPath(clazz, path)) {
                for (;;) {
                    T bean = reader.read();
                    if (bean == null) {
                        break;
                    }
                    entries.put(bean.getUuid(), bean);
                }
            }
        } else {
            // A nonexistent file is treated as empty
        }
        return new BeanFile(clazz, path, entries);
    }

    public void save(SaveTransaction transaction) throws IOException {
        Path directory = path.getParent();
        Path tempFile = Files.createTempFile(directory, null, null);
        transaction.addJob(path, tempFile);
        try (BeanWriter<T> writer = BeanWriter.forPath(clazz, tempFile)) {
            for (T bean : entries.values()) {
                writer.write(bean);
            }
        }
    }

    public void saveToTemp() throws IOException {
        Path directory = path.getParent();
        Path tempFile = Files.createTempFile(directory, null, null);
        try {
            try (BeanWriter<T> writer = BeanWriter.forPath(clazz, tempFile)) {
                for (T bean : entries.values()) {
                    writer.write(bean);
                }
            }
            Files.move(tempFile, path, REPLACE_EXISTING, ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private final Class<T> clazz;
    private final Path path;
    private final ConcurrentNavigableMap<UUID, T> entries;
}

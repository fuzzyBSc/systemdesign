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

import au.id.soundadvice.systemdesign.moduleapi.Identifiable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

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

    public static <B extends Identifiable> void saveBeans(
            SaveTransaction transaction, Path csv,
            Class<B> beanClass, Stream<B> beans) throws IOException {
        save(transaction, csv, beanClass,
                beans.sorted((left, right) -> left.getUuid().compareTo(right.getUuid())));
    }

    public static <B extends Identifiable> void saveBean(
            SaveTransaction transaction, Path csv, B bean) throws IOException {
        save(transaction, csv, (Class<B>) bean.getClass(), Stream.of(bean));
    }

    public static <B extends Identifiable> void save(
            SaveTransaction transaction, Path path, Class<B> clazz, Stream<B> entries) throws IOException {
        Path directory = path.getParent();
        Path tempFile = Files.createTempFile(directory, null, null);
        transaction.addJob(path, tempFile);
        AtomicBoolean found = new AtomicBoolean(false);
        try (BeanWriter<B> writer = BeanWriter.forPath(clazz, tempFile)) {
            Optional<IOException> exception = entries.sequential()
                    .map(bean -> {
                        try {
                            writer.write(bean);
                            found.set(true);
                            return null;
                        } catch (IOException ex) {
                            return ex;
                        }
                    })
                    .filter(ex -> ex != null)
                    .findAny();
            if (exception.isPresent()) {
                throw exception.get();
            }
            if (!found.get()) {
                // Deleting the temp file will cause the real file to be deleted
                Files.deleteIfExists(tempFile);
            }
        }
    }
}

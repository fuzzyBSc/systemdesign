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

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;

/**
 * A crude two-phase commit. It doesn't guarantee atomicity but will generally
 * achieve it. Importantly failure to write out some files will cause the entire
 * set to be left uncommitted avoiding inconsistency in the general case. Use in
 * conjunction with a configuration management system to ensure that write
 * contention is negligible.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class SaveTransaction implements Closeable {

    private static final class Job {

        public Job(Path realFile, Path tempFile) {
            this.realFile = realFile;
            this.tempFile = tempFile;
        }

        private final Path realFile;
        private final Path tempFile;
    }
    private final List<Job> jobs = new ArrayList<>();

    public void addJob(Path realFile, Path tempFile) {
        jobs.add(new Job(realFile, tempFile));
    }

    public void commit() throws IOException {
        for (Job job : jobs) {
            if (FileUtils.contentEquals(job.tempFile.toFile(), job.realFile.toFile())) {
                // Don't overwrite if identical. Delete temp file in close.
            } else {
                Files.move(job.tempFile, job.realFile, REPLACE_EXISTING, ATOMIC_MOVE);
            }
        }
    }

    @Override
    public void close() throws IOException {
        for (Job job : jobs) {
            Files.deleteIfExists(job.tempFile);
        }
    }

}

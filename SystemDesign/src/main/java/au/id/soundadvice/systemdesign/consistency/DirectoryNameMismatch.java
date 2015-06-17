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
package au.id.soundadvice.systemdesign.consistency;

import au.id.soundadvice.systemdesign.baselines.EditState;
import au.id.soundadvice.systemdesign.files.Directory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class DirectoryNameMismatch implements ProblemFactory {

    private static final Logger LOG = Logger.getLogger(DirectoryNameMismatch.class.getName());

    private class RenameDir implements Solution {

        private final Path from;
        private final Path to;

        private RenameDir(Path from, Path to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public String getDescription() {
            return "Rename";
        }

        @Override
        public void solve(EditState edit) {
            try {
                edit.rename(from, to);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, null, ex);
            }
        }

    }

    @Override
    public Collection<Problem> getProblems(EditState edit) {
        Directory dir = edit.getCurrentDirectory();
        if (dir != null) {
            Path path = dir.getPath();
            if (Files.isDirectory(path)) {
                String lastSegment = path.getFileName().toString();
                String identity = edit.getUndo().get().getAllocated().getIdentity().toString();
                if (!"".equals(identity) && !lastSegment.equals(identity)) {
                    Path renameTo = path.getParent().resolve(identity);
                    if (!Files.exists(renameTo)) {
                        return Collections.singleton(
                                new Problem("Directory name mismatch", Collections.singleton(
                                                new RenameDir(path, renameTo))));
                    }
                }
            }
        }
        return Collections.emptyList();
    }

}
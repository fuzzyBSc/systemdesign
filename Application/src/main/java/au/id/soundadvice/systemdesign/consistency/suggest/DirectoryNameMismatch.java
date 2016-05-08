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
package au.id.soundadvice.systemdesign.consistency.suggest;

import au.id.soundadvice.systemdesign.consistency.EditProblem;
import au.id.soundadvice.systemdesign.consistency.EditSolution;
import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.moduleapi.storage.RecordStorage;
import au.id.soundadvice.systemdesign.physical.Identity;
import au.id.soundadvice.systemdesign.storage.FileStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class DirectoryNameMismatch {

    private static final Logger LOG = Logger.getLogger(DirectoryNameMismatch.class.getName());

    private static class RenameDir implements EditSolution {

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
        public void solve(EditState edit, String now) {
            try {
                edit.renameDirectory(from, to);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, null, ex);
            }
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

    }

    public static Stream<EditProblem> getProblems(EditState edit) {
        Optional<RecordStorage> dir = edit.getStorage();
        if (dir.isPresent() && dir.get() instanceof FileStorage) {
            FileStorage storage = (FileStorage) dir.get();
            Path path = storage.getDirectoryPath();
            if (Files.isDirectory(path)) {
                String lastSegment = path.getFileName().toString();
                String identity = Identity.get(edit.getChild()).toString();
                if (!"".equals(identity) && !lastSegment.equals(identity)) {
                    Path renameTo = path.getParent().resolve(identity);
                    if (!Files.exists(renameTo)) {
                        return Stream.of(new EditProblem("Directory name mismatch", true,
                                Stream.of(new RenameDir(path, renameTo))));
                    }
                }
            }
        }
        return Stream.empty();
    }

}

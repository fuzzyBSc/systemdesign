/*
 * To change this license header, choose License Headers in Project Properties.
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
package au.id.soundadvice.systemdesign.versioning;

import au.id.soundadvice.systemdesign.versioning.jgit.GitVersionControl;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public interface VersionControl extends Closeable {

    public static VersionControl forPath(Path path) {
        try {
            return new GitVersionControl(path);
        } catch (IOException | IllegalArgumentException ex) {
            return new NullVersionControl();
        }
    }

    public Stream<VersionInfo> getBranches() throws IOException;

    public Stream<VersionInfo> getVersions() throws IOException;

    public void changed(Path filename) throws IOException;

    public boolean canCommit();

    public void commit() throws IOException;

    public void renameDirectory(Path from, Path to) throws IOException;

}

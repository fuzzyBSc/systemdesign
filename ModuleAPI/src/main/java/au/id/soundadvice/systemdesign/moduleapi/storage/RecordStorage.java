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
package au.id.soundadvice.systemdesign.moduleapi.storage;

import au.id.soundadvice.systemdesign.moduleapi.entity.Baseline;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public interface RecordStorage {

    /**
     * Load the whole baseline.
     *
     * @param factory The factory to use to create RecordType objects
     * @param label If empty, load the current baseline. If nonempty, load the
     * nominated branch or tag.
     * @return The baseline
     */
    public Baseline loadBaseline(RecordTypeFactory factory, Optional<String> label) throws IOException;

    public Stream<VersionInfo> getBranches() throws IOException;

    public Stream<VersionInfo> getVersions() throws IOException;

    /**
     * Save the whole baseline.
     *
     * @param relations The baseline to store
     */
    public void saveBaseline(Baseline relations) throws IOException;

    Optional<RecordStorage> getParent();

    Optional<RecordStorage> getChild(String identifier) throws IOException;

    public boolean identityFileExists() throws IOException;
}

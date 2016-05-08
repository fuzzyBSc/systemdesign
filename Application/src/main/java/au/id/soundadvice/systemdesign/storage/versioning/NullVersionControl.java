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
package au.id.soundadvice.systemdesign.storage.versioning;

import au.id.soundadvice.systemdesign.moduleapi.storage.VersionInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class NullVersionControl implements VersionControl {

    @Override
    public Stream<VersionInfo> getBranches() {
        return Stream.empty();
    }

    @Override
    public Stream<VersionInfo> getVersions() {
        return Stream.empty();
    }

    @Override
    public void changed(Path filename) {
    }

    @Override
    public boolean canCommit() {
        return false;
    }

    @Override
    public void commit(String message) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public void renameDirectory(Path from, Path to) throws IOException {
        Files.move(from, to);
    }

    @Override
    public Stream<String> listFiles(
            IdentityValidator identity, Optional<String> versionInfo) throws IOException {
        Path path = identity.getDirectoryPath();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            return StreamSupport.stream(stream.spliterator(), true)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList())
                    .stream();
        }
    }

    @Override
    public boolean exists(
            IdentityValidator identity, String filename, Optional<String> versionInfo) {
        Path path = identity.getDirectoryPath().resolve(filename);
        return Files.exists(path);
    }

    @Override
    public BufferedReader getBufferedReader(
            IdentityValidator identity, String filename, Optional<String> versionInfo) throws IOException {
        Path path = identity.getDirectoryPath().resolve(filename);
        return Files.newBufferedReader(path);
    }

    @Override
    public Optional<VersionInfo> getDefaultBaseline() {
        return Optional.empty();
    }

    @Override
    public boolean isNull() {
        return true;
    }

}

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
package au.id.soundadvice.systemdesign.preferences;

import au.com.bytecode.opencsv.CSVParser;
import au.com.bytecode.opencsv.CSVWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class RecentFiles {

    private static final Logger LOG = Logger.getLogger(RecentFiles.class.getName());

    private static final Preferences PREFERENCES
            = Preferences.userNodeForPackage(RecentFiles.class);

    public static Stream<Path> getRecentFiles() {
        try {
            String raw = PREFERENCES.get("recentFiles", "");
            /*
             * Use non-default constructor to set strictQuotes. This works
             * around a bug in the case that a single field is read with quotes
             * around it, eg "mypath". On decode the second quote is treated as
             * part of the string, so the result is the invalid `mypath"'
             */
            CSVParser parser = new CSVParser(
                    CSVParser.DEFAULT_SEPARATOR,
                    CSVParser.DEFAULT_QUOTE_CHARACTER,
                    CSVParser.DEFAULT_ESCAPE_CHARACTER,
                    true);

            return Stream.of(parser.parseLine(raw))
                    .filter(string -> !string.isEmpty())
                    .map(Paths::get)
                    .filter(Files::exists);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, null, ex);
            return Stream.empty();
        }
    }

    public static void addRecentFile(Path path) {
        try (StringWriter writer = new StringWriter()) {
            {
                String[] paths
                        = Stream.concat(
                                Stream.of(path.toAbsolutePath()),
                                getRecentFiles())
                        .map(Path::toString)
                        .distinct()
                        .limit(10)
                        .toArray(String[]::new);
                try (CSVWriter csvwriter = new CSVWriter(writer)) {
                    csvwriter.writeNext(paths);
                }
            }
            PREFERENCES.put("recentFiles", writer.toString());
        } catch (IOException ex) {
            LOG.log(Level.WARNING, null, ex);
        }
    }
}

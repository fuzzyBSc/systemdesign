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

import au.com.bytecode.opencsv.CSVReader;
import au.id.soundadvice.systemdesign.versioning.VersionControl;
import au.id.soundadvice.systemdesign.versioning.VersionInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javafx.scene.paint.Color;

/**
 * Read beans of the specified type from a CSV file.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <T> The type to read
 */
public class BeanReader<T> implements Closeable {

    public static <T> Optional<BeanReader<T>> forPath(
            Class<T> clazz, Path path,
            VersionControl versionControl, Optional<VersionInfo> version) throws IOException {
        Optional<CSVReader> csvreader = versionControl.getBufferedReader(path, version)
                .map(CSVReader::new);
        if (csvreader.isPresent()) {
            try {
                return Optional.of(new BeanReader<>(clazz, csvreader.get()));
            } catch (IOException ex) {
                try {
                    csvreader.get().close();
                } catch (IOException ex2) {
                    ex.addSuppressed(ex2);
                }
                throw ex;
            }
        } else {
            return Optional.empty();
        }
    }

    // Must keep pinnned here as PropertyEditorManager only holds weak references
    private static final Class<?> uuidEditorClass = UUIDEditor.class;
    private static final Class<?> colorEditorClass = ColorEditor.class;
    private static final Class<?> bigDecimalEditorClass = BigDecimalEditor.class;

    static {
        PropertyEditorManager.registerEditor(UUID.class, uuidEditorClass);
        PropertyEditorManager.registerEditor(Color.class, colorEditorClass);
        PropertyEditorManager.registerEditor(BigDecimal.class, bigDecimalEditorClass);
    }

    public BeanReader(Class<T> clazz, CSVReader reader) throws IOException {
        try {
            this.clazz = clazz;
            this.reader = reader;
            Map<String, PropertyDescriptor> properties = new HashMap<>();
            for (PropertyDescriptor descriptor
                    : Introspector.getBeanInfo(clazz).getPropertyDescriptors()) {
                properties.put(descriptor.getName(), descriptor);
            }
            String[] headerNames = reader.readNext();
            if (headerNames == null) {
                this.header = Collections.emptyList();
            } else {
                List<StringSetter<T>> tmpHeader = new ArrayList<>(headerNames.length);
                for (String property : headerNames) {
                    tmpHeader.add(new StringSetter<>(
                            Optional.ofNullable(properties.get(property))));
                }
                this.header = Collections.unmodifiableList(tmpHeader);
            }
        } catch (IntrospectionException ex) {
            throw new IOException(ex);
        }
    }

    public Optional<T> read() throws IOException {
        try {
            String[] line = reader.readNext();
            if (line == null) {
                return Optional.empty();
            }
            T result = clazz.newInstance();
            for (int ii = 0; ii < header.size(); ++ii) {
                header.get(ii).setProperty(result, line[ii]);
            }
            return Optional.of(result);
        } catch (InstantiationException |
                IllegalAccessException |
                ArrayIndexOutOfBoundsException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    private static final class StringSetter<T> {

        public StringSetter(Optional<PropertyDescriptor> optionalProperty) {
            this.editor = optionalProperty.map(
                    property -> PropertyEditorManager.findEditor(
                            property.getPropertyType()));
            this.setter = optionalProperty.map(
                    property -> property.getWriteMethod());
        }

        public void setProperty(T bean, String value) throws IOException {
            if (setter.isPresent() && editor.isPresent()) {
                try {
                    editor.get().setAsText(value);
                    setter.get().invoke(bean, editor.get().getValue());
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    throw new IOException(ex);
                }
            }
        }

        private final Optional<PropertyEditor> editor;
        private final Optional<Method> setter;
    }

    private final Class<T> clazz;
    private final CSVReader reader;
    private final List<StringSetter<T>> header;

    public Class<T> getClazz() {
        return clazz;
    }

}

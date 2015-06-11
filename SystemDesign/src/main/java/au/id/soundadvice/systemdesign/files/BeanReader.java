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
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Read beans of the specified type from a CSV file.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <T> The type to read
 */
public class BeanReader<T> implements Closeable {

    public static <T> BeanReader<T> forPath(Class<T> clazz, Path path) throws IOException {
        BufferedReader reader = Files.newBufferedReader(path);
        CSVReader csvreader = new CSVReader(reader);
        try {
            return new BeanReader<>(clazz, csvreader);
        } catch (IOException ex) {
            try {
                csvreader.close();
            } catch (IOException ex2) {
                ex.addSuppressed(ex2);
            }
            throw ex;
        }
    }

    // Must keep pinnned here as PropertyEditorManager only holds weak references
    private static final Class<?> uuidEditorClass = UUIDEditor.class;

    static {
        PropertyEditorManager.registerEditor(UUID.class, uuidEditorClass);
//        ConvertUtils.register(new EnumConverter(FlowDirection.class), FlowDirection.class);
//        ConvertUtils.register(new EnumConverter(DictionaryCategory.class), DictionaryCategory.class);
//        ConvertUtils.register(new EnumConverter(RequirementType.class), RequirementType.class);
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
                    tmpHeader.add(new StringSetter<>(properties.get(property)));
                }
                this.header = Collections.unmodifiableList(tmpHeader);
            }
        } catch (IntrospectionException ex) {
            throw new IOException(ex);
        }
    }

    @Nullable
    public T read() throws IOException {
        try {
            String[] line = reader.readNext();
            if (line == null) {
                return null;
            }
            T result = clazz.newInstance();
            for (int ii = 0; ii < header.size(); ++ii) {
                header.get(ii).setProperty(result, line[ii]);
            }
            return result;
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

        public StringSetter(@Nullable PropertyDescriptor property) {
            if (property == null) {
                // No-op
                this.editor = null;
                this.setter = null;
            } else {
                this.editor = PropertyEditorManager.findEditor(
                        property.getPropertyType());
                this.setter = property.getWriteMethod();
            }
        }

        public void setProperty(T bean, String value) throws IOException {
            if (setter == null || editor == null) {
                return;
            }
            try {
                editor.setAsText(value);
                setter.invoke(bean, editor.getValue());
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw new IOException(ex);
            }
        }

        @Nullable
        private final PropertyEditor editor;
        @Nullable
        private final Method setter;
    }

    private final Class<T> clazz;
    private final CSVReader reader;
    private final List<StringSetter<T>> header;

    public Class<T> getClazz() {
        return clazz;
    }

}

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

import au.com.bytecode.opencsv.CSVWriter;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/**
 * Read beans of the specified type to a CSV file.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <T> The type of object to write
 */
public class BeanWriter<T> implements Closeable {

    public static <T> BeanWriter<T> forPath(Class<T> clazz, Path path) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(path);
        CSVWriter csvwriter = new CSVWriter(writer);
        try {
            return new BeanWriter<>(clazz, csvwriter);
        } catch (IOException ex) {
            try {
                csvwriter.close();
            } catch (IOException ex2) {
                ex.addSuppressed(ex2);
            }
            throw ex;
        }
    }
    private final CSVWriter writer;
    private final SortedMap<String, Method> header;
    private final AtomicBoolean headerPrinted = new AtomicBoolean(false);

    public BeanWriter(Class<T> clazz, CSVWriter writer) throws IOException {
        try {
            this.writer = writer;
            BeanInfo info = Introspector.getBeanInfo(clazz);
            SortedMap<String, Method> tmpHeader = new TreeMap<>();
            for (PropertyDescriptor property : info.getPropertyDescriptors()) {
                // Only save properties with both a setter and getter
                if (property.getReadMethod() != null && property.getWriteMethod() != null) {
                    tmpHeader.put(property.getName(), property.getReadMethod());
                }
            }
            this.header = Collections.unmodifiableSortedMap(tmpHeader);
        } catch (IntrospectionException ex) {
            throw new IOException(ex);
        }
    }

    @Nullable
    public void write(T instance) throws IOException {
        try {
            if (headerPrinted.compareAndSet(false, true)) {
                writer.writeNext(header.keySet().toArray(new String[0]));
            }
            List<String> csvline = new ArrayList<>(header.size());
            for (Method getter : header.values()) {
                Object value = getter.invoke(instance);
                if (value == null) {
                    csvline.add("");
                } else {
                    csvline.add(value.toString());
                }
            }
            writer.writeNext(csvline.toArray(new String[0]));
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}

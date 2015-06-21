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
package au.id.soundadvice.systemdesign.relation;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Find reference properties by introspection to hopefully reduce sources of
 * error during maintenance.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <F> The type this reference is being made from
 */
public class ReferenceFinder<F extends Relation> {

    private static final Logger LOG = Logger.getLogger(ReferenceFinder.class.getName());

    public ReferenceFinder(Class<F> fromType) {
        List<Method> tmpMethods = new ArrayList<>();
        try {
            BeanInfo info = Introspector.getBeanInfo(fromType);
            for (PropertyDescriptor property : info.getPropertyDescriptors()) {
                if (Reference.class.isAssignableFrom(property.getPropertyType())) {
                    tmpMethods.add(property.getReadMethod());
                }
            }
        } catch (IntrospectionException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        this.methods = Collections.unmodifiableList(tmpMethods);
    }

    private final List<Method> methods;

    public Stream<Reference> getReferences(F instance) {
        return methods.stream()
                .map(method -> {
                    try {
                        return (Reference) method.invoke(instance);
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                        IOException ex2 = new IOException(ex);
                        // Smuggle the exception of this scope
                        throw new UncheckedIOException(ex2);
                    }
                });
    }

}

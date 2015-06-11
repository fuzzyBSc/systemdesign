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

import java.util.UUID;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <F> The type this is a reference from
 * @param <T> The type this is a reference to
 */
public class Reference<F, T extends Relation> {

    @Override
    public String toString() {
        return to.toString();
    }

    public F getFrom() {
        return from;
    }

    public ReferenceTarget<T> getTo() {
        return to;
    }

    public UUID getUuid() {
        return to.getKey();
    }

    public Reference(F from, UUID to, Class<T> toType) {
        this.from = from;
        this.to = new ReferenceTarget<>(to, toType);
    }

    public Reference(F from, ReferenceTarget<T> to) {
        this.from = from;
        this.to = to;
    }

    public T getTarget(RelationContext context) {
        return context.get(to.getKey(), to.getType());
    }

    private final F from;
    private final ReferenceTarget<T> to;
}

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
package au.id.soundadvice.systemdesign.fxml;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class UniqueName implements Collector<String, AtomicInteger, String> {

    public UniqueName(String prefix) {
        this.prefix = prefix;
    }

    private final String prefix;

    @Override
    public Supplier<AtomicInteger> supplier() {
        return AtomicInteger::new;
    }

    @Override
    public BiConsumer<AtomicInteger, String> accumulator() {
        return (index, text) -> {
            if (text.startsWith(prefix)) {
                int suffix;
                if (text.length() == prefix.length()) {
                    // Actually equal
                    suffix = 1;
                } else if (text.length() > prefix.length() + 1
                        && text.charAt(prefix.length()) == ' ') {
                    try {
                        suffix = Integer.parseInt(text.substring(prefix.length() + 1));
                    } catch (NumberFormatException ex) {
                        suffix = 0;
                    }
                } else {
                    suffix = 0;
                }
                index.getAndAccumulate(suffix, Math::max);
            } else {
                // No collision
            }
        };
    }

    @Override
    public BinaryOperator<AtomicInteger> combiner() {
        return (left, right) -> {
            left.getAndAccumulate(right.get(), Math::max);
            return left;
        };
    }

    @Override
    public Function<AtomicInteger, String> finisher() {
        return (index) -> {
            int count = index.incrementAndGet();
            if (count == 1) {
                return prefix;
            } else {
                return prefix + " " + count;
            }
        };
    }

    @Override
    public Set<Characteristics> characteristics() {
        return EnumSet.of(Characteristics.CONCURRENT, Characteristics.UNORDERED);
    }

}

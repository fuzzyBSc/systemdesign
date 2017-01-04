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
package au.id.soundadvice.systemdesign.fxml.drawing;

import au.id.soundadvice.systemdesign.moduleapi.entity.ConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.entity.Direction;
import au.id.soundadvice.systemdesign.moduleapi.entity.Identifiable;
import au.id.soundadvice.systemdesign.moduleapi.drawing.DrawingConnector;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <S> A description of the state of the drawing
 */
public interface DrawingOf<S> {

    public void setState(S state);

    public void start();

    public void stop();

    public static <I extends Identifiable, O extends DrawingOf<I>> void updateElements(
            Stream<I> inputStream,
            Map<String, O> current,
            Function<I, O> supplier) {
        Set<String> toDelete = new HashSet<>(current.keySet());
        inputStream.sequential().forEach(input -> {
            Optional<O> existing = Optional.ofNullable(
                    current.get(input.getIdentifier()));
            if (existing.isPresent()) {
                existing.get().setState(input);
                toDelete.remove(input.getIdentifier());
            } else {
                O output = supplier.apply(input);
                output.setState(input);
                output.start();
                current.put(input.getIdentifier(), output);
            }
        });
        toDelete.stream().sequential().forEach((identifier) -> {
            O existing = current.get(identifier);
            existing.stop();
            current.remove(identifier);
        });
    }

    public static <O extends DrawingOf<List<DrawingConnector>>> void updateScopes(
            Stream<DrawingConnector> inputStream,
            Map<ConnectionScope, O> current,
            BiFunction<ConnectionScope, List<DrawingConnector>, O> supplier) {
        Set<ConnectionScope> toDelete = new HashSet<>(current.keySet());
        Map<ConnectionScope, List<DrawingConnector>> byScope = inputStream
                .collect(Collectors.groupingBy(
                        connector -> connector.getScope().setDirection(Direction.None)));
        byScope.forEach((scope, input) -> {
            Optional<O> existing = Optional.ofNullable(current.get(scope));
            if (existing.isPresent()) {
                existing.get().setState(input);
                toDelete.remove(scope);
            } else {
                O output = supplier.apply(scope, input);
                output.setState(input);
                output.start();
                current.put(scope, output);
            }
        });
        toDelete.stream().sequential().forEach((scope) -> {
            O existing = current.get(scope);
            existing.stop();
            current.remove(scope);
        });
    }
}

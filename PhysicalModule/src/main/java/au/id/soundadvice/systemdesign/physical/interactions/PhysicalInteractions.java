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
package au.id.soundadvice.systemdesign.physical.interactions;

import au.id.soundadvice.systemdesign.physical.entity.Item;
import au.id.soundadvice.systemdesign.physical.entity.IDPath;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.RecordConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Direction;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.interaction.InteractionContext;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import au.id.soundadvice.systemdesign.moduleapi.util.UniqueName;
import au.id.soundadvice.systemdesign.physical.entity.Interface;
import static au.id.soundadvice.systemdesign.physical.entity.Item.item;
import au.id.soundadvice.systemdesign.physical.entity.ItemView;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.util.Pair;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class PhysicalInteractions {

    private String getUniqueShortName(Baseline baseline) {
        Set<String> usedNames = Item.find(baseline)
                .filter(item -> !item.isExternal())
                .map(Record::getShortName)
                .collect(Collectors.toSet());
        return IntStream.range(1, Integer.MAX_VALUE)
                .mapToObj(Integer::toString)
                .filter(candidate -> !usedNames.contains(candidate))
                .findFirst()
                .get();
    }

    public Optional<Record> createItem(InteractionContext context, Point2D origin) {
        AtomicReference<Record> result = new AtomicReference<>();
        String defaultName = Item.find(context.getChild()).parallel()
                .filter(item -> !item.isExternal())
                .map(Record::getLongName)
                .collect(new UniqueName("New Item"));
        Optional<String> longName = context.textInput("New Item", "Enter name for item", defaultName);
        if (longName.isPresent()) {
            String now = ISO8601.now();
            context.updateState(state -> {
                String shortName = getUniqueShortName(state.getChild());
                Pair<WhyHowPair<Baseline>, Record> itemResult = Item.create(
                        state, now, shortName, longName.get());
                result.set(itemResult.getValue());
                Pair<Baseline, Record> viewResult = ItemView.create(
                        itemResult.getKey().getChild(), now, itemResult.getValue(), origin);
                return itemResult.getKey().setChild(viewResult.getKey());
            });
        }
        return Optional.ofNullable(result.get());
    }

    public void renumberItem(InteractionContext context, Record sample) {
        // User interaction - read only
        if (sample.isExternal()) {
            return;
        }
        Optional<String> result = context.textInput("Renumber Item", "Enter new item number", sample.getShortName());
        if (result.isPresent()) {
            String now = ISO8601.now();
            String path = IDPath.valueOfSegment(result.get()).toString();
            context.updateChild(child -> {
                Optional<Record> item = child.get(sample);
                if (item.isPresent()) {
                    boolean isUnique = Item.find(child).parallel()
                            .map(Record::getShortName)
                            .noneMatch(existing -> path.equals(existing));
                    if (isUnique) {
                        return child.add(item.get().asBuilder()
                                .setShortName(path)
                                .build(now));
                    } else {
                        return child;
                    }
                } else {
                    return child;
                }
            });
        }
    }

    public void setItemName(InteractionContext context, Record sample) {
        if (sample.isExternal()) {
            return;
        }

        Optional<String> result = context.textInput("Rename Item", "Enter name for item", sample.getLongName());
        if (result.isPresent()) {
            String now = ISO8601.now();
            String name = result.get();
            context.updateChild(child -> {
                Optional<Record> item = child.get(sample);
                if (!item.isPresent()) {
                    return child;
                }
                boolean isUnique = Item.find(child).parallel()
                        .map(Record::getLongName)
                        .noneMatch((existing) -> name.equals(existing));
                if (isUnique) {
                    return child.add(item.get().asBuilder()
                            .setLongName(name)
                            .build(now));
                } else {
                    return child;
                }
            });
        }
    }

    public void setItemColor(InteractionContext context, Record sample) {
        if (sample.isExternal()) {
            return;
        }

        Optional<Color> result = context.colorInput(
                "Item Color",
                "Select color for " + item.getDisplayName(sample),
                sample.getColor());
        if (result.isPresent()) {
            String now = ISO8601.now();
            Color color = result.get();
            context.updateChild(child -> {
                Optional<Record> item = child.get(sample);
                if (!item.isPresent()) {
                    return child;
                }
                return child.add(item.get().asBuilder()
                        .setColor(color)
                        .build(now));
            });
        }
    }

    void addInterface(InteractionContext context, Record leftSample, Record rightSample) {
        if (!leftSample.isExternal() || !rightSample.isExternal()) {
            // One end has to be internal
            String now = ISO8601.now();
            context.updateState(state -> {
                RecordConnectionScope scope = RecordConnectionScope.resolve(
                        leftSample, rightSample, Direction.None);
                return Interface.connect(state, now, scope).getKey();
            });
        }
    }

}

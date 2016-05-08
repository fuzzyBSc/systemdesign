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
package au.id.soundadvice.systemdesign.moduleapi.entity;

import au.id.soundadvice.systemdesign.moduleapi.Direction;
import au.id.soundadvice.systemdesign.moduleapi.Identifiable;
import au.id.soundadvice.systemdesign.moduleapi.ConnectionScope;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.scene.paint.Color;
import javax.annotation.CheckReturnValue;
import javafx.geometry.Point2D;
import static au.id.soundadvice.systemdesign.moduleapi.util.ToBoolean.toBoolean;

/**
 * An immutable identifiable fundamental unit of storage and modeling.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Record implements Identifiable {

    private final RecordType type;
    private final SortedMap<String, String> fields;

    public boolean is(Record other) {
        return getIdentifier().equals(other.getIdentifier());
    }

    public static class Builder {

        private Builder(Optional<Record> was, RecordType type) {
            this.was = was;
            this.type = type;
            if (was.isPresent()) {
                this.fields = Optional.empty();
            } else {
                // Create the map immediately
                this.fields = Optional.of(new TreeMap<>());
            }
        }

        @CheckReturnValue
        public Builder setIdentifier(String value) {
            return put(Fields.identifier.name(), value);
        }

        @CheckReturnValue
        public Builder newIdentifier() {
            return setIdentifier(UUID.randomUUID().toString());
        }

        @CheckReturnValue
        public Builder setTrace(Optional<Record> parent) {
            if (parent.isPresent()) {
                return setTrace(parent.get());
            } else {
                return removeTrace();
            }
        }

        @CheckReturnValue
        public Builder setTrace(Record parent) {
            return put(Fields.trace.name(), parent.getTrace().orElse(""));
        }

        @CheckReturnValue
        public Builder removeTrace() {
            return put(Fields.trace.name(), "");
        }

        @CheckReturnValue
        public Builder setShortName(String value) {
            return put(Fields.shortName.name(), value);
        }

        @CheckReturnValue
        public Builder setLongName(String value) {
            return put(Fields.longName.name(), value);
        }

        @CheckReturnValue
        public Builder setDescription(String value) {
            return put(Fields.desc.name(), value);
        }

        @CheckReturnValue
        public Builder setConnectionScope(RecordConnectionScope value) {
            return put(Fields.refLeft.name(), value.getLeft().getIdentifier())
                    .put(Fields.refRight.name(), value.getRight().getIdentifier())
                    .put(Fields.direction.name(), value.getDirection().name());
        }

        public Builder setViewOf(Record value) {
            return put(Fields.refViewOf.name(), value.getIdentifier());
        }

        public Builder setContainer(Record value) {
            return put(Fields.refContainer.name(), value.getIdentifier());
        }

        public Builder setSubtype(Record value) {
            return put(Fields.refSubtype.name(), value.getIdentifier());
        }

        @CheckReturnValue
        public Builder setExternal(boolean value) {
            return put(Fields.external.name(), Boolean.toString(value));
        }

        public Builder setOrigin(Point2D origin) {
            return put(Fields.originX.name(), Integer.toString((int) origin.getX()))
                    .put(Fields.originY.name(), Integer.toString((int) origin.getY()));
        }

        @CheckReturnValue
        public Builder setColor(Color value) {
            return put(Fields.color.name(), value.toString());
        }

        public Builder putAll(Map<String, String> newFields) {
            initFields();
            fields.get().putAll(newFields);
            return this;
        }

        @CheckReturnValue
        public Builder put(String key, String value) {
            String oldValue = fields.map(map -> map.getOrDefault(key, ""))
                    .orElseGet(() -> was.get().get(key).orElse(""));
            if (!value.equals(oldValue)) {
                initFields();
                fields.get().put(key, value);
            }
            return this;
        }

        private void initFields() {
            if (!fields.isPresent()) {
                if (was.isPresent()) {
                    fields = Optional.of(new TreeMap<>(was.get().getFields()));
                } else {
                    fields = Optional.of(new TreeMap<>());
                }
            }
        }

        @CheckReturnValue
        public Builder redirectReferences(String toIdentifier, String fromIdentifier) {
            SortedMap<String, String> referenceFields = fields.orElse(was.get().getFields());
            Map<String, String> modifications = getReferenceFieldsImpl(referenceFields)
                    .filter(entry -> fromIdentifier.equals(entry.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> toIdentifier));
            return putAll(modifications);
        }

        @CheckReturnValue
        public Record build(String now) {
            if (was.isPresent() && !fields.isPresent()) {
                // Nothing changed
                return was.get();
            } else {
                assert fields.isPresent();
                SortedMap<String, String> resultFields = fields.get();
                String id = resultFields.get(Fields.identifier.name());
                if (id == null || id.isEmpty()) {
                    resultFields.put(Fields.identifier.name(), UUID.randomUUID().toString());
                }
                resultFields.put(Fields.lastChange.name(), now);
                Record result = new Record(type, Collections.unmodifiableSortedMap(resultFields));
                fields = Optional.empty();
                return result;
            }
        }
        private final Optional<Record> was;
        private final RecordType type;
        private Optional<SortedMap<String, String>> fields;
    }

    @Override
    public String getIdentifier() {
        return get(Fields.identifier.name()).get();
    }

    public String getLastChange() {
        return get(Fields.lastChange.name()).get();
    }

    public Optional<String> getTrace() {
        return get(Fields.trace.name());
    }

    public RecordType getType() {
        return this.type;
    }

    public String getShortName() {
        return get(Fields.shortName.name()).orElse("");
    }

    public String getLongName() {
        return get(Fields.longName.name()).orElse("");
    }

    @Override
    public String toString() {
        String shortName = getShortName();
        String longName = getLongName();
        if (shortName.isEmpty()) {
            if (longName.isEmpty()) {
                return getIdentifier();
            } else {
                return longName;
            }
        } else if (longName.isEmpty()) {
            return shortName;
        } else {
            return longName + " " + shortName;
        }
    }

    public String getDescription() {
        return get(Fields.desc.name()).orElse("");
    }

    public ConnectionScope getConnectionScope() {
        String left = get(Fields.refLeft.name()).orElse("");
        String right = get(Fields.refRight.name()).orElse("");
        String direction = get(Fields.direction.name()).orElse(Direction.None.name());
        return new ConnectionScope(
                left, right, Direction.valueOf(direction));
    }

    public Optional<String> getViewOf() {
        return get(Fields.refViewOf.name());
    }

    public Optional<String> getContainer() {
        return get(Fields.refContainer.name());
    }

    public Optional<String> getSubtype() {
        return get(Fields.refSubtype.name());
    }

    public boolean isExternal() {
        return toBoolean(get(Fields.external.name()));
    }

    public Point2D getOrigin() {
        try {
            return new Point2D(
                    Integer.valueOf(get(Fields.originX.name()).orElse("0")),
                    Integer.valueOf(get(Fields.originY.name()).orElse("0"))
            );
        } catch (NumberFormatException ex) {
            return Point2D.ZERO;
        }
    }

    public Color getColor() {
        try {
            return Color.valueOf(get(Fields.color.name()).orElse(""));
        } catch (IllegalArgumentException ex) {
            return Color.LIGHTYELLOW;
        }
    }

    public Optional<String> get(String key) {
        String result = fields.get(key);
        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result);
    }

    public SortedMap<String, String> getFields() {
        return fields;
    }

    private static Stream<Map.Entry<String, String>> getReferenceFieldsImpl(Map<String, String> source) {
        return source.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("ref"));
    }

    public Stream<Map.Entry<String, String>> getReferenceFields() {
        return getReferenceFieldsImpl(fields);
    }

    public static Builder create(RecordType type) {
        return new Builder(Optional.empty(), type);
    }

    public Builder asBuilder() {
        return new Builder(Optional.of(this), type);
    }

    public static Record newerOf(Record left, Record right) {
        if (left.getLastChange().compareTo(right.getLastChange()) < 0) {
            return left;
        } else {
            return right;
        }
    }

    public static Record load(RecordType type, Map<String, String> fields) {
        SortedMap<String, String> newFields = new TreeMap<>(fields);
        return new Record(type, Collections.unmodifiableSortedMap(newFields));
    }

    public static Record load(RecordType type, Stream<Map.Entry<String, String>> fields) {
        SortedMap<String, String> newValues = fields.collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (u, v) -> {
                    throw new IllegalStateException(String.format("Duplicate key %s", u));
                },
                TreeMap<String, String>::new
        ));
        return new Record(type, Collections.unmodifiableSortedMap(newValues));
    }

    private Record(RecordType type, SortedMap<String, String> unmodifiableFields) {
        this.type = type;
        this.fields = unmodifiableFields;
    }
}

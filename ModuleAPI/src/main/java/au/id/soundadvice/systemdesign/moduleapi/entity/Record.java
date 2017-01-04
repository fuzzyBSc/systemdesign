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

import au.id.soundadvice.systemdesign.moduleapi.collection.RecordConnectionScope;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.scene.paint.Color;
import javax.annotation.CheckReturnValue;
import javafx.geometry.Point2D;
import com.sun.istack.internal.Nullable;
import static au.id.soundadvice.systemdesign.moduleapi.util.ToBoolean.toBoolean;

/**
 * An immutable identifiable fundamental unit of storage and modeling.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Record implements Identifiable {

    private final Table type;
    private final RecordID identifier;
    private final SortedMap<String, String> fields;
    private final SortedMap<String, RecordID> refs;

    public boolean is(Record other) {
        return identifier.equals(other.identifier);
    }

    public static class Builder {

        private Builder(Optional<Record> was, Table type) {
            this.was = was;
            this.type = type;
            this.identifier = was
                    .map(Record::getIdentifier)
                    .orElseGet(RecordID::create);
            this.fields = new TreeMap<>();
            this.refs = new TreeMap<>();
        }

        @CheckReturnValue
        public Builder setIdentifier(RecordID value) {
            this.identifier = value;
            return this;
        }

        @CheckReturnValue
        public Builder newIdentifier() {
            return setIdentifier(RecordID.create());
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
            return put(Fields.trace.name(), parent.getTrace().map(Object::toString).orElse(""));
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
            return putRef(References.left.name(), value.getLeft().getIdentifier())
                    .putRef(References.right.name(), value.getRight().getIdentifier())
                    .put(Fields.direction.name(), value.getDirection().name());
        }

        public Builder setViewOf(Record value) {
            return putRef(References.viewOf.name(), value.getIdentifier());
        }

        public Builder setContainer(Record value) {
            return putRef(References.container.name(), value.getIdentifier());
        }

        public Builder setSubtype(Record value) {
            return putRef(References.subtype.name(), value.getIdentifier());
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

        @CheckReturnValue
        public Builder put(String key, String value) {
            @Nullable
            String oldValue = fields.get(key);
            if (oldValue == null && was.isPresent() && fields.isEmpty()) {
                oldValue = was.get().fields.get(key);
            }
            if (!value.equals(oldValue)) {
                initFields();
                fields.put(key, value);
            }
            return this;
        }

        private void initFields() {
            if (fields.isEmpty() && was.isPresent()) {
                fields.putAll(was.get().fields);
            }
        }

        @CheckReturnValue
        public Builder putRef(String key, RecordID value) {
            @Nullable
            RecordID oldValue = refs.get(key);
            if (oldValue == null && was.isPresent() && refs.isEmpty()) {
                oldValue = was.get().refs.get(key);
            }
            if (!value.equals(oldValue)) {
                initRefs();
                refs.put(key, value);
            }
            return this;
        }

        private void initRefs() {
            if (refs.isEmpty() && was.isPresent()) {
                refs.putAll(was.get().refs);
            }
        }

        @CheckReturnValue
        public Builder redirectReferences(RecordID toIdentifier, RecordID fromIdentifier) {
            initRefs();
            for (Map.Entry<String, RecordID> entry : refs.entrySet()) {
                if (fromIdentifier.equals(entry.getValue())) {
                    entry.setValue(toIdentifier);
                }
            }
            return this;
        }

        @CheckReturnValue
        public Record build(String now) {
            if (was.isPresent() && identifier.equals(was.get().identifier) && fields.isEmpty() && refs.isEmpty()) {
                // Nothing changed
                return was.get();
            } else {
                put(Fields.lastChange.name(), now);
                if (was.isPresent()) {
                    if (fields.isEmpty()) {
                        fields = was.get().fields;
                    }
                    if (refs.isEmpty()) {
                        refs = was.get().refs;
                    }
                }
                Record result = new Record(
                        type, identifier,
                        Collections.unmodifiableSortedMap(fields),
                        Collections.unmodifiableSortedMap(refs));
                fields = null;
                refs = null;
                return result;
            }
        }
        private final Optional<Record> was;
        private final Table type;
        private RecordID identifier;
        private SortedMap<String, String> fields;
        private SortedMap<String, RecordID> refs;
    }

    @Override
    public RecordID getIdentifier() {
        return identifier;
    }

    public String getLastChange() {
        return get(Fields.lastChange.name()).get();
    }

    public Optional<RecordID> getTrace() {
        return get(Fields.trace.name()).flatMap(RecordID::load);
    }

    public Table getType() {
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
                return identifier.toString();
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
        RecordID left = getRef(References.left.name()).get();
        RecordID right = getRef(References.right.name()).get();
        String direction = get(Fields.direction.name()).orElse(Direction.None.name());
        return new ConnectionScope(
                left, right, Direction.valueOf(direction));
    }

    public Optional<RecordID> getViewOf() {
        return getRef(References.viewOf.name());
    }

    public Optional<RecordID> getContainer() {
        return getRef(References.container.name());
    }

    public Optional<RecordID> getSubtype() {
        return getRef(References.subtype.name());
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
        return Optional.ofNullable(fields.get(key));
    }

    public Optional<RecordID> getRef(String key) {
        return Optional.ofNullable(refs.get(key));
    }

    public SortedMap<String, String> getAllFields() {
        SortedMap<String, String> result = new TreeMap<>();
        result.putAll(fields);
        for (Map.Entry<String, RecordID> entry : refs.entrySet()) {
            result.put(References.PREFIX + entry.getKey(), entry.getValue().toString());
        }
        result.put(Identifiable.IDENTIFIER, identifier.toString());
        return result;
    }

    public static Builder create(Table type) {
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

    public static Record load(Table type, Map<String, String> allFields) {
        Optional<RecordID> newIdentifier = Optional.empty();
        SortedMap<String, String> newFields = new TreeMap<>();
        SortedMap<String, RecordID> newRefs = new TreeMap<>();
        for (Map.Entry<String, String> entry : allFields.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.equals(Identifiable.IDENTIFIER)) {
                newIdentifier = RecordID.load(value);
            } else if (key.startsWith(References.PREFIX)) {
                Optional<RecordID> foreignKey = RecordID.load(value);
                if (foreignKey.isPresent()) {
                    newRefs.put(key, foreignKey.get());
                }
            } else if (!value.isEmpty()) {
                newFields.put(key, value);
            }
        }
        return new Record(type, newIdentifier.orElseGet(RecordID::create),
                Collections.unmodifiableSortedMap(newFields),
                Collections.unmodifiableSortedMap(newRefs));
    }

    public static Record load(Table type, Stream<Map.Entry<String, String>> allFields) {
        SortedMap<String, String> allFieldsMap = allFields.collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (u, v) -> {
                    throw new IllegalStateException(String.format("Duplicate key %s", u));
                },
                TreeMap<String, String>::new
        ));
        return load(type, allFieldsMap);
    }

    private Record(
            Table type, RecordID identifier,
            SortedMap<String, String> unmodifiableFields,
            SortedMap<String, RecordID> unmodifiableRefs) {
        this.type = type;
        this.identifier = identifier;
        this.fields = unmodifiableFields;
        this.refs = unmodifiableRefs;
    }
}

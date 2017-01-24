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
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
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
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import static au.id.soundadvice.systemdesign.moduleapi.util.ToBoolean.toBoolean;

/**
 * An immutable identifiable fundamental unit of storage and modeling.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Record implements Identifiable {

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + Objects.hashCode(this.type);
        hash = 79 * hash + Objects.hashCode(this.meta);
        hash = 79 * hash + Objects.hashCode(this.fields);
        hash = 79 * hash + Objects.hashCode(this.refs);
        hash = 79 * hash + Objects.hashCode(this.identifier);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Record other = (Record) obj;
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        if (!Objects.equals(this.meta, other.meta)) {
            return false;
        }
        if (!Objects.equals(this.fields, other.fields)) {
            return false;
        }
        if (!Objects.equals(this.refs, other.refs)) {
            return false;
        }
        if (!Objects.equals(this.identifier, other.identifier)) {
            return false;
        }
        return true;
    }

    private final Table type;
    private final SortedMap<String, String> meta;
    private final SortedMap<String, String> fields;
    private final SortedMap<String, RecordID> refs;

    private final RecordID identifier;

    public boolean is(Record other) {
        return Objects.equals(getIdentifier(), other.getIdentifier());
    }

    public static class Builder {

        private Builder(Optional<Record> was, Table type) {
            this.was = was;
            this.type = type;
            this.meta = Optional.empty();
            this.fields = Optional.empty();
            this.refs = Optional.empty();
        }

        @CheckReturnValue
        public Builder setIdentifier(RecordID value) {
            return put(MetaFields.identifier, value.toString());
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
            return put(Fields.trace, parent.getIdentifier().toString());
        }

        @CheckReturnValue
        public Builder removeTrace() {
            return put(Fields.trace, null);
        }

        @CheckReturnValue
        public Builder removeReferences() {
            this.refs = Optional.of(new TreeMap<>());
            return this;
        }

        @CheckReturnValue
        public Builder setShortName(String value) {
            return put(Fields.shortName, value);
        }

        @CheckReturnValue
        public Builder setLongName(String value) {
            return put(Fields.longName, value);
        }

        @CheckReturnValue
        public Builder setDescription(String value) {
            return put(Fields.desc, value);
        }

        @CheckReturnValue
        public Builder setConnectionScope(RecordConnectionScope value) {
            return put(References.left, value.getLeft().getIdentifier())
                    .put(References.right, value.getRight().getIdentifier())
                    .put(Fields.direction, value.getDirection().name());
        }

        public Builder setViewOf(Record value) {
            return put(References.viewOf, value.getIdentifier());
        }

        public Builder setContainer(Record value) {
            return put(References.container, value.getIdentifier());
        }

        public Builder setSubtype(Record value) {
            return put(References.subtype, value.getIdentifier());
        }

        @CheckReturnValue
        public Builder setExternal(boolean value) {
            return put(Fields.external, Boolean.toString(value));
        }

        public Builder setOrigin(Point2D origin) {
            return put(Fields.originX, Integer.toString((int) origin.getX()))
                    .put(Fields.originY, Integer.toString((int) origin.getY()));
        }

        @CheckReturnValue
        public Builder setColor(Color value) {
            return put(Fields.color, value.toString());
        }

        @CheckReturnValue
        public Builder put(Fields key, @Nullable String value) {
            return putField(key.name(), value);
        }

        @CheckReturnValue
        public Builder putField(String key, String value) {
            putToMap(
                    was.map(Record::getFields), fields, map -> fields = map,
                    key, value);
            return this;
        }

        @CheckReturnValue
        public Builder put(MetaFields key, @Nullable String value) {
            putToMap(
                    was.map(Record::getMeta), meta, map -> meta = map,
                    key.name(), value);
            return this;
        }

        @CheckReturnValue
        public Builder put(References key, @Nullable RecordID value) {
            putToMap(
                    was.map(Record::getReferences), refs, map -> refs = map,
                    key.name(), value);
            return this;
        }

        private <V> SortedMap<String, V> initMap(
                Optional<SortedMap<String, V>> wasMap,
                Optional<SortedMap<String, V>> isMap,
                Consumer<Optional<SortedMap<String, V>>> setter) {
            SortedMap<String, V> result;
            if (isMap.isPresent()) {
                result = isMap.get();
            } else if (wasMap.isPresent()) {
                result = new TreeMap<>(wasMap.get());
                setter.accept(Optional.of(result));
            } else {
                result = new TreeMap<>();
                setter.accept(Optional.of(result));
            }
            return result;
        }

        private <V> void putToMap(
                Optional<SortedMap<String, V>> wasMap,
                Optional<SortedMap<String, V>> isMap,
                Consumer<Optional<SortedMap<String, V>>> setter,
                String key, @Nullable V value) {
            SortedMap<String, V> currentMap;
            if (isMap.isPresent()) {
                currentMap = isMap.get();
            } else if (wasMap.isPresent()) {
                currentMap = wasMap.get();
            } else {
                currentMap = Collections.emptySortedMap();
            }
            @Nullable
            V oldValue = currentMap.get(key);
            if (value == null) {
                if (oldValue != null) {
                    currentMap = initMap(wasMap, isMap, setter);
                    currentMap.remove(key);
                }
            } else if (!value.equals(oldValue)) {
                currentMap = initMap(wasMap, isMap, setter);
                currentMap.put(key, value);
            }
        }

        private SortedMap<String, String> initMeta() {
            return initMap(was.map(Record::getMeta), meta, map -> meta = map);
        }

        @CheckReturnValue
        public Builder putFields(Map<String, String> value) {
            initFields().putAll(value);
            return this;
        }

        private SortedMap<String, String> initFields() {
            return initMap(was.map(Record::getFields), fields, map -> fields = map);
        }

        @CheckReturnValue
        public Builder putReferences(Map<String, RecordID> value) {
            initRefs().putAll(value);
            return this;
        }

        private SortedMap<String, RecordID> initRefs() {
            return initMap(was.map(Record::getReferences), refs, map -> refs = map);
        }

        @CheckReturnValue
        public Builder redirectReferences(RecordID toIdentifier, RecordID fromIdentifier) {
            initRefs();
            refs.get().entrySet().stream()
                    .filter(entry -> fromIdentifier.equals(entry.getValue()))
                    .forEach(entry -> entry.setValue(toIdentifier));
            return this;
        }

        private static void fillMeta(SortedMap<String, String> map, String now, boolean newVersion) {
            if (map.get(MetaFields.identifier.name()) == null) {
                map.put(MetaFields.identifier.name(), RecordID.create().toString());
            }
            if (newVersion) {
                map.put(MetaFields.lastChange.name(), now);
            } else {
                map.putIfAbsent(MetaFields.lastChange.name(), now);
            }
        }

        @CheckReturnValue
        public Record build(String now) {
            if (was.isPresent() && !meta.isPresent() && !fields.isPresent() && !refs.isPresent()) {
                // Nothing changed
                return was.get();
            } else {
                initMeta();
                fillMeta(meta.get(), now, true);
                SortedMap<String, String> newFields;
                if (fields.isPresent()) {
                    newFields = Collections.unmodifiableSortedMap(fields.get());
                } else if (was.isPresent()) {
                    newFields = was.get().fields;
                } else {
                    newFields = Collections.emptySortedMap();
                }
                SortedMap<String, RecordID> newRefs;
                if (refs.isPresent()) {
                    newRefs = Collections.unmodifiableSortedMap(refs.get());
                } else if (was.isPresent()) {
                    newRefs = was.get().refs;
                } else {
                    newRefs = Collections.emptySortedMap();
                }
                Record result = new Record(
                        type, meta.get(), newFields, newRefs);
                meta = Optional.empty();
                fields = Optional.empty();
                refs = Optional.empty();
                return result;
            }
        }
        private final Optional<Record> was;
        private final Table type;
        private Optional<SortedMap<String, String>> meta;
        private Optional<SortedMap<String, String>> fields;
        private Optional<SortedMap<String, RecordID>> refs;
    }

    @Override
    public RecordID getIdentifier() {
        return identifier;
    }

    public String getLastChange() {
        return get(MetaFields.lastChange).orElse(ISO8601.EPOCH);
    }

    public Optional<RecordID> getTrace() {
        return get(Fields.trace).flatMap(RecordID::load);
    }

    public Table getType() {
        return this.type;
    }

    public String getShortName() {
        return get(Fields.shortName).orElse("");
    }

    public String getLongName() {
        return get(Fields.longName).orElse("");
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
        return get(Fields.desc).orElse("");
    }

    public boolean isConnectionScope() {
        return get(References.left).isPresent() && get(References.right).isPresent();
    }

    public ConnectionScope getConnectionScope() {
        RecordID left = get(References.left).get();
        RecordID right = get(References.right).get();
        String direction = get(Fields.direction).orElse(Direction.None.name());
        return new ConnectionScope(
                left, right, Direction.valueOf(direction));
    }

    public Optional<RecordID> getViewOf() {
        return get(References.viewOf);
    }

    public Optional<RecordID> getContainer() {
        return get(References.container);
    }

    public Optional<RecordID> getSubtype() {
        return get(References.subtype);
    }

    public boolean isExternal() {
        return toBoolean(get(Fields.external));
    }

    public Point2D getOrigin() {
        try {
            return new Point2D(
                    Integer.valueOf(get(Fields.originX).orElse("0")),
                    Integer.valueOf(get(Fields.originY).orElse("0"))
            );
        } catch (NumberFormatException ex) {
            return Point2D.ZERO;
        }
    }

    public Color getColor() {
        try {
            return Color.valueOf(get(Fields.color).orElse(""));
        } catch (IllegalArgumentException ex) {
            return Color.LIGHTYELLOW;
        }
    }

    public Optional<String> get(MetaFields key) {
        return getMetaField(key.name());
    }

    public Optional<String> get(Fields key) {
        return getField(key.name());
    }

    public Optional<RecordID> get(References key) {
        return getRef(key.name());
    }

    public Optional<String> getMetaField(String key) {
        return Optional.ofNullable(meta.get(key));
    }

    public Optional<String> getField(String key) {
        return Optional.ofNullable(fields.get(key));
    }

    public Optional<RecordID> getRef(String key) {
        return Optional.ofNullable(refs.get(key));
    }

    public SortedMap<String, String> getMeta() {
        return meta;
    }

    public SortedMap<String, String> getFields() {
        return fields;
    }

    public SortedMap<String, RecordID> getReferences() {
        return refs;
    }

    public SortedMap<String, String> getAllFields() {
        SortedMap<String, String> result = new TreeMap<>();
        result.putAll(meta);
        result.putAll(fields);
        for (Map.Entry<String, RecordID> entry : refs.entrySet()) {
            result.put(References.PREFIX + entry.getKey(), entry.getValue().toString());
        }
        result.put(Identifiable.IDENTIFIER, identifier.toString());
        return result;
    }

    public Stream<String> getAllFieldNames() {
        return Stream.concat(Stream.concat(
                meta.keySet().stream(),
                fields.keySet().stream()),
                refs.keySet().stream().map(ss -> References.PREFIX + ss))
                .sorted().distinct();
    }

    public static Builder create(Table type) {
        return new Builder(Optional.empty(), type);
    }

    public Builder asBuilder() {
        return new Builder(Optional.of(this), type);
    }

    public static Record newerOf(Record left, Record right) {
        if (left.getLastChange().compareTo(right.getLastChange()) > 0) {
            return left;
        } else {
            return right;
        }
    }

    public static Record load(Table type, Map<String, String> allFields) {
        SortedMap<String, String> newMetaFields = new TreeMap<>();
        SortedMap<String, String> newFields = new TreeMap<>();
        SortedMap<String, RecordID> newRefs = new TreeMap<>();
        for (Map.Entry<String, String> entry : allFields.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.startsWith(References.PREFIX)) {
                String suffix = key.substring(References.PREFIX.length());
                Optional<RecordID> foreignKey = RecordID.load(value);
                if (foreignKey.isPresent()) {
                    newRefs.put(suffix, foreignKey.get());
                }
            } else if (!value.isEmpty()) {
                try {
                    // Throws if key is not meta
                    MetaFields.valueOf(key);
                    newMetaFields.put(key, value);
                } catch (RuntimeException ex) {
                    newFields.put(key, value);
                }
            }
        }
        Builder.fillMeta(newMetaFields, ISO8601.EPOCH, false);
        return new Record(type, Collections.unmodifiableSortedMap(newMetaFields),
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
            Table type,
            SortedMap<String, String> unmodifiableMeta,
            SortedMap<String, String> unmodifiableFields,
            SortedMap<String, RecordID> unmodifiableRefs) {
        this.type = type;
        this.meta = unmodifiableMeta;
        this.fields = unmodifiableFields;
        this.refs = unmodifiableRefs;

        this.identifier = RecordID.load(meta.get(MetaFields.identifier.name())).get();
    }
}

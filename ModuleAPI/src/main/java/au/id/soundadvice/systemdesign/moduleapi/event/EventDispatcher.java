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
package au.id.soundadvice.systemdesign.moduleapi.event;

import au.id.soundadvice.systemdesign.moduleapi.entity.BaselinePair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordType;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.util.Pair;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum EventDispatcher {
    INSTANCE;

    private static final Logger LOG = Logger.getLogger(EventDispatcher.class.getName());
    private final ConcurrentMap<RecordType, List<BiFunction<BaselinePair, Record, BaselinePair>>> flowDownListeners = new ConcurrentHashMap<>();
    private final ConcurrentMap<RecordType, List<BiFunction<BaselinePair, Record, BaselinePair>>> createListeners = new ConcurrentHashMap<>();
    private final ConcurrentMap<Pair<RecordType, RecordType>, BiFunction<BaselinePair, Pair<Record, Record>, BaselinePair>> copyOperations = new ConcurrentHashMap<>();
    private final ConcurrentMap<Pair<RecordType, RecordType>, BiFunction<BaselinePair, Pair<Record, Record>, BaselinePair>> moveOperations = new ConcurrentHashMap<>();
    private final ConcurrentMap<Pair<RecordType, RecordType>, BiFunction<BaselinePair, Pair<Record, Record>, BaselinePair>> linkOperations = new ConcurrentHashMap<>();

    public void addFlowDownListener(RecordType type, BiFunction<BaselinePair, Record, BaselinePair> listener) {
        List<BiFunction<BaselinePair, Record, BaselinePair>> list = flowDownListeners.get(type);
        if (list == null) {
            list = new CopyOnWriteArrayList<>();
            List<BiFunction<BaselinePair, Record, BaselinePair>> existing = flowDownListeners.putIfAbsent(type, list);
            if (existing != null) {
                list = existing;
            }
        }
        list.add(listener);
    }

    public void addCreateListener(RecordType type, BiFunction<BaselinePair, Record, BaselinePair> listener) {
        List<BiFunction<BaselinePair, Record, BaselinePair>> list = createListeners.get(type);
        if (list == null) {
            list = new CopyOnWriteArrayList<>();
            List<BiFunction<BaselinePair, Record, BaselinePair>> existing = createListeners.putIfAbsent(type, list);
            if (existing != null) {
                list = existing;
            }
        }
        list.add(listener);
    }

    public BaselinePair dispatchFlowDownEvent(BaselinePair baselines, String now, Record record) {
        List<BiFunction<BaselinePair, Record, BaselinePair>> listeners
                = flowDownListeners.getOrDefault(record.getType(), Collections.emptyList());
        for (BiFunction<BaselinePair, Record, BaselinePair> listener : listeners) {
            try {
                baselines = listener.apply(baselines, record);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, null, ex);
            }
        }
        return baselines;
    }

    public BaselinePair dispatchCreateEvent(BaselinePair baselines, String now, Record record) {
        List<BiFunction<BaselinePair, Record, BaselinePair>> listeners
                = createListeners.getOrDefault(record.getType(), Collections.emptyList());
        for (BiFunction<BaselinePair, Record, BaselinePair> listener : listeners) {
            try {
                baselines = listener.apply(baselines, record);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, null, ex);
            }
        }
        return baselines;
    }

    public void setCopyOperation(RecordType from, RecordType to, BiFunction<BaselinePair, Pair<Record, Record>, BaselinePair> operation) {
        copyOperations.put(new Pair<>(from, to), operation);
    }

    public void setMoveOperation(RecordType from, RecordType to, BiFunction<BaselinePair, Pair<Record, Record>, BaselinePair> operation) {
        moveOperations.put(new Pair<>(from, to), operation);
    }

    public void setLinkOperation(RecordType from, RecordType to, BiFunction<BaselinePair, Pair<Record, Record>, BaselinePair> operation) {
        linkOperations.put(new Pair<>(from, to), operation);
    }

    public Optional<BiFunction<BaselinePair, Pair<Record, Record>, BaselinePair>> getCopyOperation(RecordType from, RecordType to) {
        return Optional.ofNullable(copyOperations.get(new Pair<>(from, to)));
    }

    public Optional<BiFunction<BaselinePair, Pair<Record, Record>, BaselinePair>> getMoveOperation(RecordType from, RecordType to) {
        return Optional.ofNullable(moveOperations.get(new Pair<>(from, to)));
    }

    public Optional<BiFunction<BaselinePair, Pair<Record, Record>, BaselinePair>> getLinkOperation(RecordType from, RecordType to) {
        return Optional.ofNullable(linkOperations.get(new Pair<>(from, to)));
    }
}

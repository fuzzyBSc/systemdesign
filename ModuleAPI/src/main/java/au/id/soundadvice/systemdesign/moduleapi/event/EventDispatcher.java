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

import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
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
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum EventDispatcher {
    INSTANCE;

    private static final Logger LOG = Logger.getLogger(EventDispatcher.class.getName());
    private final ConcurrentMap<Table, List<BiFunction<WhyHowPair<Baseline>, Record, WhyHowPair<Baseline>>>> flowDownListeners = new ConcurrentHashMap<>();
    private final ConcurrentMap<Table, List<BiFunction<WhyHowPair<Baseline>, Record, WhyHowPair<Baseline>>>> createListeners = new ConcurrentHashMap<>();
    private final ConcurrentMap<Pair<Table, Table>, BiFunction<WhyHowPair<Baseline>, Pair<Record, Record>, WhyHowPair<Baseline>>> copyOperations = new ConcurrentHashMap<>();
    private final ConcurrentMap<Pair<Table, Table>, BiFunction<WhyHowPair<Baseline>, Pair<Record, Record>, WhyHowPair<Baseline>>> moveOperations = new ConcurrentHashMap<>();
    private final ConcurrentMap<Pair<Table, Table>, BiFunction<WhyHowPair<Baseline>, Pair<Record, Record>, WhyHowPair<Baseline>>> linkOperations = new ConcurrentHashMap<>();

    public void addFlowDownListener(Table type, BiFunction<WhyHowPair<Baseline>, Record, WhyHowPair<Baseline>> listener) {
        List<BiFunction<WhyHowPair<Baseline>, Record, WhyHowPair<Baseline>>> list = flowDownListeners.get(type);
        if (list == null) {
            list = new CopyOnWriteArrayList<>();
            List<BiFunction<WhyHowPair<Baseline>, Record, WhyHowPair<Baseline>>> existing = flowDownListeners.putIfAbsent(type, list);
            if (existing != null) {
                list = existing;
            }
        }
        list.add(listener);
    }

    public void addCreateListener(Table type, BiFunction<WhyHowPair<Baseline>, Record, WhyHowPair<Baseline>> listener) {
        List<BiFunction<WhyHowPair<Baseline>, Record, WhyHowPair<Baseline>>> list = createListeners.get(type);
        if (list == null) {
            list = new CopyOnWriteArrayList<>();
            List<BiFunction<WhyHowPair<Baseline>, Record, WhyHowPair<Baseline>>> existing = createListeners.putIfAbsent(type, list);
            if (existing != null) {
                list = existing;
            }
        }
        list.add(listener);
    }

    public WhyHowPair<Baseline> dispatchFlowDownEvent(WhyHowPair<Baseline> baselines, String now, Record record) {
        List<BiFunction<WhyHowPair<Baseline>, Record, WhyHowPair<Baseline>>> listeners
                = flowDownListeners.getOrDefault(record.getType(), Collections.emptyList());
        for (BiFunction<WhyHowPair<Baseline>, Record, WhyHowPair<Baseline>> listener : listeners) {
            try {
                baselines = listener.apply(baselines, record);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, null, ex);
            }
        }
        return baselines;
    }

    public WhyHowPair<Baseline> dispatchCreateEvent(WhyHowPair<Baseline> baselines, String now, Record record) {
        List<BiFunction<WhyHowPair<Baseline>, Record, WhyHowPair<Baseline>>> listeners
                = createListeners.getOrDefault(record.getType(), Collections.emptyList());
        for (BiFunction<WhyHowPair<Baseline>, Record, WhyHowPair<Baseline>> listener : listeners) {
            try {
                baselines = listener.apply(baselines, record);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, null, ex);
            }
        }
        return baselines;
    }

    public void setCopyOperation(Table from, Table to, BiFunction<WhyHowPair<Baseline>, Pair<Record, Record>, WhyHowPair<Baseline>> operation) {
        copyOperations.put(new Pair<>(from, to), operation);
    }

    public void setMoveOperation(Table from, Table to, BiFunction<WhyHowPair<Baseline>, Pair<Record, Record>, WhyHowPair<Baseline>> operation) {
        moveOperations.put(new Pair<>(from, to), operation);
    }

    public void setLinkOperation(Table from, Table to, BiFunction<WhyHowPair<Baseline>, Pair<Record, Record>, WhyHowPair<Baseline>> operation) {
        linkOperations.put(new Pair<>(from, to), operation);
    }

    public Optional<BiFunction<WhyHowPair<Baseline>, Pair<Record, Record>, WhyHowPair<Baseline>>> getCopyOperation(Table from, Table to) {
        return Optional.ofNullable(copyOperations.get(new Pair<>(from, to)));
    }

    public Optional<BiFunction<WhyHowPair<Baseline>, Pair<Record, Record>, WhyHowPair<Baseline>>> getMoveOperation(Table from, Table to) {
        return Optional.ofNullable(moveOperations.get(new Pair<>(from, to)));
    }

    public Optional<BiFunction<WhyHowPair<Baseline>, Pair<Record, Record>, WhyHowPair<Baseline>>> getLinkOperation(Table from, Table to) {
        return Optional.ofNullable(linkOperations.get(new Pair<>(from, to)));
    }
}

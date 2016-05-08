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
package au.id.soundadvice.systemdesign.physical;

import au.id.soundadvice.systemdesign.moduleapi.entity.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.entity.BaselinePair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Fields;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordType;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum Identity implements RecordType {
    identity;

    @Override
    public String getTypeName() {
        return name();
    }

    public static Optional<Record> getSystemOfInterest(BaselinePair state) {
        Optional<String> optionalTrace
                = Identity.findAll(state.getChild()).findAny()
                .flatMap(Record::getTrace);
        return optionalTrace.flatMap(
                trace -> state.getParent().get(trace, Item.item));
    }

    /**
     * Retrieve identifying information about the system of interest for this
     * baseline.
     *
     * @param baseline The baseline whose identity to find
     * @return
     */
    public static Record get(Baseline baseline) {
        return findAll(baseline).findAny().get();
    }

    /**
     * Retrieve identifying information about the system of interest for this
     * baseline.
     *
     * @param baseline The baseline whose identity to find
     * @return
     */
    public static Stream<Record> findAll(Baseline baseline) {
        return baseline.findByType(identity);
    }

    public static Record create(String now) {
        return Record.create(identity).build(now);
    }

    public static Record create(String now, Record parentIdentity, Record parentItem) {
        return itemToIdentity(now, parentIdentity, parentItem);
    }

    public static IDPath getIdPath(Record record) {
        return IDPath.valueOfDotted(record.getShortName());
    }

    /**
     * Return a copy of this baseline with identity set to id.
     *
     * @param baseline The baseline to update
     * @param id The identity to set
     * @return
     */
    @CheckReturnValue
    public static Baseline setIdentity(Baseline baseline, Record id) {
        Iterator<Record> existing = findAll(baseline).iterator();
        while (existing.hasNext()) {
            baseline = baseline.remove(existing.next().getIdentifier());
        }
        return baseline.add(id);
    }

    @CheckReturnValue
    public Record setId(Record record, String now, IDPath value) {
        return record.asBuilder()
                .setShortName(value.toString())
                .build(now);
    }

    @Override
    public Stream<Problem> getTraceProblems(BaselinePair context, Record traceParent, Stream<Record> traceChild) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Stream<Problem> getUntracedParentProblems(BaselinePair context, Stream<Record> untracedParents) {
        // It's correct for the identity in the parent baseline to be untraced.
        // The child identity traces to an Item, not an Identity.
        return Stream.empty();
    }

    @Override
    public Stream<Problem> getUntracedChildProblems(BaselinePair context, Stream<Record> untracedChildren) {
        return untracedChildren
                .map(record -> Problem.flowProblem(
                        "Item " + record.getLongName() + " is missing from the parent baseline",
                        Optional.of((baselines, now) -> addItemToParentBaseline(baselines, now, record)),
                        Optional.empty()));
    }

    private static Record itemToIdentity(String now, Record parentIdentity, Record parentItem) {
        IDPath parentPath = getIdPath(parentIdentity);
        IDPath itemShortPath = IDPath.valueOfDotted(parentItem.getShortName());
        IDPath childPath = parentPath.resolve(itemShortPath);
        return Record.create(identity)
                .putAll(parentItem.getFields())
                .setShortName(childPath.toString())
                .build(now);
    }

    private static Record identityToItem(Baseline parentBaseline, Record childIdentity) {
        Record parentIdentity = get(parentBaseline);
        IDPath parentPath = getIdPath(parentIdentity);
        IDPath childPath = getIdPath(childIdentity);
        IDPath itemShortPath;
        if (childPath.getParent().equals(parentPath)) {
            itemShortPath = childPath.getLastSegment();
        } else {
            itemShortPath = Item.getNextItemId(parentBaseline);
        }
        // We have done a best effort short path assignment
        // If this resulted in any conflicts we leave it to other code to
        //resolve 
        Map<String, String> fields = new HashMap<>(childIdentity.getFields());
        fields.put(Fields.shortName.name(), itemShortPath.toString());
        return Record.load(Item.item, fields);
    }

    private BaselinePair addItemToParentBaseline(BaselinePair baselines, String now, Record record) {
        Optional<Record> childIdentity = baselines.getChild().get(record);
        Optional<Record> parentIdentity = findAll(baselines.getParent()).findAny();
        Optional<String> optionalTrace = childIdentity.flatMap(Record::getTrace);
        Optional<Record> optionalItem = optionalTrace.flatMap(trace -> baselines.getParent().get(trace, Item.item));
        if (optionalTrace.isPresent() && parentIdentity.isPresent() && !optionalItem.isPresent()) {
            // Preconditions are met
            Record item = identityToItem(baselines.getParent(), childIdentity.get());
            return baselines.setParent(baselines.getParent().add(item));
        } else {
            return baselines;
        }
    }

    @Override
    public Object getUniqueConstraint(Record record) {
        // There can be only one
        return identity;
    }

    @Override
    public Record merge(BaselinePair baselines, String now, Record left, Record right) {
        return Record.newerOf(left, right);
    }
}

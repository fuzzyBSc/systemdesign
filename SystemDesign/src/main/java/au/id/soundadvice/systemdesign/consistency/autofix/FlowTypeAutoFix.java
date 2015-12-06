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
package au.id.soundadvice.systemdesign.consistency.autofix;

import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.model.Baseline.BaselineAnd;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.FlowType;
import au.id.soundadvice.systemdesign.model.UndoState;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FlowTypeAutoFix {

    private static Baseline removeUnusedTypes(Baseline baseline) {
        final Baseline preRemoveBaseline = baseline;
        Iterator<FlowType> it = FlowType.find(baseline)
                .filter(type -> {
                    Optional<Flow> flow = type.getFlows(preRemoveBaseline).findAny();
                    return !flow.isPresent();
                }).iterator();
        while (it.hasNext()) {
            FlowType type = it.next();
            baseline = type.removeFrom(baseline);
        }
        return baseline;
    }

    private static Map<String, List<FlowType>> getTypesByName(Baseline baseline) {
        return FlowType.find(baseline)
                .collect(Collectors.groupingBy(FlowType::getName));
    }

    private static BaselineAnd<Map<String, FlowType>> compressDuplicates(Baseline baseline) {
        // Fix duplicates within a given baseline
        Map<String, List<FlowType>> byName = getTypesByName(baseline);

        Map<String, FlowType> canonicalTypes = byName.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().get(0)));
        {
            Iterator<Flow> it = Flow.find(baseline).iterator();
            while (it.hasNext()) {
                Flow flow = it.next();
                FlowType type = flow.getType().getTarget(baseline.getContext());
                FlowType canonicalType = canonicalTypes.get(type.getName());
                baseline = flow.setType(baseline, canonicalType).getBaseline();
            }
        }
        {
            Iterator<FlowType> it = FlowType.find(baseline).iterator();
            while (it.hasNext()) {
                FlowType type = it.next();
                FlowType canonicalType = canonicalTypes.get(type.getName());
                if (!type.getUuid().equals(canonicalType.getUuid())) {
                    baseline = type.removeFrom(baseline);
                }
            }
        }

        return baseline.and(canonicalTypes);
    }

    static UndoState fix(UndoState state) {
        Baseline functional = state.getFunctional();
        Baseline allocated = state.getAllocated();

        functional = removeUnusedTypes(functional);
        allocated = removeUnusedTypes(allocated);

        Map<String, FlowType> functionalTypes;
        {
            BaselineAnd<Map<String, FlowType>> result = compressDuplicates(functional);
            functional = result.getBaseline();
            functionalTypes = result.getRelation();
        }
        allocated = compressDuplicates(allocated).getBaseline();

        /*
         * look for traces that are no longer valid.
         */
        Iterator<FlowType> tracedIt = FlowType.find(allocated).iterator();
        while (tracedIt.hasNext()) {
            FlowType type = tracedIt.next();
            // Find the trace if it exists
            Optional<FlowType> newTrace = type.getTrace(functional);
            // Assign it back - noop if unchanged
            allocated = type.setTrace(allocated, newTrace).getBaseline();
        }

        /*
         * Find any allocated types whose name exists in the functional baseline
         * but are untraced. That may be because we just deleted the trace.
         */
        Iterator<FlowType> untracedIt = FlowType.find(allocated)
                .filter(type -> !type.isTraced())
                .iterator();
        while (untracedIt.hasNext()) {
            FlowType type = untracedIt.next();
            Optional<FlowType> newTrace = Optional.ofNullable(
                    functionalTypes.get(type.getName()));
            allocated = type.setTrace(allocated, newTrace).getBaseline();
        }

        return new UndoState(functional, allocated);
    }
}

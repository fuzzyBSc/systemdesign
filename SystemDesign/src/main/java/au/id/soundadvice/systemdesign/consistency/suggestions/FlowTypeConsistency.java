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
package au.id.soundadvice.systemdesign.consistency.suggestions;

import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.model.UndoState;
import au.id.soundadvice.systemdesign.consistency.Problem;
import au.id.soundadvice.systemdesign.consistency.SolutionFlow;
import au.id.soundadvice.systemdesign.consistency.UpdateSolution;
import au.id.soundadvice.systemdesign.model.FlowType;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.util.Pair;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FlowTypeConsistency {

    /**
     * Search for missing flows in the allocated baseline.
     *
     * @param state
     * @return
     */
    public static Stream<Problem> getProblems(UndoState state) {
        Baseline functional = state.getFunctional();
        Baseline allocated = state.getAllocated();
        return FlowType.find(allocated)
                .flatMap(allocatedType -> {
                    Optional<FlowType> trace = allocatedType.getTrace(functional);
                    if (trace.isPresent()) {
                        FlowType functionalType = trace.get();
                        if (functionalType.getName().equals(allocatedType.getName())) {
                            // No mismatch
                            return Stream.empty();
                        } else {
                            return Stream.of(new Pair<>(functionalType, allocatedType));
                        }
                    } else {
                        // No trace
                        return Stream.empty();
                    }
                })
                .map(pair -> {
                    return new Problem("Flow type mismatch\n"
                            + "Functional = " + pair.getKey() + "\n"
                            + "Allocated = " + pair.getValue(),
                            Stream.of(
                                    UpdateSolution.update(
                                            SolutionFlow.Down,
                                            solutionState -> {
                                                Baseline solutionAllocated = solutionState.getAllocated();
                                                Optional<FlowType> sample = solutionAllocated.get(pair.getValue());
                                                String name = pair.getKey().getName();
                                                if (sample.isPresent()) {
                                                    solutionAllocated = sample.get().setName(
                                                            solutionAllocated, name)
                                                    .getBaseline();
                                                    return solutionState.setAllocated(solutionAllocated);
                                                } else {
                                                    return solutionState;
                                                }
                                            }),
                                    UpdateSolution.update(
                                            SolutionFlow.Up,
                                            solutionState -> {
                                                Baseline solutionFunctional = solutionState.getFunctional();
                                                Optional<FlowType> sample = solutionFunctional.get(pair.getKey());
                                                String name = pair.getValue().getName();
                                                if (sample.isPresent()) {
                                                    solutionFunctional = sample.get().setName(
                                                            solutionFunctional, name)
                                                    .getBaseline();
                                                    return solutionState.setFunctional(solutionFunctional);
                                                } else {
                                                    return solutionState;
                                                }
                                            })
                            ));
                });
    }
}

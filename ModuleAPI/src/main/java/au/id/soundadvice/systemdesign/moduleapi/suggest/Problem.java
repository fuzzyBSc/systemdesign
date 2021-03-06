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
package au.id.soundadvice.systemdesign.moduleapi.suggest;

import java.util.Optional;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Problem {

    public Optional<Solution> getFlowDownSolution() {
        return flowDownSolution;
    }

    public Optional<Solution> getFlowUpSolution() {
        return flowUpSolution;
    }

    public Optional<Solution> getOnLoadAutofixSolution() {
        return onLoadAutoFixSolution;
    }

    public Optional<Solution> getOnChangeAutofixSolution() {
        return onChangeAutoFixSolution;
    }

    @Override
    public String toString() {
        return description + "? " + flowDownSolution + " : " + flowUpSolution;
    }

    public String getDescription() {
        return description;
    }

    private final String description;
    private final Optional<Solution> flowDownSolution;
    private final Optional<Solution> flowUpSolution;
    private final Optional<Solution> onLoadAutoFixSolution;
    private final Optional<Solution> onChangeAutoFixSolution;

    public static Problem flowProblem(
            String description,
            Optional<Solution> flowDownSolution,
            Optional<Solution> flowUpSolution) {
        return new Problem(description, Optional.empty(), Optional.empty(), flowDownSolution, flowUpSolution);
    }

    public static Problem onLoadAutofixProblem(
            Solution solution) {
        return new Problem("onLoadAutoFix", Optional.of(solution), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static Problem onChangeAutofixProblem(
            Solution solution) {
        return new Problem("onChangeAutoFix", Optional.empty(), Optional.of(solution), Optional.empty(), Optional.empty());
    }

    private Problem(
            String description,
            Optional<Solution> onLoadAutofixSolution,
            Optional<Solution> onChangeAutofixSolution,
            Optional<Solution> flowDownSolution,
            Optional<Solution> flowUpSolution) {
        this.description = description;
        this.onLoadAutoFixSolution = onLoadAutofixSolution;
        this.onChangeAutoFixSolution = onChangeAutofixSolution;
        this.flowDownSolution = flowDownSolution;
        this.flowUpSolution = flowUpSolution;
    }
}

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
package au.id.soundadvice.systemdesign.logical.drawing;

import au.id.soundadvice.systemdesign.logical.Flow;
import au.id.soundadvice.systemdesign.moduleapi.ConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.drawing.DrawingConnector;
import au.id.soundadvice.systemdesign.moduleapi.entity.DiffPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class LogicalSchematicFlow implements DrawingConnector {

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + Objects.hashCode(this.flow);
        hash = 29 * hash + Objects.hashCode(this.flowType);
        hash = 29 * hash + Objects.hashCode(this.scope);
        hash = 29 * hash + Objects.hashCode(this.leftView);
        hash = 29 * hash + Objects.hashCode(this.rightView);
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
        final LogicalSchematicFlow other = (LogicalSchematicFlow) obj;
        if (!Objects.equals(this.flow, other.flow)) {
            return false;
        }
        if (!Objects.equals(this.flowType, other.flowType)) {
            return false;
        }
        if (!Objects.equals(this.scope, other.scope)) {
            return false;
        }
        if (!Objects.equals(this.leftView, other.leftView)) {
            return false;
        }
        if (!Objects.equals(this.rightView, other.rightView)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isDiff() {
        return flow.isDiff();
    }

    @Override
    public boolean isChanged() {
        return flow.isChanged()
                || flowType.isChanged();
    }

    @Override
    public boolean isAdded() {
        return flow.isAdded();
    }

    @Override
    public boolean isDeleted() {
        return flow.isDeleted();
    }

    public LogicalSchematicFlow(DiffPair<Record> flow, Record leftView, Record rightView) {
        this.flow = flow;
        this.leftView = leftView;
        this.rightView = rightView;
        this.flowType = flow.map((baseline, flowRecord) -> Flow.flow.getFlowType(baseline, flowRecord));
        this.scope = new ConnectionScope(
                leftView.getIdentifier(), rightView.getIdentifier(),
                flow.getSample().getConnectionScope().getDirection());
    }

    private final DiffPair<Record> flow;
    private final DiffPair<Record> flowType;
    private final ConnectionScope scope;
    private final Record leftView;
    private final Record rightView;

    @Override
    public ConnectionScope getScope() {
        return scope;
    }

    @Override
    public DiffPair<String> getLabel() {
        return flowType.map(Record::getLongName);
    }

    @Override
    public String getIdentifier() {
        return Stream.of(flow.getSample().getIdentifier(), leftView.getIdentifier(), rightView.getIdentifier())
                .collect(Collectors.joining(":"));
    }
}

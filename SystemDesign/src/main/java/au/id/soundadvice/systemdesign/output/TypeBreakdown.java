/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.output;

import au.id.soundadvice.systemdesign.model.FlowType;
import java.io.IOException;
import java.io.PrintStream;

/**
 *
 * @author fuzzy
 */
public class TypeBreakdown {

	public static void write(PrintStream stream, FlowType context) throws IOException {
		stream.println("digraph G {");
		stream.println("node [shape=record];");
		writeTraces(stream, context);
		stream.println("}");
	}

	public static void writeTraces(PrintStream stream, FlowType parent) throws IOException {
		for (FlowType type : parent.getChildren().values()) {
			stream.print('"');
			stream.print(parent.toString());
			stream.print("\" -> \"");
			stream.print(type.toString());
			stream.println("\" [dir = both arrowtail = diamond]");
			writeTraces(stream, type);
		}
	}
}

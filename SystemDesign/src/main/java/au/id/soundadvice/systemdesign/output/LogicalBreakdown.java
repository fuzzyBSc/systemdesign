/*
 * Please refer to the LICENSE file for licensing information.
 */
package au.id.soundadvice.systemdesign.output;

import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import java.io.IOException;
import java.io.PrintStream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class LogicalBreakdown {

	public static void write(
			PrintStream stream, Item context,
			boolean includeFunctions, boolean includeFlows,
			boolean cluster) throws IOException {
		stream.println("digraph G {");
		stream.println("graph [ rankdir=LR ]");
		writeTraces(stream, context, includeFunctions, includeFlows, cluster);
		stream.println("}");
	}

	public static void writeTraces(
			PrintStream stream, Item parent,
			boolean includeFunctions, boolean includeFlows,
			boolean doClustering) throws IOException {
		if (includeFlows) {
			for (Interface iface : parent.getSubInterfaces().values()) {
				writeTraces(stream, iface, doClustering);
			}
		}
		if (includeFunctions) {
			Cluster cluster = new Cluster(parent.toString(), "");
			for (Function function : parent.getFunctions()) {
				Function trace = function.getTrace();
				if (trace != null) {
					stream.print('"');
					stream.print(trace.toString());
					stream.print("\" -> \"");
					stream.print(function.toString());
					stream.println("\"");
				}
				cluster.add(function.toString());
			}
			if (doClustering) {
				cluster.write(stream);
			}
		}
		for (Item subsystem : parent.getChildren().values()) {
			writeTraces(stream, subsystem, includeFunctions, includeFlows, doClustering);
		}
	}

	public static void writeTraces(PrintStream stream, Interface parent, boolean doClustering) throws IOException {
		Cluster cluster = new Cluster(parent.toString(), "");
		for (Flow flow : parent.getFlows()) {
			Flow flowTrace = flow.getTrace();
			if (flowTrace != null) {
				stream.print('"');
				stream.print(flowTrace.toString());
				stream.print("\" -> \"");
				stream.print(flow.toString());
				stream.println("\"");
			}
			Function functionTrace = flow.getFunctionTrace();
			if (functionTrace != null) {
				stream.print('"');
				stream.print(functionTrace.toString());
				stream.print("\" -> \"");
				stream.print(flow.toString());
				stream.println("\"");
			}
			cluster.add(flow.toString());
		}
		if (doClustering) {
			cluster.write(stream);
		}
		for (Interface subInterface : parent.getChildren().values()) {
			writeTraces(stream, subInterface, doClustering);

		}
	}
}

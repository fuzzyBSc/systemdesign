/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.output;

import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.FlowNode;
import au.id.soundadvice.systemdesign.model.FlowType;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import java.io.PrintStream;

/**
 *
 * @author fuzzy
 */
public class toCSV {

	public static void write(PrintStream stream, Item context) {
		stream.println("source,flow,target");
		writeLines(stream, context);
	}

	private static void writeLines(PrintStream stream, Item context) {
		for (Interface iface : context.getSubInterfaces().values()) {
			writeLines(stream, iface);
		}
		for (Item subsystem : context.getChildren().values()) {
			writeLines(stream, subsystem);
		}
	}

	private static void writeLines(PrintStream stream, Interface iface) {
		for (Flow flow : iface.getFlows()) {
			{
				FlowNode source = flow.getSource();
				Item sourceItem = source.getItem();
				stream.print(sourceItem.getAddress());
				stream.print('.');
				stream.print(sourceItem.getName());
				if (!source.isItem()) {
					stream.print('/');
					stream.print(source.getAddress());
					stream.print('.');
					stream.print(source.getName());
				}
			}
			stream.print(',');
			{
				FlowType type = flow.getType();
				stream.print(type.getAddress());
				stream.print('.');
				stream.print(type.getName());
			}
			stream.print(',');
			{
				FlowNode target = flow.getTarget();
				Item targetItem = target.getItem();
				stream.print(targetItem.getAddress());
				stream.print('.');
				stream.print(targetItem.getName());
				if (!target.isItem()) {
					stream.print('/');
					stream.print(target.getAddress());
					stream.print('.');
					stream.print(target.getName());
				}
			}
			stream.println();
			for (Interface subinterface : iface.getChildren().values()) {
				writeLines(stream, subinterface);
			}
		}
	}
}

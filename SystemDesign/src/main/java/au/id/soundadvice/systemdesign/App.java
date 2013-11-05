/*
 * Please refer to the LICENSE file for licensing information.
 */
package au.id.soundadvice.systemdesign;

import au.id.soundadvice.systemdesign.util.PipeTo;
import au.id.soundadvice.systemdesign.hierarchy.InvalidHierarchyException;
import au.id.soundadvice.systemdesign.output.PhysicalSchematic;
import au.id.soundadvice.systemdesign.input.InputModel;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.input.Load;
import au.id.soundadvice.systemdesign.model.Model;
import au.id.soundadvice.systemdesign.output.LogicalBreakdown;
import au.id.soundadvice.systemdesign.output.LogicalContext;
import au.id.soundadvice.systemdesign.output.LogicalSchematic;
import au.id.soundadvice.systemdesign.output.PhysicalBreakdown;
import au.id.soundadvice.systemdesign.output.PhysicalContext;
import au.id.soundadvice.systemdesign.output.TypeBreakdown;
import au.id.soundadvice.systemdesign.output.toCSV;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Hello world!
 *
 */
public class App {

	public static void main(String[] args) throws IOException, InvalidHierarchyException {
		List<File> files = new ArrayList<>(args.length);
		for (String filename : args) {
			files.add(new File(filename));
		}
		InputModel inputModel = new InputModel(files);
		System.out.println(inputModel);
		Model model = Load.load(inputModel);
		System.out.println(model);

		File dir = new File("context");
		dir.mkdir();

		try (PrintStream printStream = new PrintStream(new PipeTo(
				new File(dir, "TypeBreakdown.svg"),
				Runtime.getRuntime().exec("dot -Tsvg")).start())) {
			TypeBreakdown.write(
					printStream,
					model.getTypeContext());
		}
		try (PrintStream printStream = new PrintStream(
				new FileOutputStream(new File(dir, "model.csv")))) {
			toCSV.write(
					printStream,
					model.getItemContext());
		}
		writeSchematics(dir, model.getItemContext());
	}

	private static void writeSchematics(File dir, Item item) throws IOException {
		String prefix = item.getAddress() + "." + item.getName();
		File subdir;
		if (".".equals(prefix)) {
			prefix = "";
			subdir = dir;
		} else {
			subdir = new File(dir, prefix);
			subdir.mkdir();
		}
		if (item.getTrace() != null) {
			try (PrintStream printStream = new PrintStream(new PipeTo(
					new File(subdir, prefix + "PhysicalContext.svg"),
					Runtime.getRuntime().exec("dot -Tsvg")).start())) {
				PhysicalContext.write(printStream, item);
			}
			try (PrintStream printStream = new PrintStream(new PipeTo(
					new File(subdir, prefix + "LogicalContext.svg"),
					Runtime.getRuntime().exec("dot -Tsvg")).start())) {
				LogicalContext.write(printStream, item);
			}
		}
		if (!item.getChildren().isEmpty()) {
			try (PrintStream printStream = new PrintStream(new PipeTo(
					new File(subdir, prefix + "PhysicalBreakdownFull.svg"),
					Runtime.getRuntime().exec("dot -Tsvg")).start())) {
				PhysicalBreakdown.write(
						printStream,
						item, true);
			}
			try (PrintStream printStream = new PrintStream(new PipeTo(
					new File(subdir, prefix + "LogicalBreakdownClusteredFull.svg"),
					Runtime.getRuntime().exec("dot -Tsvg")).start())) {
				LogicalBreakdown.write(
						printStream,
						item, true, true, true);
			}
			try (PrintStream printStream = new PrintStream(new PipeTo(
					new File(subdir, prefix + "PhysicalBreakdown.svg"),
					Runtime.getRuntime().exec("dot -Tsvg")).start())) {
				PhysicalBreakdown.write(
						printStream,
						item, false);
			}
			try (PrintStream printStream = new PrintStream(new PipeTo(
					new File(subdir, prefix + "LogicalBreakdownClustered.svg"),
					Runtime.getRuntime().exec("dot -Tsvg")).start())) {
				LogicalBreakdown.write(
						printStream,
						item, true, false, true);
			}
			try (PrintStream printStream = new PrintStream(new PipeTo(
					new File(subdir, prefix + "LogicalFlowBreakdownClustered.svg"),
					Runtime.getRuntime().exec("dot -Tsvg")).start())) {
				LogicalBreakdown.write(
						printStream,
						item, false, true, true);
			}
			try (PrintStream printStream = new PrintStream(new PipeTo(
					new File(subdir, prefix + "PhysicalDesign.svg"),
					Runtime.getRuntime().exec("dot -Tsvg")).start())) {
				PhysicalSchematic.write(printStream, item);
			}
			try (PrintStream printStream = new PrintStream(new PipeTo(
					new File(subdir, prefix + "LogicalDesign.svg"),
					Runtime.getRuntime().exec("dot -Tsvg")).start())) {
				LogicalSchematic.write(printStream, item);
			}
		}

		for (Item subsystem : item.getChildren().values()) {
			writeSchematics(subdir, subsystem);
		}
	}
}

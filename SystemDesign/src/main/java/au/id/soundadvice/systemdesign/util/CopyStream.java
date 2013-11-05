/*
 * Please refer to the LICENSE file for licensing information.
 */
package au.id.soundadvice.systemdesign.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class CopyStream {

	private static final Logger LOG = Logger.getLogger(CopyStream.class.getName());
	private static final ExecutorService executor = Executors.newCachedThreadPool(
			new NamedThreads("CopyStream"));

	private final OutputStream to;
	private final InputStream from;

	/**
	 * Copies the contents of "from" to "to" in a background thread
	 *
	 * @param to Copy to this stream
	 * @param from Copy from this stream
	 */
	public CopyStream(OutputStream to, InputStream from) {
		this.to = to;
		this.from = from;
	}

	/**
	 * Start the copy process
	 */
	public void start() {
		executor.submit(new Run());
	}

	private class Run implements Runnable {

		@Override
		public void run() {
			try {
				for (;;) {
					byte[] bytes = new byte[1024 * 16];
					int readResult = from.read(bytes);
					if (readResult == -1) {
						break;
					}
					to.write(bytes, 0, readResult);
				}
			} catch (RuntimeException ex) {
				LOG.log(Level.SEVERE, "Failed to copy stream: " + ex, ex);
			} catch (IOException ex) {
				LOG.log(Level.WARNING, "Failed to copy stream: " + ex, ex);
			} finally {
				try {
					to.close();
				} catch (IOException ex) {
					LOG.log(Level.WARNING, "Failed to close stream: " + ex, ex);
				}
				try {
					from.close();
				} catch (IOException ex) {
					LOG.log(Level.WARNING, "Failed to close stream: " + ex, ex);
				}
			}
		}
	}
}

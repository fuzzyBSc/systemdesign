/*
 * Please refer to the LICENSE file for licensing information.
 */
package au.id.soundadvice.systemdesign.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread factory that names the created threads for easier debugging
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
class NamedThreads implements ThreadFactory {

	private final String prefix;
	private final AtomicInteger count = new AtomicInteger();

	public NamedThreads(String prefix) {
		this.prefix = prefix;
	}

	@Override
	public Thread newThread(Runnable r) {
		return new Thread(r, prefix + " #" + count.incrementAndGet());
	}

}

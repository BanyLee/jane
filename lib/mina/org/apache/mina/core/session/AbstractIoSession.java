/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.mina.core.session;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.file.DefaultFileRegion;
import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.DefaultCloseFuture;
import org.apache.mina.core.future.DefaultWriteFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.write.DefaultWriteRequest;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.core.write.WriteRequestQueue;
import org.apache.mina.core.write.WriteToClosedSessionException;
import org.apache.mina.util.ExceptionMonitor;

/**
 * Base implementation of {@link IoSession}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class AbstractIoSession implements IoSession {
	/** An internal write request object that triggers session close */
	public static final WriteRequest CLOSE_REQUEST = new DefaultWriteRequest("CLOSE_REQUEST");

	/** An id generator guaranteed to generate unique IDs for the session */
	private static final AtomicLong idGenerator = new AtomicLong();

	/** The associated handler */
	private final IoHandler handler;

	/** The session config */
	protected IoSessionConfig config;

	/** The service which will manage this session */
	private final IoService service;

	private IoSessionAttributeMap attributes;
	private Object attachment;

	private WriteRequestQueue writeRequestQueue;

	private WriteRequest currentWriteRequest;

	/** The session ID */
	private final long sessionId;

	/** A future that will be set 'closed' when the connection is closed */
	private final CloseFuture closeFuture = new DefaultCloseFuture(this);

	// Status variables
	private final AtomicBoolean scheduledForFlush = new AtomicBoolean();

	private volatile boolean closing;

	private boolean deferDecreaseReadBuffer = true;

	// traffic control
	private boolean readSuspended;

	private boolean writeSuspended;

	/**
	 * Create a Session for a service
	 *
	 * @param service the Service for this session
	 */
	protected AbstractIoSession(IoService service) {
		this.service = service;
		handler = service.getHandler();

		// Set a new ID for this session
		sessionId = idGenerator.incrementAndGet();
	}

	/**
	 * We use an AtomicLong to guarantee that the session ID are unique.
	 */
	@Override
	public final long getId() {
		return sessionId;
	}

	/**
	 * @return The associated IoProcessor for this session
	 */
	@SuppressWarnings("rawtypes")
	public abstract IoProcessor getProcessor();

	@Override
	public final boolean isConnected() {
		return !closeFuture.isClosed();
	}

	@Override
	public boolean isActive() {
		// Return true by default
		return true;
	}

	@Override
	public final boolean isClosing() {
		return closing || closeFuture.isClosed();
	}

	@Override
	public final CloseFuture getCloseFuture() {
		return closeFuture;
	}

	/**
	 * Tells if the session is scheduled for flushed
	 *
	 * @return true if the session is scheduled for flush
	 */
	public final boolean isScheduledForFlush() {
		return scheduledForFlush.get();
	}

	/**
	 * Schedule the session for flushed
	 */
	public final void scheduledForFlush() {
		scheduledForFlush.set(true);
	}

	/**
	 * Change the session's status: it's not anymore scheduled for flush
	 */
	public final void unscheduledForFlush() {
		scheduledForFlush.set(false);
	}

	/**
	 * Set the scheduledForFLush flag. As we may have concurrent access to this
	 * flag, we compare and set it in one call.
	 *
	 * @param schedule
	 *            the new value to set if not already set.
	 * @return true if the session flag has been set, and if it wasn't set
	 *         already.
	 */
	public final boolean setScheduledForFlush(boolean schedule) {
		if (schedule) {
			// If the current tag is set to false, switch it to true,
			// otherwise, we do nothing but return false: the session
			// is already scheduled for flush
			return scheduledForFlush.compareAndSet(false, schedule);
		}

		scheduledForFlush.set(schedule);
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public final CloseFuture closeOnFlush() {
		if (!isClosing()) {
			getWriteRequestQueue().offer(CLOSE_REQUEST);
			getProcessor().flush(this);
		}

		return closeFuture;
	}

	@Override
	public final CloseFuture closeNow() {
		synchronized (scheduledForFlush) {
			if (isClosing()) {
				return closeFuture;
			}

			closing = true;

			try {
				destroy();
			} catch (Exception e) {
				getFilterChain().fireExceptionCaught(e);
			}
		}

		getFilterChain().fireFilterClose();

		return closeFuture;
	}

	/**
	 * Destroy the session
	 */
	protected void destroy() {
		if (writeRequestQueue != null) {
			while (!writeRequestQueue.isEmpty()) {
				WriteRequest writeRequest = writeRequestQueue.poll();

				if (writeRequest != null) {
					WriteFuture writeFuture = writeRequest.getFuture();

					// The WriteRequest may not always have a future: The CLOSE_REQUEST
					// and MESSAGE_SENT_REQUEST don't.
					if (writeFuture != null) {
						writeFuture.setWritten();
					}
				}
			}
		}
	}

	@Override
	public IoHandler getHandler() {
		return handler;
	}

	@Override
	public IoSessionConfig getConfig() {
		return config;
	}

	@Override
	public WriteFuture write(Object message) {
		if (message == null) {
			throw new IllegalArgumentException("Trying to write a null message: not allowed");
		}

		// If the session has been closed or is closing, we can't either
		// send a message to the remote side. We generate a future
		// containing an exception.
		if (isClosing() || !isConnected()) {
			WriteFuture future = new DefaultWriteFuture(this);
			WriteRequest request = new DefaultWriteRequest(message, future);
			future.setException(new WriteToClosedSessionException(request));
			return future;
		}

		try {
			if ((message instanceof IoBuffer) && !((IoBuffer) message).hasRemaining()) {
				// Nothing to write: probably an error in the user code
				throw new IllegalArgumentException("message is empty. Forgot to call flip()?");
			} else if (message instanceof FileChannel) {
				FileChannel fileChannel = (FileChannel) message;
				message = new DefaultFileRegion(fileChannel, 0, fileChannel.size());
			}
		} catch (IOException e) {
			ExceptionMonitor.getInstance().exceptionCaught(e);
			return DefaultWriteFuture.newNotWrittenFuture(this, e);
		}

		// Now, we can write the message. First, create a future
		WriteFuture writeFuture = new DefaultWriteFuture(this);
		WriteRequest writeRequest = new DefaultWriteRequest(message, writeFuture);

		// Then, get the chain and inject the WriteRequest into it
		getFilterChain().fireFilterWrite(writeRequest);

		// Return the WriteFuture.
		return writeFuture;
	}

	@Override
	public final Object getAttachment() {
		return attachment;
	}

	@Override
	public final Object setAttachment(Object attachment) {
		Object old = this.attachment;
		this.attachment = attachment;
		return old;
	}

	@Override
	public final Object getAttribute(Object key) {
		return getAttribute(key, null);
	}

	@Override
	public final Object getAttribute(Object key, Object defaultValue) {
		return attributes.getAttribute(key, defaultValue);
	}

	@Override
	public final Object setAttribute(Object key, Object value) {
		return attributes.setAttribute(key, value);
	}

	@Override
	public final Object setAttribute(Object key) {
		return setAttribute(key, Boolean.TRUE);
	}

	@Override
	public final Object setAttributeIfAbsent(Object key, Object value) {
		return attributes.setAttributeIfAbsent(key, value);
	}

	@Override
	public final Object setAttributeIfAbsent(Object key) {
		return setAttributeIfAbsent(key, Boolean.TRUE);
	}

	@Override
	public final Object removeAttribute(Object key) {
		return attributes.removeAttribute(key);
	}

	@Override
	public final boolean removeAttribute(Object key, Object value) {
		return attributes.removeAttribute(key, value);
	}

	@Override
	public final boolean replaceAttribute(Object key, Object oldValue, Object newValue) {
		return attributes.replaceAttribute(key, oldValue, newValue);
	}

	@Override
	public final boolean containsAttribute(Object key) {
		return attributes.containsAttribute(key);
	}

	@Override
	public final Set<Object> getAttributeKeys() {
		return attributes.getAttributeKeys();
	}

	/**
	 * @return The map of attributes associated with the session
	 */
	public final IoSessionAttributeMap getAttributeMap() {
		return attributes;
	}

	/**
	 * Set the map of attributes associated with the session
	 *
	 * @param attributes The Map of attributes
	 */
	public final void setAttributeMap(IoSessionAttributeMap attributes) {
		this.attributes = attributes;
	}

	/**
	 * Create a new close aware write queue, based on the given write queue.
	 *
	 * @param writeRequestQueue The write request queue
	 */
	public final void setWriteRequestQueue(WriteRequestQueue writeRequestQueue) {
		this.writeRequestQueue = writeRequestQueue;
	}

	@SuppressWarnings("unchecked")
	@Override
	public final void suspendRead() {
		readSuspended = true;
		if (isClosing() || !isConnected()) {
			return;
		}
		getProcessor().updateTrafficControl(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public final void suspendWrite() {
		writeSuspended = true;
		if (isClosing() || !isConnected()) {
			return;
		}
		getProcessor().updateTrafficControl(this);
	}

	@Override
	@SuppressWarnings("unchecked")
	public final void resumeRead() {
		readSuspended = false;
		if (isClosing() || !isConnected()) {
			return;
		}
		getProcessor().updateTrafficControl(this);
	}

	@Override
	@SuppressWarnings("unchecked")
	public final void resumeWrite() {
		writeSuspended = false;
		if (isClosing() || !isConnected()) {
			return;
		}
		getProcessor().updateTrafficControl(this);
	}

	@Override
	public boolean isReadSuspended() {
		return readSuspended;
	}

	@Override
	public boolean isWriteSuspended() {
		return writeSuspended;
	}

	@Override
	public final WriteRequestQueue getWriteRequestQueue() {
		if (writeRequestQueue == null) {
			throw new IllegalStateException();
		}

		return writeRequestQueue;
	}

	@Override
	public final WriteRequest getCurrentWriteRequest() {
		return currentWriteRequest;
	}

	@Override
	public final void setCurrentWriteRequest(WriteRequest currentWriteRequest) {
		this.currentWriteRequest = currentWriteRequest;
	}

	/**
	 * Increase the ReadBuffer size (it will double)
	 */
	public final void increaseReadBufferSize() {
		IoSessionConfig cfg = getConfig();
		int readBufferSize = cfg.getReadBufferSize() << 1;
		if (readBufferSize <= cfg.getMaxReadBufferSize()) {
			cfg.setReadBufferSize(readBufferSize);
		}

		deferDecreaseReadBuffer = true;
	}

	/**
	 * Decrease the ReadBuffer size (it will be divided by a factor 2)
	 */
	public final void decreaseReadBufferSize() {
		if (deferDecreaseReadBuffer) {
			deferDecreaseReadBuffer = false;
			return;
		}

		IoSessionConfig cfg = getConfig();
		int readBufferSize = cfg.getReadBufferSize() >> 1;
		if (readBufferSize >= cfg.getMinReadBufferSize()) {
			cfg.setReadBufferSize(readBufferSize);
		}

		deferDecreaseReadBuffer = true;
	}

	@Override
	public InetSocketAddress getServiceAddress() {
		IoService ioService = getService();
		return ioService instanceof IoAcceptor ? ((IoAcceptor)ioService).getLocalAddress() :
				getRemoteAddress();
	}

	@Override
	public String toString() {
		if (isConnected() || isClosing()) {
			String remote;
			String local = null;

			try {
				remote = String.valueOf(getRemoteAddress());
			} catch (Exception e) {
				remote = "Cannot get the remote address informations: " + e.getMessage();
			}

			try {
				local = String.valueOf(getLocalAddress());
			} catch (Exception e) {
			}

			if (getService() instanceof IoAcceptor) {
				return '(' + getIdAsString() + ": nio socket, server, " + remote + " => " + local + ')';
			}

			return '(' + getIdAsString() + ": nio socket, client, " + local + " => " + remote + ')';
		}

		return '(' + getIdAsString() + ") Session disconnected ...";
	}

	/**
	 * Get the Id as a String
	 */
	private String getIdAsString() {
		String id = Long.toHexString(getId()).toUpperCase();
		return id.length() <= 8 ? "0x00000000".substring(0, 10 - id.length()) + id : "0x" + id;
	}

	@Override
	public IoService getService() {
		return service;
	}
}

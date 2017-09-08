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
package org.apache.mina.transport.socket.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Iterator;
import org.apache.mina.core.polling.AbstractPollingIoConnector;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.service.SimpleIoProcessorPool;
import org.apache.mina.transport.socket.SocketConnector;

/**
 * {@link IoConnector} for socket transport (TCP/IP).
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public final class NioSocketConnector extends AbstractPollingIoConnector implements SocketConnector {

	private Selector selector;

	/**
	 * Constructor for {@link NioSocketConnector} with default configuration (multiple thread model).
	 */
	public NioSocketConnector() {
		super();
	}

	/**
	 * Constructor for {@link NioSocketConnector} with default configuration, and
	 * given number of {@link NioProcessor} for multithreading I/O operations
	 * @param processorCount the number of processor to create and place in a
	 * {@link SimpleIoProcessorPool}
	 */
	public NioSocketConnector(int processorCount) {
		super(processorCount);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void init() throws Exception {
		selector = Selector.open();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void destroy() throws Exception {
		if (selector != null) {
			selector.close();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public InetSocketAddress getDefaultRemoteAddress() {
		return (InetSocketAddress) super.getDefaultRemoteAddress();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setDefaultRemoteAddress(InetSocketAddress defaultRemoteAddress) {
		super.setDefaultRemoteAddress(defaultRemoteAddress);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Iterator<SocketChannel> allHandles() {
		return new SocketChannelIterator(selector.keys());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean connect(SocketChannel handle, SocketAddress remoteAddress) throws Exception {
		return handle.connect(remoteAddress);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected ConnectionRequest getConnectionRequest(SocketChannel handle) {
		SelectionKey key = handle.keyFor(selector);
		return key != null && key.isValid() ? (ConnectionRequest) key.attachment() : null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void close(SocketChannel handle) throws Exception {
		SelectionKey key = handle.keyFor(selector);

		if (key != null) {
			key.cancel();
		}

		handle.close();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean finishConnect(SocketChannel handle) throws Exception {
		if (handle.finishConnect()) {
			SelectionKey key = handle.keyFor(selector);

			if (key != null) {
				key.cancel();
			}

			return true;
		}

		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected SocketChannel newHandle(SocketAddress localAddress) throws Exception {
		@SuppressWarnings("resource")
		SocketChannel ch = SocketChannel.open();

		int receiveBufferSize = (getSessionConfig()).getReceiveBufferSize();

		if (receiveBufferSize > 65535) {
			ch.socket().setReceiveBufferSize(receiveBufferSize);
		}

		if (localAddress != null) {
			try {
				ch.socket().bind(localAddress);
			} catch (IOException ioe) {
				// Add some info regarding the address we try to bind to the message
				String newMessage = "Error while binding on " + localAddress + "\noriginal message : " + ioe.getMessage();
				Exception e = new IOException(newMessage);
				e.initCause(ioe.getCause());

				// Preemptively close the channel
				ch.close();
				throw e;
			}
		}

		ch.configureBlocking(false);

		return ch;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected NioSession newSession(IoProcessor<NioSession> processor, SocketChannel handle) {
		return new NioSocketSession(this, processor, handle);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void register(SocketChannel handle, ConnectionRequest request) throws Exception {
		handle.register(selector, SelectionKey.OP_CONNECT, request);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected int select(long timeout) throws Exception {
		return selector.select(timeout);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Iterator<SocketChannel> selectedHandles() {
		return new SocketChannelIterator(selector.selectedKeys());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void wakeup() {
		selector.wakeup();
	}

	private static final class SocketChannelIterator implements Iterator<SocketChannel> {

		private final Iterator<SelectionKey> i;

		private SocketChannelIterator(Collection<SelectionKey> selectedKeys) {
			i = selectedKeys.iterator();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean hasNext() {
			return i.hasNext();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public SocketChannel next() {
			SelectionKey key = i.next();
			return (SocketChannel) key.channel();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void remove() {
			i.remove();
		}
	}
}

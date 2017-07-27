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
package org.apache.mina.core.service;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.core.session.IoSessionInitializer;

/**
 * A base implementation of {@link IoConnector}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class AbstractIoConnector extends AbstractIoService implements IoConnector {
	/**
	 * The minimum timeout value that is supported (in milliseconds).
	 */
	private long connectTimeoutCheckInterval = 50L;

	private long connectTimeoutInMillis = 60 * 1000L; // 1 minute by default

	/** The remote address we are connected to */
	private SocketAddress defaultRemoteAddress;

	/** The local address */
	private SocketAddress defaultLocalAddress;

	/**
	 * Constructor for {@link AbstractIoConnector}. You need to provide a
	 * default session configuration and an {@link Executor} for handling I/O
	 * events. If null {@link Executor} is provided, a default one will be
	 * created using {@link Executors#newCachedThreadPool()}.
	 *
	 * @see AbstractIoService#AbstractIoService(IoSessionConfig, Executor)
	 *
	 * @param sessionConfig
	 *            the default configuration for the managed {@link IoSession}
	 * @param executor
	 *            the {@link Executor} used for handling execution of I/O
	 *            events. Can be <code>null</code>.
	 */
	protected AbstractIoConnector(IoSessionConfig sessionConfig, Executor executor) {
		super(sessionConfig, executor);
	}

	/**
	* @return
	 *  The minimum time that this connector can have for a connection
	 *  timeout in milliseconds.
	 */
	public long getConnectTimeoutCheckInterval() {
		return connectTimeoutCheckInterval;
	}

	/**
	 * Sets the timeout for the connection check
	 *
	 * @param minimumConnectTimeout The delay we wait before checking the connection
	 */
	public void setConnectTimeoutCheckInterval(long minimumConnectTimeout) {
		if (getConnectTimeoutMillis() < minimumConnectTimeout) {
			connectTimeoutInMillis = minimumConnectTimeout;
		}

		connectTimeoutCheckInterval = minimumConnectTimeout;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final long getConnectTimeoutMillis() {
		return connectTimeoutInMillis;
	}

	/**
	 * Sets the connect timeout value in milliseconds.
	 *
	 */
	@Override
	public final void setConnectTimeoutMillis(long connectTimeoutInMillis) {
		if (connectTimeoutInMillis <= connectTimeoutCheckInterval) {
			connectTimeoutCheckInterval = connectTimeoutInMillis;
		}
		this.connectTimeoutInMillis = connectTimeoutInMillis;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SocketAddress getDefaultRemoteAddress() {
		return defaultRemoteAddress;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void setDefaultLocalAddress(SocketAddress localAddress) {
		defaultLocalAddress = localAddress;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final SocketAddress getDefaultLocalAddress() {
		return defaultLocalAddress;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void setDefaultRemoteAddress(SocketAddress defaultRemoteAddress) {
		if (defaultRemoteAddress == null) {
			throw new IllegalArgumentException("defaultRemoteAddress");
		}

		if (!InetSocketAddress.class.isAssignableFrom(defaultRemoteAddress.getClass())) {
			throw new IllegalArgumentException("defaultRemoteAddress type: " + defaultRemoteAddress.getClass()
					+ " (expected: InetSocketAddress)");
		}
		this.defaultRemoteAddress = defaultRemoteAddress;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectFuture connect() {
		SocketAddress remoteAddress = getDefaultRemoteAddress();

		if (remoteAddress == null) {
			throw new IllegalStateException("defaultRemoteAddress is not set.");
		}

		return connect(remoteAddress, null, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConnectFuture connect(IoSessionInitializer<? extends ConnectFuture> sessionInitializer) {
		SocketAddress remoteAddress = getDefaultRemoteAddress();

		if (remoteAddress == null) {
			throw new IllegalStateException("defaultRemoteAddress is not set.");
		}

		return connect(remoteAddress, null, sessionInitializer);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectFuture connect(SocketAddress remoteAddress) {
		return connect(remoteAddress, null, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConnectFuture connect(SocketAddress remoteAddress,
			IoSessionInitializer<? extends ConnectFuture> sessionInitializer) {
		return connect(remoteAddress, null, sessionInitializer);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConnectFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
		return connect(remoteAddress, localAddress, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectFuture connect(SocketAddress remoteAddress, SocketAddress localAddress,
			IoSessionInitializer<? extends ConnectFuture> sessionInitializer) {
		if (isDisposing()) {
			throw new IllegalStateException("The connector is being disposed.");
		}

		if (remoteAddress == null) {
			throw new IllegalArgumentException("remoteAddress");
		}

		if (!InetSocketAddress.class.isAssignableFrom(remoteAddress.getClass())) {
			throw new IllegalArgumentException("remoteAddress type: " + remoteAddress.getClass() + " (expected: InetSocketAddress)");
		}

		if (localAddress != null && !InetSocketAddress.class.isAssignableFrom(localAddress.getClass())) {
			throw new IllegalArgumentException("localAddress type: " + localAddress.getClass() + " (expected: InetSocketAddress)");
		}

		if (getHandler() == null) {
			throw new IllegalStateException("handler is not set.");
		}

		return connect0(remoteAddress, localAddress, sessionInitializer);
	}

	/**
	 * Implement this method to perform the actual connect operation.
	 *
	 * @param remoteAddress The remote address to connect from
	 * @param localAddress <tt>null</tt> if no local address is specified
	 * @param sessionInitializer The IoSessionInitializer to use when the connection s successful
	 * @return The ConnectFuture associated with this asynchronous operation
	 *
	 */
	protected abstract ConnectFuture connect0(SocketAddress remoteAddress, SocketAddress localAddress,
			IoSessionInitializer<? extends ConnectFuture> sessionInitializer);

	/**
	 * Adds required internal attributes and {@link IoFutureListener}s
	 * related with event notifications to the specified {@code session}
	 * and {@code future}.  Do not call this method directly;
	 */
	@Override
	protected final void finishSessionInitialization0(final IoSession session, IoFuture future) {
		// In case that ConnectFuture.cancel() is invoked before
		// setSession() is invoked, add a listener that closes the
		// connection immediately on cancellation.
		future.addListener(new IoFutureListener<ConnectFuture>() {
			/**
			 * {@inheritDoc}
			 */
			@Override
			public void operationComplete(ConnectFuture future1) {
				if (future1.isCanceled()) {
					session.closeNow();
				}
			}
		});
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "(nio socket connector: managedSessionCount: " + getManagedSessionCount() + ')';
	}
}

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
 */
package org.apache.mina.core.filterchain;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;

/**
 * An adapter class for {@link IoFilter}.
 * You can extend this class and selectively override required event filter methods only.
 * All methods forwards events to the next filter by default.
 */
public class IoFilterAdapter implements IoFilter {
	@Override
	public void onPreAdd(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
	}

	@Override
	public void onPostAdd(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
	}

	@Override
	public void onPreRemove(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
	}

	@Override
	public void onPostRemove(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
	}

	@Override
	public void sessionCreated(NextFilter nextFilter, IoSession session) throws Exception {
		nextFilter.sessionCreated();
	}

	@Override
	public void sessionOpened(NextFilter nextFilter, IoSession session) throws Exception {
		nextFilter.sessionOpened();
	}

	@Override
	public void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception {
		nextFilter.sessionClosed();
	}

	@Override
	public void exceptionCaught(NextFilter nextFilter, IoSession session, Throwable cause) throws Exception {
		nextFilter.exceptionCaught(cause);
	}

	@Override
	public void inputClosed(NextFilter nextFilter, IoSession session) throws Exception {
		nextFilter.inputClosed();
	}

	@Override
	public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
		nextFilter.messageReceived(message);
	}

	@Override
	public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
		nextFilter.filterWrite(writeRequest);
	}

	@Override
	public void filterClose(NextFilter nextFilter, IoSession session) throws Exception {
		nextFilter.filterClose();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}

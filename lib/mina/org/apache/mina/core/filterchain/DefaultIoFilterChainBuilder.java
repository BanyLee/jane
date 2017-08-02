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
package org.apache.mina.core.filterchain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.mina.core.filterchain.IoFilter.NextFilter;
import org.apache.mina.core.filterchain.IoFilterChain.Entry;
import org.apache.mina.core.session.IoSession;

/**
 * The default implementation of {@link IoFilterChainBuilder} which is useful
 * in most cases.  {@link DefaultIoFilterChainBuilder} has an identical interface
 * with {@link IoFilter}; it contains a list of {@link IoFilter}s that you can
 * modify. The {@link IoFilter}s which are added to this builder will be appended
 * to the {@link IoFilterChain} when {@link #buildFilterChain(IoFilterChain)} is
 * invoked.
 * <p>
 * However, the identical interface doesn't mean that it behaves in an exactly
 * same way with {@link IoFilterChain}.  {@link DefaultIoFilterChainBuilder}
 * doesn't manage the life cycle of the {@link IoFilter}s at all, and the
 * existing {@link IoSession}s won't get affected by the changes in this builder.
 * {@link IoFilterChainBuilder}s affect only newly created {@link IoSession}s.
 *
 * <pre>
 * IoAcceptor acceptor = ...;
 * DefaultIoFilterChainBuilder builder = acceptor.getFilterChain();
 * builder.addLast( "myFilter", new MyFilter() );
 * ...
 * </pre>
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * @org.apache.xbean.XBean
 */
public final class DefaultIoFilterChainBuilder implements IoFilterChainBuilder {
	/** The list of filters */
	private final List<Entry> entries;

	/**
	 * Creates a new instance with an empty filter list.
	 */
	public DefaultIoFilterChainBuilder() {
		entries = new CopyOnWriteArrayList<>();
	}

	/**
	 * Creates a new copy of the specified {@link DefaultIoFilterChainBuilder}.
	 *
	 * @param filterChain The FilterChain we will copy
	 */
	public DefaultIoFilterChainBuilder(DefaultIoFilterChainBuilder filterChain) {
		if (filterChain == null) {
			throw new IllegalArgumentException("filterChain");
		}
		entries = new CopyOnWriteArrayList<>(filterChain.entries);
	}

	/**
	 * @see IoFilterChain#getEntry(String)
	 *
	 * @param name The Filter's name we are looking for
	 * @return The found Entry
	 */
	public Entry getEntry(String name) {
		for (Entry e : entries) {
			if (e.getName().equals(name)) {
				return e;
			}
		}

		return null;
	}

	/**
	 * @see IoFilterChain#getEntry(IoFilter)
	 *
	 * @param filter The Filter we are looking for
	 * @return The found Entry
	 */
	public Entry getEntry(IoFilter filter) {
		for (Entry e : entries) {
			if (e.getFilter() == filter) {
				return e;
			}
		}

		return null;
	}

	/**
	 * @see IoFilterChain#getEntry(Class)
	 *
	 * @param filterType The FilterType we are looking for
	 * @return The found Entry
	 */
	public Entry getEntry(Class<? extends IoFilter> filterType) {
		for (Entry e : entries) {
			if (filterType.isAssignableFrom(e.getFilter().getClass())) {
				return e;
			}
		}

		return null;
	}

	/**
	 * @see IoFilterChain#get(String)
	 *
	 * @param name The Filter's name we are looking for
	 * @return The found Filter, or null
	 */
	public IoFilter get(String name) {
		Entry e = getEntry(name);
		return e != null ? e.getFilter() : null;
	}

	/**
	 * @see IoFilterChain#get(Class)
	 *
	 * @param filterType The FilterType we are looking for
	 * @return The found Filter, or null
	 */
	public IoFilter get(Class<? extends IoFilter> filterType) {
		Entry e = getEntry(filterType);
		return e != null ? e.getFilter() : null;
	}

	/**
	 * @see IoFilterChain#getAll()
	 *
	 * @return The list of Filters
	 */
	public List<Entry> getAll() {
		return new ArrayList<>(entries);
	}

	/**
	 * @see IoFilterChain#getAllReversed()
	 *
	 * @return The list of Filters, reversed
	 */
	public List<Entry> getAllReversed() {
		List<Entry> result = getAll();
		Collections.reverse(result);

		return result;
	}

	/**
	 * @see IoFilterChain#contains(String)
	 *
	 * @param name The Filter's name we want to check if it's in the chain
	 * @return <tt>true</tt> if the chain contains the given filter name
	 */
	public boolean contains(String name) {
		return getEntry(name) != null;
	}

	/**
	 * @see IoFilterChain#contains(IoFilter)
	 *
	 * @param filter The Filter we want to check if it's in the chain
	 * @return <tt>true</tt> if the chain contains the given filter
	 */
	public boolean contains(IoFilter filter) {
		return getEntry(filter) != null;
	}

	/**
	 * @see IoFilterChain#contains(Class)
	 *
	 * @param filterType The FilterType we want to check if it's in the chain
	 * @return <tt>true</tt> if the chain contains the given filterType
	 */
	public boolean contains(Class<? extends IoFilter> filterType) {
		return getEntry(filterType) != null;
	}

	/**
	 * @see IoFilterChain#addFirst(String, IoFilter)
	 *
	 * @param name The filter's name
	 * @param filter The filter to add
	 */
	public synchronized void addFirst(String name, IoFilter filter) {
		register(0, new EntryImpl(name, filter));
	}

	/**
	 * @see IoFilterChain#addLast(String, IoFilter)
	 *
	 * @param name The filter's name
	 * @param filter The filter to add
	 */
	public synchronized void addLast(String name, IoFilter filter) {
		register(entries.size(), new EntryImpl(name, filter));
	}

	/**
	 * @see IoFilterChain#addBefore(String, String, IoFilter)
	 *
	 * @param baseName The filter baseName
	 * @param name The filter's name
	 * @param filter The filter to add
	 */
	public synchronized void addBefore(String baseName, String name, IoFilter filter) {
		checkBaseName(baseName);

		for (ListIterator<Entry> i = entries.listIterator(); i.hasNext();) {
			if (i.next().getName().equals(baseName)) {
				register(i.previousIndex(), new EntryImpl(name, filter));
				break;
			}
		}
	}

	/**
	 * @see IoFilterChain#addAfter(String, String, IoFilter)
	 *
	 * @param baseName The filter baseName
	 * @param name The filter's name
	 * @param filter The filter to add
	 */
	public synchronized void addAfter(String baseName, String name, IoFilter filter) {
		checkBaseName(baseName);

		for (ListIterator<Entry> i = entries.listIterator(); i.hasNext();) {
			if (i.next().getName().equals(baseName)) {
				register(i.nextIndex(), new EntryImpl(name, filter));
				break;
			}
		}
	}

	/**
	 * @see IoFilterChain#remove(String)
	 *
	 * @param name The Filter's name to remove from the list of Filters
	 * @return The removed IoFilter
	 */
	public synchronized IoFilter remove(String name) {
		if (name == null) {
			throw new IllegalArgumentException("name");
		}

		for (ListIterator<Entry> i = entries.listIterator(); i.hasNext();) {
			Entry e = i.next();

			if (e.getName().equals(name)) {
				entries.remove(i.previousIndex());

				return e.getFilter();
			}
		}

		throw new IllegalArgumentException("Unknown filter name: " + name);
	}

	/**
	 * @see IoFilterChain#remove(IoFilter)
	 *
	 * @param filter The Filter we want to remove from the list of Filters
	 * @return The removed IoFilter
	 */
	public synchronized IoFilter remove(IoFilter filter) {
		if (filter == null) {
			throw new IllegalArgumentException("filter");
		}

		for (ListIterator<Entry> i = entries.listIterator(); i.hasNext();) {
			Entry e = i.next();

			if (e.getFilter() == filter) {
				entries.remove(i.previousIndex());

				return e.getFilter();
			}
		}

		throw new IllegalArgumentException("Filter not found: " + filter.getClass().getName());
	}

	/**
	 * @see IoFilterChain#remove(Class)
	 *
	 * @param filterType The FilterType we want to remove from the list of Filters
	 * @return The removed IoFilter
	 */
	public synchronized IoFilter remove(Class<? extends IoFilter> filterType) {
		if (filterType == null) {
			throw new IllegalArgumentException("filterType");
		}

		for (ListIterator<Entry> i = entries.listIterator(); i.hasNext();) {
			Entry e = i.next();

			if (filterType.isAssignableFrom(e.getFilter().getClass())) {
				entries.remove(i.previousIndex());

				return e.getFilter();
			}
		}

		throw new IllegalArgumentException("Filter not found: " + filterType.getName());
	}

	/**
	 * Replace a filter by a new one.
	 *
	 * @param name The name of the filter to replace
	 * @param newFilter The new filter to use
	 * @return The replaced filter
	 */
	public synchronized IoFilter replace(String name, IoFilter newFilter) {
		checkBaseName(name);
		EntryImpl e = (EntryImpl) getEntry(name);
		IoFilter oldFilter = e.getFilter();
		e.setFilter(newFilter);

		return oldFilter;
	}

	/**
	 * Replace a filter by a new one.
	 *
	 * @param oldFilter The filter to replace
	 * @param newFilter The new filter to use
	 */
	public synchronized void replace(IoFilter oldFilter, IoFilter newFilter) {
		for (Entry e : entries) {
			if (e.getFilter() == oldFilter) {
				((EntryImpl) e).setFilter(newFilter);

				return;
			}
		}

		throw new IllegalArgumentException("Filter not found: " + oldFilter.getClass().getName());
	}

	/**
	 * Replace a filter by a new one. We are looking for a filter type,
	 * but if we have more than one with the same type, only the first
	 * found one will be replaced
	 *
	 * @param oldFilterType The filter type to replace
	 * @param newFilter The new filter to use
	 */
	public synchronized void replace(Class<? extends IoFilter> oldFilterType, IoFilter newFilter) {
		for (Entry e : entries) {
			if (oldFilterType.isAssignableFrom(e.getFilter().getClass())) {
				((EntryImpl) e).setFilter(newFilter);

				return;
			}
		}

		throw new IllegalArgumentException("Filter not found: " + oldFilterType.getName());
	}

	/**
	 * @see IoFilterChain#clear()
	 */
	public synchronized void clear() {
		entries.clear();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void buildFilterChain(IoFilterChain chain) throws Exception {
		for (Entry e : entries) {
			chain.addLast(e.getName(), e.getFilter());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("{ ");

		boolean empty = true;

		for (Entry e : entries) {
			if (!empty) {
				buf.append(", ");
			} else {
				empty = false;
			}

			buf.append('(');
			buf.append(e.getName()).append(':').append(e.getFilter());
			buf.append(')');
		}

		if (empty) {
			buf.append("empty");
		}

		buf.append(" }");

		return buf.toString();
	}

	private void checkBaseName(String baseName) {
		if (baseName == null) {
			throw new IllegalArgumentException("baseName");
		}

		if (!contains(baseName)) {
			throw new IllegalArgumentException("Unknown filter name: " + baseName);
		}
	}

	private void register(int index, Entry e) {
		if (contains(e.getName())) {
			throw new IllegalArgumentException("Other filter is using the same name: " + e.getName());
		}

		entries.add(index, e);
	}

	private final class EntryImpl implements Entry {
		private final String name;

		private volatile IoFilter filter;

		private EntryImpl(String name, IoFilter filter) {
			if (name == null) {
				throw new IllegalArgumentException("name");
			}

			if (filter == null) {
				throw new IllegalArgumentException("filter");
			}

			this.name = name;
			this.filter = filter;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getName() {
			return name;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IoFilter getFilter() {
			return filter;
		}

		private void setFilter(IoFilter filter) {
			this.filter = filter;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public NextFilter getNextFilter() {
			throw new IllegalStateException();
		}

		@Override
		public String toString() {
			return '(' + getName() + ':' + filter + ')';
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void addAfter(String name1, IoFilter filter1) {
			DefaultIoFilterChainBuilder.this.addAfter(getName(), name1, filter1);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void addBefore(String name1, IoFilter filter1) {
			DefaultIoFilterChainBuilder.this.addBefore(getName(), name1, filter1);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void remove() {
			DefaultIoFilterChainBuilder.this.remove(getName());
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void replace(IoFilter newFilter) {
			DefaultIoFilterChainBuilder.this.replace(getName(), newFilter);
		}
	}
}

package jane.core;

import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import jane.core.SContext.Wrap;

/**
 * Deque类型的安全修改类
 * <p>
 * 只支持无容量限制的ArrayDeque,且不支持删除中间元素
 */
public final class SDeque<V> implements Deque<V>, Cloneable
{
	private final Wrap<?>  _owner;
	private final Deque<V> _deque;
	private SContext       _sctx;

	public SDeque(Wrap<?> owner, Deque<V> queue)
	{
		_owner = owner;
		_deque = queue;
	}

	private SContext sContext()
	{
		if(_sctx != null) return _sctx;
		_owner.dirty();
		return _sctx = SContext.current();
	}

	@Override
	public int size()
	{
		return _deque.size();
	}

	@Override
	public boolean isEmpty()
	{
		return _deque.isEmpty();
	}

	@Override
	public boolean contains(Object o)
	{
		return _deque.contains(o instanceof Wrap ? ((Wrap<?>)o).unsafe() : o);
	}

	@Override
	public boolean containsAll(Collection<?> c)
	{
		return _deque.containsAll(c);
	}

	@Override
	public Object[] toArray()
	{
		return _deque.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a)
	{
		return _deque.toArray(a);
	}

	@Override
	public V element()
	{
		return _deque.element();
	}

	@SuppressWarnings("unchecked")
	public <S extends Wrap<V>> S elementSafe()
	{
		V v = _deque.element();
		return v != null ? (S)(((Bean<?>)v).safe(_owner)) : null;
	}

	@Override
	public V peek()
	{
		return _deque.peek();
	}

	@SuppressWarnings("unchecked")
	public <S extends Wrap<V>> S peekSafe()
	{
		V v = _deque.peek();
		return v != null ? (S)(((Bean<?>)v).safe(_owner)) : null;
	}

	@Override
	public V getFirst()
	{
		return _deque.getFirst();
	}

	@SuppressWarnings("unchecked")
	public <S extends Wrap<V>> S getFirstSafe()
	{
		V v = _deque.getFirst();
		return v != null ? (S)(((Bean<?>)v).safe(_owner)) : null;
	}

	@Override
	public V getLast()
	{
		return _deque.getLast();
	}

	@SuppressWarnings("unchecked")
	public <S extends Wrap<V>> S getLastSafe()
	{
		V v = _deque.getLast();
		return v != null ? (S)(((Bean<?>)v).safe(_owner)) : null;
	}

	@Override
	public V peekFirst()
	{
		return _deque.peekFirst();
	}

	@SuppressWarnings("unchecked")
	public <S extends Wrap<V>> S peekFirstSafe()
	{
		V v = _deque.peekFirst();
		return v != null ? (S)(((Bean<?>)v).safe(_owner)) : null;
	}

	@Override
	public V peekLast()
	{
		return _deque.peekLast();
	}

	@SuppressWarnings("unchecked")
	public <S extends Wrap<V>> S peekLastSafe()
	{
		V v = _deque.peekLast();
		return v != null ? (S)(((Bean<?>)v).safe(_owner)) : null;
	}

	@Override
	public boolean add(V v)
	{
		if(!_deque.add(v)) return false;
		sContext().addOnRollback(new Runnable()
		{
			@Override
			public void run()
			{
				_deque.removeLast();
			}
		});
		return true;
	}

	public <S extends Wrap<V>> void add(S v)
	{
		add(v.unsafe());
	}

	@Override
	public void addFirst(V v)
	{
		_deque.addFirst(v);
		sContext().addOnRollback(new Runnable()
		{
			@Override
			public void run()
			{
				_deque.removeFirst();
			}
		});
	}

	public <S extends Wrap<V>> void addFirst(S v)
	{
		addFirst(v.unsafe());
	}

	@Override
	public void addLast(V v)
	{
		add(v);
	}

	public <S extends Wrap<V>> void addLast(S v)
	{
		add(v);
	}

	@Override
	public boolean offer(V v)
	{
		return add(v);
	}

	public <S extends Wrap<V>> void offer(S v)
	{
		add(v);
	}

	@Override
	public boolean offerFirst(V v)
	{
		addFirst(v);
		return true;
	}

	public <S extends Wrap<V>> void offerFirst(S v)
	{
		addFirst(v);
	}

	@Override
	public boolean offerLast(V v)
	{
		return add(v);
	}

	public <S extends Wrap<V>> void offerLast(S v)
	{
		add(v);
	}

	@Override
	public void push(V v)
	{
		addFirst(v);
	}

	public <S extends Wrap<V>> void push(S v)
	{
		addFirst(v);
	}

	@Override
	public boolean addAll(Collection<? extends V> c)
	{
		final int n = c.size();
		if(!_deque.addAll(c)) return false;
		sContext().addOnRollback(new Runnable()
		{
			@Override
			public void run()
			{
				for(int i = 0; i < n; ++i)
					_deque.removeLast();
			}
		});
		return true;
	}

	@Override
	public V remove()
	{
		final V v_old = _deque.remove();
		sContext().addOnRollback(new Runnable()
		{
			@Override
			public void run()
			{
				_deque.addFirst(v_old);
			}
		});
		return v_old;
	}

	@SuppressWarnings("unchecked")
	public <S extends Wrap<V>> S removeSafe()
	{
		V v_old = remove();
		return v_old != null ? (S)((Bean<?>)v_old).safe(_owner) : null;
	}

	@Override
	public boolean remove(Object o)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public V removeFirst()
	{
		return remove();
	}

	public <S extends Wrap<V>> S removeFirstSafe()
	{
		return removeSafe();
	}

	@Override
	public V removeLast()
	{
		final V v_old = _deque.removeLast();
		sContext().addOnRollback(new Runnable()
		{
			@Override
			public void run()
			{
				_deque.addLast(v_old);
			}
		});
		return v_old;
	}

	@SuppressWarnings("unchecked")
	public <S extends Wrap<V>> S removeLastSafe()
	{
		V v_old = removeLast();
		return v_old != null ? (S)((Bean<?>)v_old).safe(_owner) : null;
	}

	@Override
	public boolean removeFirstOccurrence(Object o)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeLastOccurrence(Object o)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public V poll()
	{
		final V v_old = _deque.poll();
		if(v_old == null) return null;
		sContext().addOnRollback(new Runnable()
		{
			@Override
			public void run()
			{
				_deque.addFirst(v_old);
			}
		});
		return v_old;
	}

	@SuppressWarnings("unchecked")
	public <S extends Wrap<V>> S pollSafe()
	{
		V v_old = poll();
		return v_old != null ? (S)((Bean<?>)v_old).safe(_owner) : null;
	}

	@Override
	public V pollFirst()
	{
		return poll();
	}

	public <S extends Wrap<V>> S pollFirstSafe()
	{
		return pollSafe();
	}

	@Override
	public V pollLast()
	{
		final V v_old = _deque.pollLast();
		if(v_old == null) return null;
		sContext().addOnRollback(new Runnable()
		{
			@Override
			public void run()
			{
				_deque.addLast(v_old);
			}
		});
		return v_old;
	}

	@SuppressWarnings("unchecked")
	public <S extends Wrap<V>> S pollLastSafe()
	{
		V v_old = pollLast();
		return v_old != null ? (S)((Bean<?>)v_old).safe(_owner) : null;
	}

	@Override
	public V pop()
	{
		return remove();
	}

	public <S extends Wrap<V>> S popSafe()
	{
		return removeSafe();
	}

	@Override
	public boolean removeAll(Collection<?> c)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c)
	{
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void clear()
	{
		if(_deque.isEmpty()) return;
		sContext().addOnRollback(new Runnable()
		{
			private final Deque<V> _saved;

			{
				try
				{
					_saved = _deque.getClass().newInstance();
					_saved.addAll(_deque);
				}
				catch(Exception e)
				{
					throw new RuntimeException(e);
				}
			}

			@Override
			public void run()
			{
				_deque.addAll(_saved);
				_saved.clear();
			}
		});
		_deque.clear();
	}

	public final class SIterator implements Iterator<V>
	{
		private final Iterator<V> _it;

		private SIterator(boolean descend)
		{
			_it = (descend ? _deque.descendingIterator() : _deque.iterator());
		}

		@Override
		public boolean hasNext()
		{
			return _it.hasNext();
		}

		@Override
		public V next()
		{
			return _it.next();
		}

		@SuppressWarnings("unchecked")
		public <S extends Wrap<V>> S nextSafe()
		{
			V v = _it.next();
			return v != null ? (S)((Bean<?>)v).safe(_owner) : null;
		}

		@Override
		public void remove()
		{
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public SIterator iterator()
	{
		return new SIterator(false);
	}

	@Override
	public Iterator<V> descendingIterator()
	{
		return new SIterator(true);
	}

	@Override
	public Object clone()
	{
		return new UnsupportedOperationException();
	}

	@Override
	public int hashCode()
	{
		return _deque.hashCode();
	}

	@Override
	public boolean equals(Object o)
	{
		return this == o || _deque.equals(o);
	}

	@Override
	public String toString()
	{
		return _deque.toString();
	}
}

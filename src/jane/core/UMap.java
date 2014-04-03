package jane.core;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import jane.core.UndoContext.Wrap;

/**
 * Map类型的回滚处理类
 * <p>
 * 不支持value为null
 */
public class UMap<K, V> implements Map<K, V>, Cloneable
{
	protected final Wrap<?> _owner;
	protected Map<K, V>     _map;
	private UndoContext     _undoctx;

	public UMap(Wrap<?> owner, Map<K, V> map)
	{
		_owner = owner;
		_map = map;
	}

	@SuppressWarnings("unchecked")
	protected <S extends Wrap<V>> S safe(V v)
	{
		return v != null ? (S)((Bean<?>)v).safe(_owner) : null;
	}

	private UndoContext undoContext()
	{
		if(_undoctx != null) return _undoctx;
		_owner.dirty();
		return _undoctx = UndoContext.current();
	}

	protected void addUndoPut(final K k, final V v_old)
	{
		undoContext().addOnRollback(new Runnable()
		{
			@Override
			public void run()
			{
				if(v_old != null)
					_map.put(k, v_old);
				else
					_map.remove(k);
			}
		});
	}

	protected void addUndoRemove(final K k, final V v_old)
	{
		undoContext().addOnRollback(new Runnable()
		{
			@Override
			public void run()
			{
				_map.put(k, v_old);
			}
		});
	}

	@Override
	public int size()
	{
		return _map.size();
	}

	@Override
	public boolean isEmpty()
	{
		return _map.isEmpty();
	}

	@Override
	public boolean containsKey(Object k)
	{
		return _map.containsKey(k);
	}

	@Override
	public boolean containsValue(Object v)
	{
		return _map.containsValue(v instanceof Wrap ? ((Wrap<?>)v).unsafe() : v);
	}

	@Override
	public V get(Object k)
	{
		return _map.get(k);
	}

	public <S extends Wrap<V>> S getSafe(Object k)
	{
		return safe(_map.get(k));
	}

	@Override
	public V put(K k, V v)
	{
		if(v == null) throw new NullPointerException();
		v = _map.put(k, v);
		addUndoPut(k, v);
		return v;
	}

	public <S extends Wrap<V>> S put(K k, S v)
	{
		return safe(put(k, v.unsafe()));
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m)
	{
		if(_map == m || this == m) return;
		for(Entry<? extends K, ? extends V> e : m.entrySet())
		{
			V v = e.getValue();
			if(v != null) put(e.getKey(), v);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public V remove(Object k)
	{
		final V v_old = _map.remove(k);
		if(v_old == null) return null;
		addUndoRemove((K)k, v_old);
		return v_old;
	}

	public <S extends Wrap<V>> S removeSafe(Object k)
	{
		return safe(remove(k));
	}

	@SuppressWarnings("unchecked")
	@Override
	public void clear()
	{
		if(_map.isEmpty()) return;
		undoContext().addOnRollback(new Runnable()
		{
			private final UMap<K, V> _saved = UMap.this;

			@Override
			public void run()
			{
				_map = _saved;
			}
		});
		try
		{
			_map = _map.getClass().newInstance();
		}
		catch(Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	public final class UEntry implements Entry<K, V>
	{
		private final Entry<K, V> _e;

		protected UEntry(Entry<K, V> e)
		{
			_e = e;
		}

		@Override
		public K getKey()
		{
			return _e.getKey();
		}

		@Override
		public V getValue()
		{
			return _e.getValue();
		}

		public <S extends Wrap<V>> S getValueSafe()
		{
			return safe(_e.getValue());
		}

		@Override
		public V setValue(V v)
		{
			if(v == null) throw new NullPointerException();
			v = _e.setValue(v);
			addUndoPut(_e.getKey(), v);
			return v;
		}

		public <S extends Wrap<V>> V setValue(S v)
		{
			return setValue(v.unsafe());
		}

		@Override
		public int hashCode()
		{
			return _e.hashCode();
		}

		@Override
		public boolean equals(Object o)
		{
			return this == o || _e.equals(o);
		}
	}

	public abstract class UIterator<E> implements Iterator<E>
	{
		private final Iterator<Entry<K, V>> _it = _map.entrySet().iterator();
		private UEntry                      _cur;

		@Override
		public boolean hasNext()
		{
			return _it.hasNext();
		}

		public UEntry nextEntry()
		{
			return _cur = new UEntry(_it.next());
		}

		@Override
		public void remove()
		{
			K k = _cur.getKey();
			V v = _cur.getValue();
			_it.remove();
			addUndoRemove(k, v);
		}
	}

	public final class UEntrySet extends AbstractSet<Entry<K, V>>
	{
		private Set<Entry<K, V>> _it;

		private UEntrySet()
		{
		}

		@Override
		public UIterator<Entry<K, V>> iterator()
		{
			return new UIterator<Entry<K, V>>()
			{
				@Override
				public UEntry next()
				{
					return nextEntry();
				}
			};
		}

		@Override
		public int size()
		{
			return UMap.this.size();
		}

		@Override
		public boolean contains(Object o)
		{
			if(_it == null) _it = _map.entrySet();
			return _it.contains(o);
		}

		@Override
		public boolean remove(Object o)
		{
			return UMap.this.remove(o instanceof Wrap ? ((Wrap<?>)o).unsafe() : o) != null;
		}

		@Override
		public void clear()
		{
			UMap.this.clear();
		}
	}

	public final class UKeySet extends AbstractSet<K>
	{
		private UKeySet()
		{
		}

		@Override
		public UIterator<K> iterator()
		{
			return new UIterator<K>()
			{
				@Override
				public K next()
				{
					return nextEntry().getKey();
				}
			};
		}

		@Override
		public int size()
		{
			return UMap.this.size();
		}

		@Override
		public boolean contains(Object o)
		{
			return UMap.this.containsKey(o instanceof Wrap ? ((Wrap<?>)o).unsafe() : o);
		}

		@Override
		public boolean remove(Object o)
		{
			return UMap.this.remove(o instanceof Wrap ? ((Wrap<?>)o).unsafe() : o) != null;
		}

		@Override
		public void clear()
		{
			UMap.this.clear();
		}
	}

	public final class UValues extends AbstractCollection<V>
	{
		private UValues()
		{
		}

		@Override
		public UIterator<V> iterator()
		{
			return new UIterator<V>()
			{
				@Override
				public V next()
				{
					return nextEntry().getValue();
				}
			};
		}

		@Override
		public int size()
		{
			return UMap.this.size();
		}

		@Override
		public boolean contains(Object o)
		{
			return UMap.this.containsValue(o instanceof Wrap ? ((Wrap<?>)o).unsafe() : o);
		}

		@Override
		public void clear()
		{
			UMap.this.clear();
		}
	}

	@Override
	public UEntrySet entrySet()
	{
		return new UEntrySet();
	}

	@Override
	public UKeySet keySet()
	{
		return new UKeySet();
	}

	@Override
	public UValues values()
	{
		return new UValues();
	}

	@Override
	public Object clone()
	{
		return new UnsupportedOperationException();
	}

	@Override
	public int hashCode()
	{
		return _map.hashCode();
	}

	@Override
	public boolean equals(Object o)
	{
		return this == o || _map.equals(o);
	}

	@Override
	public String toString()
	{
		return _map.toString();
	}
}

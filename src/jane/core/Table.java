package jane.core;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import jane.core.SContext.Record;
import jane.core.SContext.Safe;
import jane.core.Storage.Helper;
import jane.core.Storage.WalkHandler;

/**
 * 通用key类型的数据库表类
 */
public final class Table<K, V extends Bean<V>, S extends Safe<V>> extends TableBase<V>
{
	private final Storage.Table<K, V> _stoTable; // 存储引擎的表对象
	private final Map<K, V>           _cache;   // 读缓存. 有大小限制,溢出自动清理
	private final ConcurrentMap<K, V> _cacheMod; // 写缓存. 不会溢出,保存到数据库存储引擎后清理

	/**
	 * 创建一个数据库表
	 * @param tableName 表名
	 * @param stoTable 存储引擎的表对象. null表示此表是内存表
	 * @param lockName 此表关联的锁名
	 * @param cacheSize 此表的读缓存记录数量上限. 如果是内存表则表示超过此上限则会自动丢弃
	 * @param stubV 记录value的存根对象,不要用于记录有用的数据. 这里只用于标记删除的字段,如果为null则表示此表是内存表
	 */
	Table(int tableId, String tableName, Storage.Table<K, V> stoTable, String lockName, int cacheSize, V stubV)
	{
		super(tableId, tableName, stubV, (lockName != null && !(lockName = lockName.trim()).isEmpty() ? lockName.hashCode() : tableId) * 0x9e3779b1);
		_stoTable = stoTable;
		_cache = Util.newLRUConcurrentHashMap(cacheSize);
		_cacheMod = (stoTable != null ? Util.<K, V>newConcurrentHashMap() : null);
		if(stoTable != null) _tables.add(this);
	}

	/**
	 * 根据记录的key获取锁的ID(lockId)
	 * <p>
	 * 用于事务的加锁({@link Procedure#lock})
	 */
	public int lockId(K k)
	{
		return _lockId ^ k.hashCode();
	}

	/**
	 * 尝试依次加锁并保存此表已修改的记录
	 * <p>
	 * @param counts 长度必须>=3,用于保存3个统计值,分别是保存前所有修改的记录数,保存后的剩余记录数,保存的记录数
	 */
	@Override
	protected void trySaveModified(long[] counts)
	{
		counts[0] += _cacheMod.size();
		long n = 0;
		try
		{
			for(K k : _cacheMod.keySet())
			{
				Lock lock = Procedure.tryLock(lockId(k));
				if(lock != null)
				{
					try
					{
						++n;
						V v = _cacheMod.get(k);
						if(v == _deleted)
							_stoTable.remove(k);
						else
						{
							v.setSaveState(1);
							_stoTable.put(k, v);
						}
						_cacheMod.remove(k, v);
					}
					finally
					{
						lock.unlock();
					}
				}
			}
		}
		finally
		{
			counts[1] += _cacheMod.size();
			counts[2] += n;
		}
	}

	/**
	 * 在所有事务暂停的情况下直接依次保存此表已修改的记录
	 */
	@Override
	protected int saveModified()
	{
		for(Entry<K, V> e : _cacheMod.entrySet())
		{
			K k = e.getKey();
			V v = e.getValue();
			if(v == _deleted)
				_stoTable.remove(k);
			else
			{
				v.setSaveState(1);
				_stoTable.put(k, v);
			}
		}
		int m = _cacheMod.size();
		_cacheMod.clear();
		return m;
	}

	/**
	 * 根据记录的key获取value
	 * <p>
	 * 会自动添加到读cache中<br>
	 * 必须在事务中已加锁的状态下调用此方法
	 */
	@Deprecated
	public V getUnsafe(K k)
	{
		_readCount.incrementAndGet();
		V v = _cache.get(k);
		if(v != null) return v;
		if(_cacheMod == null) return null;
		v = _cacheMod.get(k);
		if(v != null)
		{
			if(v == _deleted) return null;
			_cache.put(k, v);
			return v;
		}
		_readStoCount.incrementAndGet();
		v = _stoTable.get(k);
		if(v != null)
		{
			v.setSaveState(1);
			_cache.put(k, v);
		}
		return v;
	}

	/**
	 * 同getUnsafe,但同时设置修改标记
	 */
	@Deprecated
	public V getModified(K k)
	{
		V v = getUnsafe(k);
		if(v != null) modify(k, v);
		return v;
	}

	/**
	 * 同getUnsafe,但增加的安全封装,可回滚修改,但没有加锁检查
	 */
	public S getNoLock(K k)
	{
		V v = getUnsafe(k);
		return v != null ? SContext.current().addRecord(this, k, v) : SContext.current().getRecord(this, k);
	}

	/**
	 * 同getNoLock,但有加锁检查
	 */
	public S get(K k)
	{
		if(!Procedure.isLockedByCurrentThread(lockId(k)))
		    throw new IllegalAccessError("get unlocked record! table=" + _tableName + ",key=" + k);
		return getNoLock(k);
	}

	/**
	 * 根据记录的key获取value
	 * <p>
	 * 不会自动添加到读cache中<br>
	 * 必须在事务中已加锁的状态下调用此方法<br>
	 * <b>注意</b>: 不能在同一事务里使用NoCache方式(或混合Cache方式)get同一个记录多次并且对这些记录有多次修改,否则会触发modify函数中的异常
	 */
	@Deprecated
	public V getNoCacheUnsafe(K k)
	{
		_readCount.incrementAndGet();
		V v = _cache.get(k);
		if(v != null) return v;
		if(_cacheMod == null) return null;
		v = _cacheMod.get(k);
		if(v != null)
		{
			if(v == _deleted) return null;
			return v;
		}
		_readStoCount.incrementAndGet();
		return _stoTable.get(k);
	}

	/**
	 * 同getNoCacheUnsafe,但增加的安全封装,可回滚修改<br>
	 * <b>注意</b>: 不能在同一事务里使用NoCache方式(或混合Cache方式)get同一个记录多次并且对这些记录有多次修改,否则会触发modify函数中的异常
	 */
	public S getNoCache(K k)
	{
		V v = getNoCacheUnsafe(k);
		return v != null ? SContext.current().addRecord(this, k, v) : SContext.current().getRecord(this, k);
	}

	/**
	 * 根据记录的key获取value
	 * <p>
	 * 只在读和写cache中获取<br>
	 * 必须在事务中已加锁的状态下调用此方法
	 */
	@Deprecated
	public V getCacheUnsafe(K k)
	{
		_readCount.incrementAndGet();
		V v = _cache.get(k);
		if(v != null) return v;
		if(_cacheMod == null) return null;
		v = _cacheMod.get(k);
		return v != null && v != _deleted ? v : null;
	}

	/**
	 * 同getCacheUnsafe,但增加的安全封装,可回滚修改
	 */
	public S getCache(K k)
	{
		V v = getCacheUnsafe(k);
		return v != null ? SContext.current().addRecord(this, k, v) : SContext.current().getRecord(this, k);
	}

	/**
	 * 标记记录已修改的状态
	 * <p>
	 * 必须在事务中已加锁的状态下调用此方法
	 * @param v 必须是get获取到的对象引用. 如果不是,则应该调用put方法
	 */
	public void modify(K k, V v)
	{
		if(!v.modified() && _cacheMod != null)
		{
			V vOld = _cacheMod.put(k, v);
			if(vOld == null)
			{
				v.setSaveState(2);
				DBManager.instance().incModCount();
			}
			else if(vOld != v)
			{
				_cacheMod.put(k, vOld);
				throw new IllegalStateException("modify unmatched record: t=" + _tableName +
				        ",k=" + k + ",vOld=" + vOld + ",v=" + v);
			}
		}
	}

	@SuppressWarnings("unchecked")
	void modify(Object ko, Object vo)
	{
		K k = (K)ko;
		V v = (V)vo;
		if(!v.modified() && _cacheMod != null)
		{
			V vOld = _cacheMod.put(k, v);
			if(vOld == null)
			{
				v.setSaveState(2);
				DBManager.instance().incModCount();
			}
			else if(vOld != v)
			{
				// 可能之前已经覆盖或删除过记录,然后再modify的话,就忽略本次modify了,因为SContext.commit无法识别这种情况
				_cacheMod.put(k, vOld);
			}
		}
	}

	/**
	 * 根据记录的key保存value
	 * <p>
	 * 必须在事务中已加锁的状态下调用此方法
	 * @param v 如果是get获取到的对象引用,可调用modify来提高性能
	 */
	@Deprecated
	public void putUnsafe(K k, V v)
	{
		V vOld = _cache.put(k, v);
		if(vOld == v)
			modify(k, v);
		else
		{
			if(!v.stored())
			{
				if(_cacheMod != null)
				{
					vOld = _cacheMod.put(k, v);
					if(vOld == null)
					{
						v.setSaveState(2);
						DBManager.instance().incModCount();
					}
				}
			}
			else
			{
				if(vOld != null)
					_cache.put(k, vOld);
				else
					_cache.remove(k);
				throw new IllegalStateException("put shared record: t=" + _tableName +
				        ",k=" + k + ",vOld=" + vOld + ",v=" + v);
			}
		}
	}

	/**
	 * 同putUnsafe,但增加的安全封装,可回滚修改
	 */
	public void put(final K k, V v)
	{
		final V vOld = getNoCacheUnsafe(k);
		if(vOld == v) return;
		if(v.stored())
		    throw new IllegalStateException("put shared record: t=" + _tableName + ",k=" + k + ",v=" + v);
		if(!Procedure.isLockedByCurrentThread(lockId(k)))
		    throw new IllegalAccessError("put unlocked record! table=" + _tableName + ",key=" + k);
		SContext.current().addOnRollbackDirty(new Runnable()
		{
			@Override
			public void run()
			{
				if(vOld != null)
				{
					vOld.setSaveState(0);
					putUnsafe(k, vOld);
				}
				else
					removeUnsafe(k);
			}
		});
		putUnsafe(k, v);
	}

	@SuppressWarnings("deprecation")
	public void put(K k, S s)
	{
		put(k, s.unsafe());
		s.record(new Record<K, V, S>(this, k, s));
	}

	/**
	 * 根据记录的key删除记录
	 * <p>
	 * 必须在事务中已加锁的状态下调用此方法
	 */
	@Deprecated
	public void removeUnsafe(K k)
	{
		_cache.remove(k);
		if(_cacheMod != null && _cacheMod.put(k, _deleted) == null)
		    DBManager.instance().incModCount();
	}

	/**
	 * 同removeUnsafe,但增加的安全封装,可回滚修改
	 */
	public void remove(final K k)
	{
		final V vOld = getNoCacheUnsafe(k);
		if(vOld == null) return;
		if(!Procedure.isLockedByCurrentThread(lockId(k)))
		    throw new IllegalAccessError("remove unlocked record! table=" + _tableName + ",key=" + k);
		SContext.current().addOnRollbackDirty(new Runnable()
		{
			@Override
			public void run()
			{
				vOld.setSaveState(0);
				putUnsafe(k, vOld);
			}
		});
		removeUnsafe(k);
	}

	/**
	 * 只在读cache中遍历此表的所有记录
	 * <p>
	 * 遍历时注意先根据记录的key获取锁再调用get获得其value, 必须在事务中调用此方法<br>
	 * 注意此遍历方法是无序的
	 * @param handler 遍历过程中返回false可中断遍历
	 */
	public boolean walkCache(WalkHandler<K> handler)
	{
		for(K k : _cache.keySet())
			if(!Helper.onWalkSafe(handler, k)) return false;
		return true;
	}

	/**
	 * 按记录key的顺序遍历此表的所有记录
	 * <p>
	 * 遍历时注意先根据记录的key获取锁再调用get获得其value(取锁操作必须在事务中)<br>
	 * 注意: 遍历的key列表仅从数据库层获取,当前没有checkpoint的cache记录会被无视,所以get获取到的key可能不是最新,而且得到的value有可能为null
	 * @param handler 遍历过程中返回false可中断遍历
	 * @param from 需要遍历的最小key. null表示最小值
	 * @param to 需要遍历的最大key. null表示最大值
	 * @param inclusive 遍历是否包含from和to的key
	 * @param reverse 是否按反序遍历
	 */
	public boolean walk(WalkHandler<K> handler, K from, K to, boolean inclusive, boolean reverse)
	{
		if(_stoTable != null)
		    return _stoTable.walk(handler, from, to, inclusive, reverse);
		return walkCache(handler);
	}

	public boolean walk(WalkHandler<K> handler, boolean reverse)
	{
		return walk(handler, null, null, true, reverse);
	}

	public boolean walk(WalkHandler<K> handler)
	{
		return walk(handler, null, null, true, false);
	}
}

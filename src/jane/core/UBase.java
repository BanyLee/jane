package jane.core;

import java.lang.reflect.Field;

/**
 * 各种基础类型的回滚处理类
 */
public abstract class UBase implements Runnable
{
	protected final Bean<?> _bean;
	protected final Field   _field;

	protected UBase(Bean<?> b, Field f)
	{
		_bean = b;
		_field = f;
	}

	public static final class UBoolean extends UBase
	{
		private final boolean _saved;

		public UBoolean(Bean<?> b, Field f, boolean v)
		{
			super(b, f);
			_saved = v;
		}

		@Override
		public void run()
		{
			try
			{
				_field.setBoolean(_bean, _saved);
			}
			catch(Exception e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	public static final class UChar extends UBase
	{
		private final char _saved;

		public UChar(Bean<?> b, Field f, char v)
		{
			super(b, f);
			_saved = v;
		}

		@Override
		public void run()
		{
			try
			{
				_field.setChar(_bean, _saved);
			}
			catch(Exception e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	public static final class UByte extends UBase
	{
		private final byte _saved;

		public UByte(Bean<?> b, Field f, byte v)
		{
			super(b, f);
			_saved = v;
		}

		@Override
		public void run()
		{
			try
			{
				_field.setByte(_bean, _saved);
			}
			catch(Exception e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	public static final class UShort extends UBase
	{
		private final short _saved;

		public UShort(Bean<?> b, Field f, short v)
		{
			super(b, f);
			_saved = v;
		}

		@Override
		public void run()
		{
			try
			{
				_field.setShort(_bean, _saved);
			}
			catch(Exception e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	public static final class UInteger extends UBase
	{
		private final int _saved;

		public UInteger(Bean<?> b, Field f, int v)
		{
			super(b, f);
			_saved = v;
		}

		@Override
		public void run()
		{
			try
			{
				_field.setInt(_bean, _saved);
			}
			catch(Exception e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	public static final class ULong extends UBase
	{
		private final long _saved;

		public ULong(Bean<?> b, Field f, long v)
		{
			super(b, f);
			_saved = v;
		}

		@Override
		public void run()
		{
			try
			{
				_field.setLong(_bean, _saved);
			}
			catch(Exception e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	public static final class UFloat extends UBase
	{
		private final float _saved;

		public UFloat(Bean<?> b, Field f, float v)
		{
			super(b, f);
			_saved = v;
		}

		@Override
		public void run()
		{
			try
			{
				_field.setFloat(_bean, _saved);
			}
			catch(Exception e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	public static final class UDouble extends UBase
	{
		private final double _saved;

		public UDouble(Bean<?> b, Field f, double v)
		{
			super(b, f);
			_saved = v;
		}

		@Override
		public void run()
		{
			try
			{
				_field.setDouble(_bean, _saved);
			}
			catch(Exception e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	public static final class UOctets extends UBase
	{
		private final Octets _saved;

		public UOctets(Bean<?> b, Field f, Octets v, boolean clone)
		{
			super(b, f);
			_saved = (clone ? v.clone() : v);
		}

		@Override
		public void run()
		{
			try
			{
				_field.set(_bean, _saved);
			}
			catch(Exception e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	public static final class UString extends UBase
	{
		private final String _saved;

		public UString(Bean<?> b, Field field, String v)
		{
			super(b, field);
			_saved = v;
		}

		@Override
		public void run()
		{
			try
			{
				_field.set(_bean, _saved);
			}
			catch(Exception e)
			{
				throw new RuntimeException(e);
			}
		}
	}
}

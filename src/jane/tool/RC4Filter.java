package jane.tool;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;

/**
 * RC4加密算法的mina网络过滤器
 * <p>
 * 此类可直接使用或作为示例编写其它的加密或压缩相关的过滤器<br>
 * 可在合适的时机调用下面几行代码来启用:<br>
 * <code><pre>
 * RC4Filter filter = new RC4Filter();
 * filter.setInputKey(...);
 * filter.setOutputKey(...);
 * session.getFilterChain().addFirst("enc", filter);
 * </pre></code>
 */
public final class RC4Filter extends IoFilterAdapter
{
	private final byte[] _ctxI = new byte[256];
	private final byte[] _ctxO = new byte[256];
	private int			 _idx1I, _idx2I;
	private int			 _idx1O, _idx2O;

	private static void setKey(byte[] ctx, byte[] key, int len)
	{
		for(int i = 0; i < 256; ++i)
			ctx[i] = (byte)i;
		if(len > key.length) len = key.length;
		if(len <= 0) return;
		for(int i = 0, j = 0, k = 0; i < 256; ++i)
		{
			byte t = ctx[i];
			k = (k + t + key[j]) & 0xff;
			if(++j >= len) j = 0;
			ctx[i] = ctx[k];
			ctx[k] = t;
		}
	}

	/**
	 * 设置网络输入流的对称密钥
	 * <p>
	 * @param key 密钥内容,可以是任意数据
	 * @param len 密钥的长度,最多256字节有效
	 */
	public void setInputKey(byte[] key, int len)
	{
		setKey(_ctxI, key, len);
		_idx1I = _idx2I = 0;
	}

	/**
	 * 设置网络输出流的对称密钥
	 * <p>
	 * @param key 密钥内容,可以是任意数据
	 * @param len 密钥的长度,最多256字节有效
	 */
	public void setOutputKey(byte[] key, int len)
	{
		setKey(_ctxO, key, len);
		_idx1O = _idx2O = 0;
	}

	private static int update(byte[] ctx, int idx1, int idx2, byte[] buf, int pos, int len)
	{
		for(int i = 0; i < len; ++i)
		{
			idx1 = (idx1 + 1) & 0xff;
			byte a = ctx[idx1];
			idx2 = (idx2 + a) & 0xff;
			byte b = ctx[idx2];
			ctx[idx1] = b;
			ctx[idx2] = a;
			buf[pos + i] ^= ctx[(a + b) & 0xff];
		}
		return idx2;
	}

	/**
	 * 加解密一段输入数据
	 * <p>
	 * 加解密是对称的
	 * @param buf 数据的缓冲区
	 * @param pos 数据缓冲区的起始位置
	 * @param len 数据的长度
	 */
	public void updateInput(byte[] buf, int pos, int len)
	{
		_idx2I = update(_ctxI, _idx1I, _idx2I, buf, pos, len);
		_idx1I = (_idx1I + len) & 0xff;
	}

	/**
	 * 加解密一段输出数据
	 * <p>
	 * 加解密是对称的
	 * @param buf 数据的缓冲区
	 * @param pos 数据缓冲区的起始位置
	 * @param len 数据的长度
	 */
	public void updateOutput(byte[] buf, int pos, int len)
	{
		_idx2O = update(_ctxO, _idx1O, _idx2O, buf, pos, len);
		_idx1O = (_idx1O + len) & 0xff;
	}

	@Override
	public void messageReceived(NextFilter nextFilter, IoSession session, Object message)
	{
		if(message instanceof IoBuffer)
		{
			IoBuffer ioBuf = (IoBuffer)message;
			if(ioBuf.hasArray())
				updateInput(ioBuf.array(), ioBuf.position(), ioBuf.remaining());
			else
			{
				int len = ioBuf.remaining();
				byte[] buf = new byte[len];
				int pos = ioBuf.position();
				ioBuf.get(buf, 0, len);
				ioBuf.position(pos);
				updateInput(buf, 0, len);
				ioBuf.put(buf, 0, len);
				ioBuf.position(pos);
			}
		}
		nextFilter.messageReceived(message);
	}

	@Override
	public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest)
	{
		Object message = writeRequest.getMessage();
		if(message instanceof IoBuffer)
		{
			IoBuffer ioBuf = (IoBuffer)message;
			if(ioBuf.hasArray())
				updateOutput(ioBuf.array(), ioBuf.position(), ioBuf.remaining());
			else
			{
				int len = ioBuf.remaining();
				byte[] buf = new byte[len];
				int pos = ioBuf.position();
				ioBuf.get(buf, 0, len);
				ioBuf.position(pos);
				updateOutput(buf, 0, len);
				ioBuf.put(buf, 0, len);
				ioBuf.position(pos);
			}
		}
		nextFilter.filterWrite(writeRequest);
	}
}

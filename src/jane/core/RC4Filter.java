package jane.core;

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
public class RC4Filter extends IoFilterAdapter
{
	private final byte[] _ctx_i = new byte[256];
	private final byte[] _ctx_o = new byte[256];
	private int          _idx1_i, _idx2_i;
	private int          _idx1_o, _idx2_o;

	private static void setKey(byte[] ctx, byte[] key, int len)
	{
		for(int i = 0; i < 256; ++i)
			ctx[i] = (byte)i;
		if(len > key.length) len = key.length;
		if(len <= 0) return;
		for(int i = 0, j = 0, k = 0; i < 256; ++i)
		{
			k = (k + ctx[i] + key[j]) & 0xff;
			if(++j >= len) j = 0;
			byte t = ctx[i];
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
		setKey(_ctx_i, key, len);
		_idx1_i = _idx2_i = 0;
	}

	/**
	 * 设置网络输出流的对称密钥
	 * <p>
	 * @param key 密钥内容,可以是任意数据
	 * @param len 密钥的长度,最多256字节有效
	 */
	public void setOutputKey(byte[] key, int len)
	{
		setKey(_ctx_o, key, len);
		_idx1_o = _idx2_o = 0;
	}

	private static int update(byte[] ctx, int idx1, int idx2, byte[] buf, int pos, int len)
	{
		for(int i = 0; i < len; ++i)
		{
			idx1 = (idx1 + 1) & 0xff;
			idx2 = (idx2 + ctx[idx1]) & 0xff;
			byte k = ctx[idx1];
			ctx[idx1] = ctx[idx2];
			ctx[idx2] = k;
			buf[pos + i] ^= ctx[(ctx[idx1] + ctx[idx2]) & 0xff];
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
		_idx2_i = update(_ctx_i, _idx1_i, _idx2_i, buf, pos, len);
		_idx1_i = (_idx1_i + len) & 0xff;
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
		_idx2_o = update(_ctx_o, _idx1_o, _idx2_o, buf, pos, len);
		_idx1_o = (_idx1_o + len) & 0xff;
	}

	@Override
	public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception
	{
		if(message instanceof IoBuffer)
		{
			IoBuffer iobuf = (IoBuffer)message;
			updateInput(iobuf.array(), iobuf.position(), iobuf.remaining());
		}
		nextFilter.messageReceived(session, message);
	}

	@Override
	public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception
	{
		Object message = writeRequest.getMessage();
		if(message instanceof IoBuffer)
		{
			IoBuffer iobuf = (IoBuffer)message;
			updateOutput(iobuf.array(), iobuf.position(), iobuf.remaining());
		}
		nextFilter.filterWrite(session, writeRequest);
	}
}

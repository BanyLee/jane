package jane.core;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderAdapter;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

/**
 * HTTP的mina协议编解码过滤器
 * <p>
 * 输入(解码): OctetsStream类型,包括一次完整请求原始的HTTP头和内容,position指向内容的起始,如果没有内容则指向结尾<br>
 * 输出(编码): OctetsStream(从position到结尾的数据),或Octets,或byte[]<br>
 * 输入处理: 获取HTTP头中的条目,method,url-path,url-param,content-charset,以及cookie条目,支持url编码的解码<br>
 * 输出处理: 固定长度输出,chunked方式输出<br>
 * 不直接支持: https, mime, Connection:close/timeout, Accept-Encoding, Set-Cookie, Multi-Part, encodeUrl, cookie value with "; "
 */
public final class HttpCodec extends ProtocolDecoderAdapter implements ProtocolEncoder, ProtocolCodecFactory
{
	private static final byte[]     HEAD_END_MARK    = "\r\n\r\n".getBytes(Const.stringCharsetUTF8);
	private static final byte[]     CONT_LEN_MARK    = "\r\nContent-Length: ".getBytes(Const.stringCharsetUTF8);
	private static final byte[]     CONT_TYPE_MARK   = "\r\nContent-Type: ".getBytes(Const.stringCharsetUTF8);
	private static final byte[]     COOKIE_MARK      = "\r\nCookie: ".getBytes(Const.stringCharsetUTF8);
	private static final byte[]     CHUNK_OVER_MARK  = "\r\n".getBytes(Const.stringCharsetUTF8);
	private static final byte[]     CHUNK_END_MARK   = "0\r\n\r\n".getBytes(Const.stringCharsetUTF8);
	private static final String     DEF_CONT_CHARSET = "UTF-8";
	private static final Pattern    PATTERN_COOKIE   = Pattern.compile("(\\w+)=(.*?)(; |$)");
	private static final Pattern    PATTERN_CHARSET  = Pattern.compile("charset=([\\w-]+)");
	private static final DateFormat _sdf             = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
	private OctetsStream            _buf             = new OctetsStream(1024);                                        // 用于解码器的数据缓存
	private long                    _bodysize;                                                                        // 当前请求所需的内容大小

	static
	{
		_sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	private static synchronized String getDate()
	{
		return _sdf.format(new Date());
	}

	public static String decodeUrl(byte[] src, int srcpos, int srclen)
	{
		if(srcpos < 0) srcpos = 0;
		if(srcpos + srclen > src.length) srclen = src.length - srcpos;
		if(srclen <= 0) return "";
		byte[] dst = new byte[srclen];
		int dstpos = 0;
		for(int srcend = srcpos + srclen; srcpos < srcend;)
		{
			int c = src[srcpos++];
			switch(c)
			{
				case '+':
					dst[dstpos++] = (byte)' ';
					break;
				case '%':
					if(srcpos + 1 < srcend)
					{
						c = src[srcpos++];
						int v = (c < 'A' ? c - '0' : c - 'A' + 10) << 4;
						c = src[srcpos++];
						v += (c < 'A' ? c - '0' : c - 'A' + 10);
						dst[dstpos++] = (byte)v;
						break;
					}
					//$FALL-THROUGH$
				default:
					dst[dstpos++] = (byte)c;
					break;
			}
		}
		return new String(dst, 0, dstpos, Const.stringCharsetUTF8);
	}

	public static String getHeadMethod(OctetsStream head)
	{
		int p = head.find(0, head.position(), (byte)' ');
		return p < 0 ? "" : new String(head.array(), 0, p, Const.stringCharsetUTF8);
	}

	// GET /path/name.html?k=v&a=b HTTP/1.1
	public static String getHeadPath(OctetsStream head)
	{
		int e = head.position();
		int p = head.find(0, e, (byte)' ');
		if(p < 0) return "";
		int q = head.find(++p, e, (byte)' ');
		if(q < 0) return "";
		int r = head.find(p, q, (byte)'?');
		if(r >= p && r < q) q = r;
		return new String(head.array(), p, q - p, Const.stringCharsetUTF8);
	}

	/**
	 * @return 获取的参数数量
	 */
	public static int getHeadParams(Octets oct, int pos, int len, Map<String, String> param)
	{
		byte[] buf = oct.array();
		if(pos < 0) pos = 0;
		if(pos + len > buf.length) len = buf.length - pos;
		if(len <= 0) return 0;
		int e = pos + len;
		int q = oct.find(0, e, (byte)'\r');
		if(q < 0) return 0;
		int p = oct.find(0, q, (byte)'?');
		if(p < 0) return 0;
		q = oct.find(++p, q, (byte)' ');
		if(q < p) return 0;
		int n = 0;
		for(; p < q; p = e + 1, ++n)
		{
			e = oct.find(p, q, (byte)'&');
			if(e < 0) e = q;
			int r = oct.find(p, e, (byte)'=');
			if(r >= p)
			{
				String k = decodeUrl(buf, p, r - p);
				String v = decodeUrl(buf, r + 1, e - r - 1);
				param.put(k, v);
			}
			else
				param.put(decodeUrl(buf, p, e - p), "");
		}
		return n;
	}

	public static int getHeadParams(OctetsStream os, Map<String, String> param)
	{
		return getHeadParams(os, 0, os.position(), param);
	}

	public static long getHeadLong(OctetsStream head, byte[] key)
	{
		int p = head.find(0, head.position(), key);
		if(p < 0) return -1;
		p += key.length;
		int e = head.find(p + key.length, (byte)'\r');
		if(e < 0) return -1;
		long r = 0;
		for(byte[] buf = head.array(); p < e; ++p)
			r = r * 10 + (buf[p] - 0x30);
		return r;
	}

	/**
	 * 获取HTTP请求头中的条目
	 * @param key 格式示例: "\r\nReferer: ".getBytes()
	 */
	public static String getHeadProp(OctetsStream head, byte[] key)
	{
		int p = head.find(0, head.position(), key);
		if(p < 0) return "";
		p += key.length;
		int e = head.find(p + key.length, (byte)'\r');
		if(e < 0) return "";
		return decodeUrl(head.array(), p, e - p);
	}

	public static String getHeadCharset(OctetsStream head)
	{
		String conttype = getHeadProp(head, CONT_TYPE_MARK);
		if(conttype.isEmpty()) return DEF_CONT_CHARSET; // default charset
		Matcher mat = PATTERN_CHARSET.matcher(conttype);
		return mat.find() ? mat.group(1) : DEF_CONT_CHARSET;
	}

	/**
	 * @return 获取的cookie数量
	 */
	public static int getHeadCookie(OctetsStream head, Map<String, String> cookies)
	{
		String cookie = getHeadProp(head, COOKIE_MARK);
		if(cookie.isEmpty()) return 0;
		Matcher mat = PATTERN_COOKIE.matcher(cookie);
		int n = 0;
		for(; mat.find(); ++n)
			cookies.put(mat.group(1), mat.group(2));
		return n;
	}

	/**
	 * 发送HTTP的回复头
	 * @param code 回复的HTTP状态码. 如200表示正常
	 * @param len
	 * <li>len < 0: 使用chunked模式,后续发送若干个{@link #sendChunk},最后发送{@link #sendChunkEnd}
	 * <li>len > 0: 后续使用{@link #send}发送固定长度的数据
	 * @param heads 额外发送的HTTP头. 每个元素表示一行文字,没有做验证,所以小心使用,可传null表示无任何额外的头信息
	 */
	public static boolean sendHead(IoSession session, int code, int len, Iterable<String> heads)
	{
		if(session.isClosing()) return false;
		StringBuilder sb = new StringBuilder(1024);
		sb.append("HTTP/1.1 ").append(code).append('\r').append('\n');
		sb.append("Date: ").append(getDate()).append('\r').append('\n');
		if(len >= 0)
			sb.append("Content-Length: ").append(len).append('\r').append('\n');
		else
			sb.append("Transfer-Encoding: chunked").append('\r').append('\n');
		if(heads != null)
		{
			for(String head : heads)
				sb.append(head).append('\r').append('\n');
		}
		sb.append('\r').append('\n');
		int n = sb.length();
		byte[] out = new byte[n];
		for(int i = 0; i < n; ++i)
			out[i] = (byte)sb.charAt(i);
		return session.write(out).getException() == null;
	}

	public static boolean send(IoSession session, Octets data)
	{
		return !session.isClosing() && session.write(data).getException() == null;
	}

	public static boolean sendChunk(IoSession session, Octets chunk)
	{
		if(session.isClosing()) return false;
		if(chunk == null)
		{
			if(session.write(CHUNK_END_MARK).getException() != null) return false;
		}
		else
		{
			if(session.write(String.format("%x\r\n", chunk.remain()).getBytes(Const.stringCharsetUTF8)).getException() != null) return false;
			if(session.write(chunk).getException() != null) return false;
			if(session.write(CHUNK_OVER_MARK).getException() != null) return false;
		}
		return true;
	}

	public static boolean sendChunk(IoSession session, byte[] chunk)
	{
		return sendChunk(session, chunk != null ? Octets.wrap(chunk) : null);
	}

	public static boolean sendChunk(IoSession session, String chunk)
	{
		return sendChunk(session, chunk != null ? Octets.wrap(chunk.getBytes(Const.stringCharsetUTF8)) : null);
	}

	public static boolean sendChunkEnd(IoSession session)
	{
		return sendChunk(session, (Octets)null);
	}

	@Override
	public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception
	{
		if(message instanceof OctetsStream)
		{
			OctetsStream os = (OctetsStream)message;
			int remain = os.remain();
			if(remain > 0) out.write(IoBuffer.wrap(os.array(), os.position(), remain));
		}
		else if(message instanceof Octets)
		{
			Octets oct = (Octets)message;
			int size = oct.size();
			if(size > 0) out.write(IoBuffer.wrap(oct.array(), 0, size));
		}
		else if(message instanceof byte[])
		{
			byte[] bytes = (byte[])message;
			if(bytes.length > 0) out.write(IoBuffer.wrap(bytes));
		}
	}

	@Override
	public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception
	{
		for(;;)
		{
			if(_bodysize <= 0)
			{
				int r = in.remaining();
				int s = (r < 1024 ? r : 1024);
				int p = _buf.size();
				if(s > 0)
				{
					_buf.resize(p + s);
					in.get(_buf.array(), p, s);
				}
				p = _buf.find(p - 3, HEAD_END_MARK);
				if(p < 0)
				{
					if(_buf.size() > Const.maxHttpHeadSize)
					    throw new Exception("http head size overflow: bufsize=" + _buf.size() + ",maxsize=" + Const.maxHttpHeadSize);
					if(!in.hasRemaining()) return;
					continue;
				}
				p += HEAD_END_MARK.length;
				_buf.setPosition(p);
				_bodysize = getHeadLong(_buf, CONT_LEN_MARK);
				if(_bodysize <= 0)
				{
					OctetsStream os = new OctetsStream(_buf.array(), p, _buf.remain());
					_buf.resize(p);
					out.write(_buf);
					_buf = os;
					continue;
				}
				if(_bodysize > Const.maxHttpBodySize)
				    throw new Exception("http body size overflow: bodysize=" + _bodysize + ",maxsize=" + Const.maxHttpBodySize);
			}
			int r = in.remaining();
			int s = (int)_bodysize - _buf.remain();
			if(s > r) s = r;
			int p = _buf.size();
			if(s > 0)
			{
				_buf.resize(p + s);
				in.get(_buf.array(), p, s);
				if(_buf.remain() < _bodysize) return;
			}
			OctetsStream os = (s >= 0 ? new OctetsStream(1024) : new OctetsStream(_buf.array(), p + s, -s));
			out.write(_buf);
			_buf = os;
			_bodysize = 0;
		}
	}

	@Override
	public ProtocolEncoder getEncoder(IoSession session) throws Exception
	{
		return this;
	}

	@Override
	public ProtocolDecoder getDecoder(IoSession session) throws Exception
	{
		return this;
	}
}

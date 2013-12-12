package sas.core;

import java.util.Collection;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderAdapter;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

/**
 * bean的mina协议编解码过滤器(单件)
 */
public class BeanCodec extends ProtocolDecoderAdapter implements ProtocolEncoder, ProtocolCodecFactory
{
	private static final BeanCodec         _instance = new BeanCodec();                 // 这个单件用于通用的编码器和解码器工厂
	protected static final IntMap<Integer> _maxsize  = new IntMap<Integer>(65536, 0.5f); // 所有注册beans的最大空间限制
	protected static final IntMap<Bean<?>> _stubmap  = new IntMap<Bean<?>>(65536, 0.5f); // 所有注册beans的存根对象
	private final OctetsStream             _os       = new OctetsStream();              // 用于解码器的数据缓存
	private int                            _ptype;                                      // 当前数据缓存中获得的协议类型
	private int                            _psize    = -1;                              // 当前数据缓存中获得的协议大小. -1表示没获取到

	public static BeanCodec instance()
	{
		return _instance;
	}

	/**
	 * 重新注册所有的beans
	 * <p>
	 * 参数中的所有beans会被保存下来当存根(通过调用create方法创建对象)<br>
	 * 警告: 此方法<b>必须</b>在开启任何<b>网络连接前</b>调用
	 */
	public static void registerAllBeans(Collection<Bean<?>> beans)
	{
		_maxsize.clear();
		_stubmap.clear();
		for(Bean<?> bean : beans)
		{
			int type = bean.type();
			if(type > 0)
			{
				_maxsize.put(type, bean.maxSize());
				_stubmap.put(type, bean);
			}
		}
	}

	/**
	 * 获取某个类型bean的最大空间限制(字节)
	 */
	public static int beanMaxSize(int type)
	{
		Integer size = _maxsize.get(type);
		return size != null ? size : -1;
	}

	/**
	 * 根据类型创建一个默认初始化的bean
	 */
	public static Bean<?> createBean(int type)
	{
		Bean<?> bean = _stubmap.get(type);
		return bean != null ? bean.create() : null;
	}

	protected BeanCodec()
	{
	}

	@Override
	public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception
	{
		Bean<?> bean = (Bean<?>)message;
		int type = bean.type();
		if(type == 0)
		{
			RawBean rawbean = (RawBean)bean;
			Octets rawdata = rawbean.getData();
			OctetsStream os_head = new OctetsStream(10).marshalUInt(rawbean.getType()).marshalUInt(rawdata.size());
			out.write(IoBuffer.wrap(os_head.array(), 0, os_head.size()));
			out.write(IoBuffer.wrap(rawdata.array(), 0, rawdata.size()));
		}
		else
		{
			OctetsStream os_body = bean.marshalProtocol(new OctetsStream(bean.initSize()));
			OctetsStream os_head = new OctetsStream(10).marshalUInt(type).marshalUInt(os_body.size());
			out.write(IoBuffer.wrap(os_head.array(), 0, os_head.size()));
			out.write(IoBuffer.wrap(os_body.array(), 0, os_body.size()));
		}
	}

	protected boolean decodeProtocol(OctetsStream os, ProtocolDecoderOutput out) throws Exception
	{
		int pos = os.position();
		try
		{
			if(_psize < 0)
			{
				_ptype = os.unmarshalUInt();
				_psize = os.unmarshalUInt();
				pos = os.position();
				int maxsize = beanMaxSize(_ptype);
				if(maxsize < 0) maxsize = Const.maxRawBeanSize;
				if(_psize > maxsize)
				    throw new Exception("bean maxsize overflow: type=" + _ptype + ",size=" + _psize + ",maxsize=" + maxsize);
			}
			if(_psize > os.remain()) return false;
			Bean<?> bean = createBean(_ptype);
			if(bean != null)
			{
				bean.unmarshalProtocol(os);
				int realsize = os.position() - pos;
				if(realsize > _psize)
				    throw new Exception("bean realsize overflow: type=" + _ptype + ",size=" + _psize + ",realsize=" + realsize);
				out.write(bean);
			}
			else
				out.write(new RawBean(_ptype, os.unmarshalRaw(_psize)));
			_psize = -1;
			return true;
		}
		catch(MarshalException.EOF e)
		{
			os.setPosition(pos);
			return false;
		}
	}

	@Override
	public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception
	{
		if(!_os.empty())
		{
			if(_psize < 0)
			{
				int s = _os.size();
				int n = 10 - s;
				if(n > 0)
				{
					n = Math.min(n, in.remaining());
					_os.resize(s + n);
					in.get(_os.array(), s, n);
				}
			}
			else
			{
				int s = _os.size();
				int n = Math.min(_psize, in.remaining());
				_os.resize(s + n);
				in.get(_os.array(), s, n);
			}
			if(!decodeProtocol(_os, out))
			{
				int r = in.remaining();
				int s = _psize - _os.remain();
				int p = in.position();
				if(r < s)
				{
					_os.append(in.array(), p, r);
					in.position(p + r);
					return;
				}
				_os.append(in.array(), p, s);
				in.position(p + s);
				decodeProtocol(_os, out);
			}
			else if(_os.remain() > 0)
			    in.position(in.position() - _os.remain());
			_os.clear();
			if(in.remaining() <= 0) return;
		}
		int n = in.limit();
		OctetsStream os = OctetsStream.wrap(in.array(), n);
		os.setPosition(in.position());
		in.position(n);
		while(decodeProtocol(os, out))
		{
		}
		if(os.remain() > 0)
		{
			_os.replace(os.array(), os.position(), os.remain());
			_os.setPosition(0);
		}
	}

	@Override
	public ProtocolEncoder getEncoder(IoSession session) throws Exception
	{
		return _instance;
	}

	@Override
	public ProtocolDecoder getDecoder(IoSession session) throws Exception
	{
		return new BeanCodec();
	}
}

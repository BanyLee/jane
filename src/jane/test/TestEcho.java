package jane.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.buffer.SimpleBufferAllocator;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.session.IoSession;
import jane.core.Log;
import jane.core.NetManager;

// start.bat jane.test.TestEcho 32 100000 1 64
public final class TestEcho extends NetManager
{
	private static int TEST_ECHO_SIZE	 = 32;
	private static int TEST_ECHO_COUNT	 = 100000;
	private static int TEST_CLIENT_COUNT = 64;

	private static final CountDownLatch	_closedCount = new CountDownLatch(TEST_CLIENT_COUNT * 2);
	private static final AtomicInteger	_recvCount	 = new AtomicInteger();

/*
	private static final AtomicInteger _wrqCount = new AtomicInteger();

	private static final DefaultIoSessionDataStructureFactory _dsFactory = new DefaultIoSessionDataStructureFactory()
	{
		@Override
		public WriteRequestQueue getWriteRequestQueue(IoSession session) throws Exception
		{
			_wrqCount.incrementAndGet();
			return new WriteRequestQueue()
			{
				private ArrayDeque<WriteRequest> _wrq = new ArrayDeque<>();

				@Override
				public synchronized WriteRequest poll(@SuppressWarnings("hiding") IoSession session)
				{
					return _wrq.pollFirst();
				}

				@Override
				public synchronized void offer(@SuppressWarnings("hiding") IoSession session, WriteRequest writeRequest)
				{
					_wrq.addLast(writeRequest);
				}

				@Override
				public synchronized boolean isEmpty(@SuppressWarnings("hiding") IoSession session)
				{
					return _wrq.isEmpty();
				}

				@Override
				public synchronized void clear(@SuppressWarnings("hiding") IoSession session)
				{
					_wrq.clear();
				}

				@Override
				public void dispose(@SuppressWarnings("hiding") IoSession session)
				{
				}

				@Override
				public synchronized int size()
				{
					return _wrq.size();
				}
			};
		}
	};
*/
	@Override
	public void startServer(SocketAddress addr) throws IOException
	{
		setIoThreadCount(1);
//		if(getAcceptor().getSessionDataStructureFactory() != _dsFactory)
//			getAcceptor().setSessionDataStructureFactory(_dsFactory);
		getServerConfig().setReuseAddress(true);
		getServerConfig().setTcpNoDelay(true);
		super.startServer(addr);
	}

	@Override
	public ConnectFuture startClient(SocketAddress addr)
	{
		setIoThreadCount(1);
//		if(getConnector().getSessionDataStructureFactory() != _dsFactory)
//			getConnector().setSessionDataStructureFactory(_dsFactory);
		getClientConfig().setTcpNoDelay(true);
		return super.startClient(addr);
	}

	@Override
	public void sessionCreated(IoSession session) throws Exception
	{
	}

	@Override
	protected void onAddSession(IoSession session)
	{
		write(session, IoBuffer.allocate(TEST_ECHO_SIZE).sweep());
	}

	@Override
	protected void onDelSession(IoSession session)
	{
		_closedCount.countDown();
	}

	@Override
	public void messageReceived(IoSession session, Object message)
	{
		if(_recvCount.incrementAndGet() <= TEST_ECHO_COUNT)
			write(session, message);
		else
			session.closeNow();
	}

	public static void main(String[] args) throws IOException, InterruptedException
	{
		Log.removeAppendersFromArgs(args);
		System.out.println("TestEcho: start: " + TEST_CLIENT_COUNT);
		if(args.length > 0) TEST_ECHO_SIZE = Integer.parseInt(args[0]);
		if(args.length > 1) TEST_ECHO_COUNT = Integer.parseInt(args[1]);
		IoBuffer.setUseDirectBuffer((args.length > 2 ? Integer.parseInt(args[2]) : 0) > 0);
		int count = (args.length > 3 ? Integer.parseInt(args[3]) : 0);
		IoBuffer.setAllocator(count > 0 ? new TestCachedBufferAllocator(count, 64 * 1024) : new SimpleBufferAllocator());
		long time = System.currentTimeMillis();
		TestEcho mgr = new TestEcho();
		mgr.startServer(new InetSocketAddress("0.0.0.0", 9123));
		for(int i = 0; i < TEST_CLIENT_COUNT; ++i)
			mgr.startClient(new InetSocketAddress("127.0.0.1", 9123));
		_closedCount.await();
		System.out.println("TestEcho: end (" + (System.currentTimeMillis() - time) + " ms)");
		System.out.println(TestCachedBufferAllocator.allocCount.get());
		System.out.println(TestCachedBufferAllocator.cacheCount.get());
		System.out.println(TestCachedBufferAllocator.offerCount.get());
		// System.out.println(_wrqCount.get());
		System.exit(0);
	}
}

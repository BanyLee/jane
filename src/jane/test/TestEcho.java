package jane.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.session.AbstractIoSession;
import org.apache.mina.core.session.DefaultIoSessionDataStructureFactory;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.core.write.WriteRequestQueue;
import jane.core.CachedIoBufferAllocator;
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

	private static final AtomicInteger _wrqCount = new AtomicInteger();

	private static final DefaultIoSessionDataStructureFactory _dsFactory = new DefaultIoSessionDataStructureFactory()
	{
		@Override
		public WriteRequestQueue getWriteRequestQueue(final IoSession session) throws Exception
		{
			_wrqCount.incrementAndGet();
			return new WriteRequestQueue()
			{
				private final ArrayDeque<WriteRequest> _wrq = new ArrayDeque<>();

				@Override
				public synchronized int size()
				{
					return _wrq.size();
				}

				@Override
				public synchronized boolean isEmpty()
				{
					return _wrq.isEmpty();
				}

				@Override
				public synchronized void clear()
				{
					_wrq.clear();
				}

				@Override
				public WriteRequest poll()
				{
					WriteRequest wr;
					synchronized(this)
					{
						wr = _wrq.pollFirst();
					}
					if(wr == AbstractIoSession.CLOSE_REQUEST)
					{
						wr = null;
						session.closeNow();
						dispose();
					}
					return wr;
				}

				@Override
				public synchronized void offer(WriteRequest writeRequest) // message must be IoBuffer or FileRegion
				{
					_wrq.addLast(writeRequest);
				}

				@Override
				public void dispose()
				{
				}

				@Override
				public synchronized String toString()
				{
					return _wrq.toString();
				}
			};
		}
	};

	public static final TestPerf[] perf = new TestPerf[20];

	static
	{
		for(int i = 0; i < perf.length; ++i)
			perf[i] = new TestPerf();
	}

	@Override
	public void startServer(SocketAddress addr) throws IOException
	{
		setIoThreadCount(1);
		if(getAcceptor().getSessionDataStructureFactory() != _dsFactory)
			getAcceptor().setSessionDataStructureFactory(_dsFactory);
		getServerConfig().setReuseAddress(true);
		getServerConfig().setTcpNoDelay(true);
		super.startServer(addr);
	}

	@Override
	public ConnectFuture startClient(SocketAddress addr)
	{
		setIoThreadCount(1);
		if(getConnector().getSessionDataStructureFactory() != _dsFactory)
			getConnector().setSessionDataStructureFactory(_dsFactory);
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
//		perf[6].begin();
		write(session, IoBuffer.allocate(TEST_ECHO_SIZE).sweep());
//		perf[6].end();
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
		{
//			perf[6].begin();
			write(session, message);
//			perf[6].end();
		}
		else
			session.closeNow();
	}

	public static void main(String[] args) throws IOException, InterruptedException
	{
		Log.removeAppendersFromArgs(args);
		System.out.println("TestEcho: start: " + TEST_CLIENT_COUNT);
		if(args.length > 0) TEST_ECHO_SIZE = Integer.parseInt(args[0]);
		if(args.length > 1) TEST_ECHO_COUNT = Integer.parseInt(args[1]);
		CachedIoBufferAllocator.globalSet((args.length > 2 ? Integer.parseInt(args[2]) : 0) > 0,
				args.length > 3 ? Integer.parseInt(args[3]) : 0, 64 * 1024);
		long time = System.currentTimeMillis();
//		perf[2].begin();
//		perf[0].begin();
		TestEcho mgr = new TestEcho();
		mgr.startServer(new InetSocketAddress("0.0.0.0", 9123));
		for(int i = 0; i < TEST_CLIENT_COUNT; ++i)
			mgr.startClient(new InetSocketAddress("127.0.0.1", 9123));
//		perf[0].end();
//		perf[1].begin();
		_closedCount.await();
//		perf[1].end();
//		perf[2].end();
		System.out.println("TestEcho: end (" + (System.currentTimeMillis() - time) + " ms)");
		System.out.println(CachedIoBufferAllocator.allocCount.get());
		System.out.println(CachedIoBufferAllocator.cacheCount.get());
		System.out.println(CachedIoBufferAllocator.offerCount.get());
		System.out.println(_wrqCount.get());
//		for(int i = 0; i < perf.length; ++i)
//			System.out.println("perf[" + i + "]: " + perf[i].getAllMs() + ", " + perf[i].getAllCount());
		System.exit(0);
	}
}

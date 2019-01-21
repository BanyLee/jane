package jane.test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 无锁的定长非blocking的Ring Buffer. 缓存Object的队列,非字符流/字节流的buffer
 */
public final class TestMpMcRingBuffer
{
	private final Object[]	 buffer;
	private final int		 idxMask;
	private final AtomicLong writeIdx	 = new AtomicLong();
	private final AtomicLong readHeadIdx = new AtomicLong();
	private final AtomicLong readTailIdx = new AtomicLong();

	/**
	 * @param bufSize buffer数组的长度. 必须是2的幂
	 */
	public TestMpMcRingBuffer(int bufSize)
	{
		if(bufSize <= 0 || Integer.highestOneBit(bufSize) != bufSize)
			throw new IllegalArgumentException();
		buffer = new Object[bufSize];
		idxMask = bufSize - 1;
	}

	public boolean offer(Object obj)
	{
		if(obj == null)
			throw new NullPointerException();
		for(;;)
		{
			long wi = writeIdx.get();
			if(readTailIdx.get() + idxMask < wi)
				return false;
			if(writeIdx.compareAndSet(wi, wi + 1))
			{
				buffer[(int)wi & idxMask] = obj;
				return true;
			}
		}
	}

	public Object poll()
	{
		for(;;)
		{
			long ri = readHeadIdx.get();
			if(ri == writeIdx.get())
				return null;
			long ri1 = ri + 1;
			if(readHeadIdx.compareAndSet(ri, ri1))
			{
				for(int i = (int)ri & idxMask, n = 0;;)
				{
					Object obj = buffer[i];
					if(obj != null)
					{
						buffer[i] = null;
						for(n = 0;;)
						{
							if(readTailIdx.compareAndSet(ri, ri1))
								return obj;
							if(++n > 127)
								Thread.yield();
						}
					}
					if(++n > 127)
						Thread.yield();
				}
			}
		}
	}

	public Object peek()
	{
		for(int n = 0;;)
		{
			long ri = readHeadIdx.get();
			if(ri == writeIdx.get())
				return null;
			Object obj = buffer[(int)ri & idxMask];
			if(obj != null && readHeadIdx.get() == ri)
				return obj;
			if(++n > 127)
				Thread.yield();
		}
	}

	public static void main(String[] args) throws InterruptedException
	{
		final long t = System.nanoTime();

		final long TEST_COUNT = 10_000_000;
		final int BUF_SIZE = 1024;
		final int WRITER_COUNT = 2;
		final int READER_COUNT = 2;

		final TestMpMcRingBuffer buf = new TestMpMcRingBuffer(BUF_SIZE);
		final AtomicLong wc = new AtomicLong(TEST_COUNT);
		final AtomicLong rc = new AtomicLong(TEST_COUNT);
		final AtomicLong wr = new AtomicLong();
		final AtomicLong rr = new AtomicLong();
		final Thread[] wts = new Thread[WRITER_COUNT];
		final Thread[] rts = new Thread[READER_COUNT];

		for(int i = 0; i < WRITER_COUNT; ++i)
		{
			wts[i] = new Thread(() ->
			{
				for(long c; (c = wc.decrementAndGet()) >= 0;)
				{
					int v = (int)c & 127;
					wr.addAndGet(v);
					while(!buf.offer(Integer.valueOf(v)))
						Thread.yield();
				}
			}, "WriterThread" + i);
			wts[i].start();
		}

		for(int i = 0; i < READER_COUNT; ++i)
		{
			rts[i] = new Thread(() ->
			{
				while(rc.decrementAndGet() >= 0)
				{
					Object v;
					while((v = buf.poll()) == null)
						Thread.yield();
					rr.addAndGet((Integer)v);
				}
			}, "ReaderThread" + i);
			rts[i].start();
		}

		for(int i = 0; i < WRITER_COUNT; ++i)
			wts[i].join();
		for(int i = 0; i < READER_COUNT; ++i)
			rts[i].join();

		System.out.format("wr = %d%nrr = %d%ntime = %d ms%n", wr.get(), rr.get(), (System.nanoTime() - t) / 1_000_000);
	}
}

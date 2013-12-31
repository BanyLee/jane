package jane.test;

import java.net.InetSocketAddress;
import org.apache.mina.core.session.IoSession;
import jane.bean.AllBeans;
import jane.core.BeanManager;
import jane.core.Log;

public final class TestClient extends BeanManager
{
	private static final int        MAX_CONNECT_DELAY_SEC = 60;
	private static final TestClient _instance             = new TestClient();

	public static TestClient instance()
	{
		return _instance;
	}

	private TestClient()
	{
		setHandlers(AllBeans.getTestClientHandlers());
	}

	@Override
	protected void onAddSession(IoSession session)
	{
	}

	@Override
	protected void onDelSession(IoSession session)
	{
		// startClient((InetSocketAddress)session.getRemoteAddress());
	}

	@Override
	protected int onConnectFailed(InetSocketAddress address, int count, Object ctx)
	{
		Log.log.error("{}: onConnectFailed: addr={},count={}", getName(), address, count);
		int sec = 1 << count;
		return sec < MAX_CONNECT_DELAY_SEC ? sec : MAX_CONNECT_DELAY_SEC;
	}
}

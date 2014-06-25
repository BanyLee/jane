package jane.test;

import static jane.bean.AllTables.Benchmark;
import static jane.bean.AllTables.TestTable;
import java.util.Map;
import java.util.Map.Entry;
import jane.core.DBManager;
import jane.core.Octets;
import jane.core.Procedure;
import jane.core.SContext.Rec;
import jane.core.SMap;
import jane.core.SMap.SMapListener;
import jane.core.StorageLevelDB;
import jane.bean.AllTables;
import jane.bean.TestBean;
import jane.bean.TestType;
import jane.bean.TestType.Safe;

public final class TestUndo
{
	public static void main(String[] args) throws Throwable
	{
		DBManager.instance().startup(StorageLevelDB.instance());
		AllTables.register();
		System.out.println("start");
		final long id = 1;
		final int v = 1;

		new Procedure()
		{
			@Override
			protected void onProcess() throws Exception
			{
				lock(Benchmark.lockId(id));
				TestBean.Safe a = Benchmark.getSafe(id);
				if(a == null)
				{
					TestBean aa = new TestBean();
					aa.setValue1(v);
					System.out.println("new: " + aa.getValue1());
					Benchmark.putSafe(id, aa);
				}
				else
				{
					System.out.println("get: " + a.getValue1());
					if(a.getValue1() != v)
					{
						a.setValue1(v);
						System.out.println("set: " + a.getValue1());
					}
				}
				System.out.println("=== 1");
			}
		}.run();

		new Procedure()
		{
			@Override
			protected void onProcess() throws Exception
			{
				lock(Benchmark.lockId(id));
				TestBean.Safe a = Benchmark.getSafe(id);
				System.out.println("get: " + a.getValue1());
				a.setValue1(v + 1);
				System.out.println("set: " + a.getValue1());
				System.out.println("=== 2");
				throw new Exception("only-for-test-rollback");
			}
		}.run();

		new Procedure()
		{
			@Override
			protected void onProcess() throws Exception
			{
				lock(Benchmark.lockId(id));
				TestBean.Safe a = Benchmark.getSafe(id);
				System.out.println("get: " + a.getValue1());
				a.setValue1(v + 2);
				System.out.println("set: " + a.getValue1());
				System.out.println("=== 3");
			}
		}.run();

		new Procedure()
		{
			@Override
			protected void onProcess() throws Exception
			{
				lock(Benchmark.lockId(id));
				TestBean.Safe a = Benchmark.getSafe(id);
				System.out.println("get: " + a.getValue1());
				System.out.println("=== 4");
			}
		}.run();

		TestType.Safe.onListenerV18(new SMapListener()
		{
			@Override
			public void onChanged(Rec rec, Map<?, ?> changed)
			{
				System.out.println("changed key: " + rec.getKey());
				System.out.println("changed value:");
				for(Entry<?, ?> e : changed.entrySet())
					System.out.println(((Octets)e.getKey()).dump() + ": " + e.getValue());
			}
		});

		new Procedure()
		{
			@Override
			protected void onProcess() throws Exception
			{
				lock(TestTable.lockId(1));
				TestType.Safe a = TestTable.getSafe(1);
				if(a == null)
				{
					a = (Safe)new TestType().safe();
					TestTable.putSafe(1, a);
				}
				SMap<Octets, TestBean> map = a.getV18();
				map.put(Octets.wrap("a"), new TestBean(11, 22));
				map.put(Octets.wrap("b"), new TestBean(33, 44));
				map.remove(Octets.wrap("a"));
				System.out.println("=== 5");
			}
		}.run();

		new Procedure()
		{
			@Override
			protected void onProcess() throws Exception
			{
				lock(TestTable.lockId(1));
				TestType.Safe a = TestTable.getSafe(1);
				SMap<Octets, TestBean> map = a.getV18();
				map.remove(Octets.wrap("a"));
				TestBean.Safe b = map.getSafe(Octets.wrap("b"));
				b.setValue1(55);
				System.out.println("=== 6");
			}
		}.run();

		System.out.println("checkpoint");
		DBManager.instance().checkpoint();
		System.out.println("end");
		System.exit(0);
	}
}

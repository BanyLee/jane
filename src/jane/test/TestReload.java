package jane.test;

import java.util.ArrayList;
import java.util.List;
import jane.core.Util;
import jane.tool.ClassReloader;

// java -javaagent:jane-core.jar -cp bin jane.test.TestReload
public final class TestReload
{
	private int a = 123;

	public void test()
	{
		System.out.println("func-1");

		new Runnable()
		{
			@Override
			public void run()
			{
				System.out.println("inner-1: " + a);
			}
		}.run();
	}

	public static void main(String[] args) throws Exception
	{
		TestReload a = new TestReload();
		a.test();

		System.out.print("now modify TestReload classes and press enter ... ");
		System.in.read();

		List<byte[]> classes = new ArrayList<>();
		classes.add(Util.readFileData("bin/jane/test/TestReload.class"));
		classes.add(Util.readFileData("bin/jane/test/TestReload$1.class"));
		ClassReloader.reloadClasses(classes);

		a.test();
		new TestReload().test();

		System.out.println("done!");
	}
}

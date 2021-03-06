package games.strategy.engine.message;

import java.util.Comparator;
import java.util.List;

import junit.framework.TestCase;

public class EndPointTest extends TestCase {
  @SuppressWarnings("rawtypes")
  public void testEndPoint() {
    final EndPoint endPoint = new EndPoint("", Comparator.class, false);
    endPoint.addImplementor(new Comparator() {
      @Override
      public int compare(final Object o1, final Object o2) {
        return 2;
      }
    });
    final RemoteMethodCall call = new RemoteMethodCall("", "compare", new Object[] {"", ""},
        new Class[] {Object.class, Object.class}, Comparator.class);
    final List<RemoteMethodCallResults> results = endPoint.invokeLocal(call, endPoint.takeANumber(), null);
    assertEquals(results.size(), 1);
    assertEquals(2, (results.iterator().next()).getRVal());
  }
}

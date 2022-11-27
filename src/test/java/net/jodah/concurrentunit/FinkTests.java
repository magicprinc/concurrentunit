package net.jodah.concurrentunit;

import net.jodah.concurrentunit.internal.ThrowingAction;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static net.jodah.concurrentunit.Waiter.line;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests for a.fink addons to {@link Waiter}.
 */
@Test
public class FinkTests {

  @Test
  public void testUEH() throws Exception {
    final Waiter w = new Waiter();

    new Thread(new Runnable() {
      public void run() {
        w.setUncaughtExceptionHandler(); // "fire and forget" solution for unexpected throwables

        long d = 0;

        System.out.println("The answer: "+(2022/d));
      }
    }).start();

    try {
      w.await();
      fail();
    } catch (AssertionError e) {
      assertTrue(e.getCause() instanceof ArithmeticException);
      Throwable ex = w.assertStartsWith("java.lang.ArithmeticException: / by zero",
          e.getCause());
      assertSame(ex, e.getCause());
      w.assertStartsWith("java.lang.AssertionError: Uncaught Exception! In thread: Thread[Thread-", e);
    }

  }

  @Test
  public void testAssertThrows1() throws Throwable {
    final Waiter w = new Waiter();

    try {
      w.assertThrows(NullPointerException.class, new ThrowingAction() {
        @Override public void exec() throws Throwable{
          // nothing happens
        }
      });
      fail();
    } catch (Throwable e) {
      w.assertStartsWith(
          "java.lang.AssertionError: [assertThrows] No exception was thrown! Expected: NullPointerException\\n\\n@ net.jodah.concurrentunit.FinkTests$",
          e);
    }
  }

  public void testAssertThrows2() throws Throwable {
    final Waiter w = new Waiter();

    Throwable ex = w.assertThrows(ArithmeticException.class, new ThrowingAction() {
      @Override public void exec() throws Throwable{
        long d = 0;
        d = 1/d;
        w.fail();
      }
    });
    w.assertStartsWith("java.lang.ArithmeticException: / by z", ex);
    assertEquals("java.lang.ArithmeticException: / by zero", ex.toString());
  }

  public void testAssertThrows3() throws Throwable {
    final Waiter w = new Waiter();

    Throwable e = w.assertThrows(AssertionError.class, new ThrowingAction() {
        @Override public void exec() throws Throwable{
          w.fail("test", new IOException("fake"));
        }
      });
    w.assertStartsWith("java.lang.AssertionError: test\tThread: Thread[", e);
    w.assertStartsWith("java.io.IOException: fake", e.getCause());
  }

  @Test
  public void testAssertThrows4() throws Throwable {
    final Waiter w = new Waiter();

    try {
      w.assertThrows(NullPointerException.class, new ThrowingAction() {
        @Override public void exec() throws Throwable{
          fail("$test wrong type$");
        }
      });
      fail();
    } catch (AssertionError e) {
      w.assertStartsWith(
        "java.lang.AssertionError: [assertThrows] Bad thrown exception type! " +
        "Expected: NullPointerException\n\t^^^ but → java.lang.AssertionError: $test wrong type$",
        e);
    }
  }


  @Test
  public void testLine(){
    assertEquals(line(null), "null");
    assertEquals(line(new Object[]{new Integer[]{1,2,3}, new String[]{"aa","bb"}}),
        "[[1, 2, 3], [aa, bb]]");
    assertEquals(line(Integer.decode("42")), "42");
  }

  @Test
  public void testStartsWith(){
    final Waiter w = new Waiter();
    w.assertStartsWith(" ma", " mama");

    AssertionError ae = w.assertThrows(AssertionError.class, new ThrowingAction() {
      @Override public void exec(){
        w.assertStartsWith("x", 'y');
      }
    });
    w.assertStartsWith("AssertionError: assertStartsWith: expected:<x>, but was:<y> (Character)", ae);

    ae = w.assertThrows(AssertionError.class, new ThrowingAction() {
      @Override public void exec(){
        w.assertStartsWith(null, 'y');
      }
    });
    w.assertStartsWith("AssertionError: assertStartsWith: Expected toString is empty!" +
      " Actual: y\t(java.lang.Character)", ae);

    ae = w.assertThrows(AssertionError.class, new ThrowingAction() {
      @Override public void exec(){
        w.assertStartsWith("x", null);
      }
    });
    assertEquals(ae.getMessage(), "assertStartsWith: expected:<x>, but was:<null>");
  }


  @Test
  public void testDirectExec(){
    final Waiter w = new Waiter();

    final AtomicInteger cnt = new AtomicInteger();

    w.run(new Runnable() {
      @Override public void run(){
        cnt.incrementAndGet();
      }
    });
    w.assertEquals(1, cnt.get());
    // 2
    assertEquals(2, w.call(new Callable<Integer>() {
      @Override public Integer call(){
        return cnt.incrementAndGet();
      }
    }).intValue());
    w.assertEquals(2, cnt.get());
    // 3
    w.exec(new ThrowingAction() {
      @Override public void exec() throws Throwable{
        cnt.incrementAndGet();
      }
    });
    w.assertEquals(3, cnt.get());
  }


  @Test
  public void testOthers(){
    final Waiter w = new Waiter();

    AssertionError ae = w.assertThrows(AssertionError.class, new ThrowingAction() {
      @Override public void exec() throws Throwable{
        w.assertNotNull(null);
      }
    });
    assertEquals(ae.toString(), "java.lang.AssertionError: assertNotNull: expected not null");

    ae = w.assertThrows(AssertionError.class, new ThrowingAction() {
      @Override public void exec() throws Throwable{
        w.assertFalse(true);
      }
    });
    assertEquals(ae.toString(), "java.lang.AssertionError: assertFalse: expected false");

    assertTrue(w.assertEquals(null, null));
    assertTrue(w.assertEquals("Zv5", "Zv5"));

    ae = w.assertThrows(AssertionError.class, new ThrowingAction() {
      @Override public void exec() throws Throwable{
        w.assertEquals("ZvExp", "Zv5");
      }
    });
    assertEquals(ae.toString(), "java.lang.AssertionError: assertEquals: expected:<ZvExp>, but was:<Zv5>");
  }


  /**
   * Should throw an assertion error caused by the failure.
   */
  @Test
  public void waitShouldSupportFail() throws Throwable {
    final Waiter w = new Waiter();

    new Thread(new Runnable() {
      public void run() {
        w.fail(new IllegalArgumentException());
      }
    }).start();

    try {
      w.await();
      fail();
    } catch (AssertionError e) {
      assertTrue(e.getCause() instanceof IllegalArgumentException);
    }
  }


  @Test(expectedExceptions = IOException.class)
  public void shouldRethrow1() throws Throwable {
    final Waiter w = new Waiter();

    new Thread(new Runnable() {
      public void run() {
        w.run(new Runnable() {
          @Override public void run(){
            Waiter.sneakyThrow(new IOException());
          }
        });
      }
    }).start();

    w.await();
    fail();
  }

  @Test(expectedExceptions = IOException.class)
  public void shouldRethrow2() throws Throwable {
    final Waiter w = new Waiter();

    new Thread(new Runnable() {
      public void run() {
        w.call(new Callable<Integer>() {
          @Override public Integer call() throws Exception {
            throw new IOException();
          }
        });
      }
    }).start();

    w.await();
    fail();
  }

  @Test(expectedExceptions = IOException.class)
  public void shouldRethrow3() throws Throwable {
    final Waiter w = new Waiter();

    new Thread(new Runnable() {
      public void run() {
        w.exec(new ThrowingAction() {
          @Override public void exec(){
            Waiter.sneakyThrow(new IOException());
          }
        });
      }
    }).start();

    w.await();
    fail();
  }

  @Test
  public void testExecs() throws Exception{
    final Waiter w = new Waiter();

    final AtomicInteger cnt = new AtomicInteger();

    Runnable r = w.runnable(new Runnable() {
      @Override public void run(){
        cnt.incrementAndGet();
      }
    });
    r.run();
    w.assertEquals(1, cnt.get());
    w.assertStartsWith("Waiter.runnable: net.jodah.concurrentunit.FinkTests$", r);
    // 2
    Callable<Integer> callable = w.callable(new Callable<Integer>() {
      @Override public Integer call(){
        return cnt.incrementAndGet();
      }
    });
    assertEquals(2, callable.call().intValue());
    w.assertEquals(2, cnt.get());
    w.assertStartsWith("Waiter.callable: net.jodah.concurrentunit.FinkTests$", callable);

    // 3
    r = w.wrap(new ThrowingAction() {
      @Override public void exec() throws Throwable{
        cnt.incrementAndGet();
      }
    });
    r.run();
    w.assertEquals(3, cnt.get());
    w.assertStartsWith("Waiter.wrap: net.jodah.concurrentunit.FinkTests$", r);
  }

  @Test
  public void shouldRethrow1Runnable () throws Throwable {
    final Waiter w = new Waiter();

    ExecutorService ex = Executors.newFixedThreadPool(1);
    Future<?> f = ex.submit(w.runnable(new Runnable()
    {
      @Override public void run(){
        Waiter.sneakyThrow(new IOException());
      }
    }));
    try {
      w.await();
      fail();
    } catch (Throwable expected) {
      assertTrue(expected instanceof IOException);
    }
    assertTrue(f.isDone());
    try {
      f.get();
      fail();
    } catch (ExecutionException ee) {
      assertTrue(ee.getCause() instanceof IOException);
    }
    assertEquals(ex.shutdownNow().size(), 0);
  }

  @Test
  public void shouldRethrowE2Callable() throws Throwable {
    final Waiter w = new Waiter();

    ExecutorService ex = Executors.newFixedThreadPool(1);
    Future<?> f = ex.submit(w.callable(new Callable<Integer>()
    {
      @Override public Integer call() throws Exception {
        throw new IOException();
      }
    }));
    try {
      w.await();
      fail();
    } catch (Throwable expected) {
      assertTrue(expected instanceof IOException);
    }
    assertTrue(f.isDone());
    try {
      f.get();
      fail();
    } catch (ExecutionException ee) {
      assertTrue(ee.getCause() instanceof IOException);
    }
    assertEquals(ex.shutdownNow().size(), 0);
  }

  @Test
  public void shouldRethrowE3() throws Throwable {
    final Waiter w = new Waiter();

    ExecutorService ex = Executors.newFixedThreadPool(1);
    Future<?> f = ex.submit(w.wrap(new ThrowingAction()
    {
      @Override public void exec(){
        Waiter.sneakyThrow(new IOException());
      }
    }));
    try {
      w.await();
      fail();
    } catch (Throwable expected) {
      assertTrue(expected instanceof IOException);
    }
    assertTrue(f.isDone());
    try {
      f.get();
      fail();
    } catch (ExecutionException ee) {
      assertTrue(ee.getCause() instanceof IOException);
    }
    assertEquals(ex.shutdownNow().size(), 0);

  }

}
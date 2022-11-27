package net.jodah.concurrentunit;

import net.jodah.concurrentunit.internal.CUTools;
import net.jodah.concurrentunit.internal.CUTools.ThrowingActionImpl;
import net.jodah.concurrentunit.internal.ThrowingAction;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static net.jodah.concurrentunit.internal.CUTools.line;
import static net.jodah.concurrentunit.internal.CUTools.of;
import static net.jodah.concurrentunit.internal.CUTools.sneakyThrow;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import static org.testng.AssertJUnit.assertFalse;

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
            sneakyThrow(new IOException());
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
            sneakyThrow(new IOException());
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
        sneakyThrow(new IOException());
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
        sneakyThrow(new IOException());
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
  public void testUtils(){
    assertEquals(  CUTools.format(Integer.decode("-41"), new Object[]{1,2,3}, "test"),
     "test: expected:<-41> (Integer), but was:<[1, 2, 3]> (Object[])");
    assertEquals(  CUTools.format(Integer.decode("-41"), "-41", "Xyz"),
        "Xyz: expected:<-41> (Integer), but was:<-41>");
  }


  @Test
  public void example() throws InterruptedException, TimeoutException, ExecutionException{
    ExecutorService ex = Executors.newCachedThreadPool();
    final Waiter w = new Waiter();

    final AtomicLong cnt = new AtomicLong();

    ThrowingActionImpl task1 = of(new ThrowingAction() {
      @Override public void exec() throws Throwable{
        Thread.sleep(500);
        cnt.incrementAndGet();
      }
    });

    ThrowingActionImpl task2 = of(new ThrowingAction() {
      @Override public void exec() throws Throwable{
        Thread.sleep(700);
        throw new IOException("task2 fake failure");
      }
    });

    LinkedList<Future<?>> futures = new LinkedList<Future<?>>();

    for (int i=0; i<20; i++) {// 60
      futures.add(ex.submit(w.callable(task1)));
      futures.add(ex.submit(w.runnable(task1)));
      futures.add(ex.submit(w.wrap(task1)));
    }

    assertEquals(futures.size(), 60);
    w.await(5000, 60);

    while (!futures.isEmpty()) {
      Future<?> f = futures.pop();
      assertTrue(f.isDone());
      assertFalse(f.isCancelled());
      assertNull(f.get());
    }
    assertEquals(w.getRemainingResumes(), 0);

    // task2 - failures
    for (int i=0; i<20; i++) {
      futures.add(ex.submit(w.callable(task2)));
      futures.add(ex.submit(w.runnable(task2)));
      futures.add(ex.submit(w.wrap(task2)));
    }
    assertEquals(futures.size(), 60);

    IOException e = w.assertThrows(IOException.class, new ThrowingAction() {
      @Override public void exec() throws Throwable{
        w.await(5000, 60);
        fail();
      }
    });
    assertEquals(e.toString(), "java.io.IOException: task2 fake failure");
    assertEquals(w.getRemainingResumes(), 0);

//    for (int i=0; i<60; i++){
//      IOException e = w.assertThrows(IOException.class, new ThrowingAction() {
//        @Override public void exec() throws Throwable{
//          w.await(5000, 1);
//          fail();
//        }
//      });
//      assertEquals(e.toString(), "java.io.IOException: task2 fake failure");
//    }

    Thread.sleep(5000);
    assertEquals(w.getRemainingResumes(), 0);

    while (!futures.isEmpty()) {
      final Future<?> f = futures.pop();
      assertTrue(f.isDone());
      assertFalse(f.isCancelled());
      ExecutionException ee = w.assertThrows(ExecutionException.class, new ThrowingAction() {
        @Override public void exec() throws Throwable{
          assertNull(f.get());
          fail();
        }
      });
      assertEquals(ee.toString(), "java.util.concurrent.ExecutionException: java.io.IOException: task2 fake failure");
    }

    assertEquals(ex.shutdownNow().size(), 0);
  }
}
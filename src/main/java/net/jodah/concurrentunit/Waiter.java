/*
 * Copyright 2010-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package net.jodah.concurrentunit;

import net.jodah.concurrentunit.internal.ReentrantCircuit;
import net.jodah.concurrentunit.internal.ThrowingAction;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static net.jodah.concurrentunit.internal.CUTools.assertionError;
import static net.jodah.concurrentunit.internal.CUTools.clsName;
import static net.jodah.concurrentunit.internal.CUTools.contains;
import static net.jodah.concurrentunit.internal.CUTools.format;
import static net.jodah.concurrentunit.internal.CUTools.line;
import static net.jodah.concurrentunit.internal.CUTools.sneakyThrow;

/**
 * Waits on a test, carrying out assertions, until being resumed.
 *
 * @author Jonathan Halterman
 */
public class Waiter implements Thread.UncaughtExceptionHandler {
  private static final String TIMEOUT_MESSAGE = "Test timed out while waiting for an expected result, expectedResumes: %d, actualResumes: %d, failures: %d";
  private final AtomicInteger remainingResumes = new AtomicInteger(0);
  private final ReentrantCircuit circuit = new ReentrantCircuit();
  private final ConcurrentLinkedQueue<AssertionError> failure = new ConcurrentLinkedQueue<AssertionError>();
  private final boolean failFast;
  private final boolean verify;
  private final String name;

  /**
   * Creates a new Waiter.
   */
  public Waiter() { this("", true, false); }

  public Waiter(String name, boolean failFast, boolean verify){
    circuit.open();

    this.name = name;
    this.failFast = failFast;
    this.verify = verify;
  }

  /**
   * Asserts that the {@code expected} values equals the {@code actual} value
   *
   * @throws AssertionError when the assertion fails
   */
  public boolean assertEquals(Object expected, Object actual) {
    if (expected == actual) // both null or same
      return true;
    if (expected != null && expected.equals(actual))
      return true;
    return fail(format(expected, actual, "assertEquals"));
  }

  /**
   * Asserts that the {@code condition} is false.
   *
   * @throws AssertionError when the assertion fails
   */
  public void assertFalse(boolean condition) {
    if (condition)
      fail("assertFalse: expected false");
  }

  /**
   * Asserts that the {@code object} is not null.
   *
   * @throws AssertionError when the assertion fails
   */
  public void assertNotNull(Object object) {
    if (object == null)
      fail("assertNotNull: expected not null");
  }

  /**
   * Asserts that the {@code object} is null.
   *
   * @throws AssertionError when the assertion fails
   */
  public void assertNull(Object object) {
    if (object != null)
      fail(format(null, object, "assertNull"));
  }

  /**
   * Asserts that the {@code condition} is true.
   *
   * @throws AssertionError when the assertion fails
   */
  public void assertTrue(boolean condition) {
    if (!condition)
      fail("assertTrue: expected true");
  }

  /**
   * Asserts that {@code actual} satisfies the condition specified by {@code matcher}.
   *
   * @throws AssertionError when the assertion fails
   */
  public <T> void assertThat(T actual, org.hamcrest.Matcher<? super T> matcher) {
    try {
      org.hamcrest.MatcherAssert.assertThat(actual, matcher);
    } catch (AssertionError e) {
      fail(e);
    }
  }

  /**
   * Waits until {@link #resume()} is called, or the test is failed.
   *
   * @throws TimeoutException if the operation times out while waiting
   * @throws InterruptedException if the operations is interrupted while waiting
   * @throws AssertionError if any assertion fails while waiting
   */
  public void await() throws TimeoutException, InterruptedException {
    await(0, MILLISECONDS, 1);
  }

  /**
   * Waits until the {@code delay} has elapsed, {@link #resume()} is called, or the test is failed.
   *
   * @param delayMillis Delay to wait in milliseconds
   * @throws TimeoutException if the operation times out while waiting
   * @throws InterruptedException if the operations is interrupted while waiting
   * @throws AssertionError if any assertion fails while waiting
   */
  public void await(long delayMillis) throws TimeoutException, InterruptedException {
    await(delayMillis, MILLISECONDS, 1);
  }

  /**
   * Waits until the {@code delay} has elapsed, {@link #resume()} is called, or the test is failed.
   *
   * @param delay Delay to wait for
   * @param timeUnit TimeUnit to delay for
   * @throws TimeoutException if the operation times out while waiting
   * @throws InterruptedException if the operations is interrupted while waiting
   * @throws AssertionError if any assertion fails while waiting
   */
  public void await(long delay, TimeUnit timeUnit) throws TimeoutException, InterruptedException {
    await(delay, timeUnit, 1);
  }

  /**
   * Waits until the {@code delay} has elapsed, {@link #resume()} is called {@code expectedResumes} times, or the test
   * is failed.
   *
   * @param delay Delay to wait for in milliseconds
   * @param expectedResumes Number of times {@link #resume()} is expected to be called before the awaiting thread is
   *          resumed
   * @throws TimeoutException if the operation times out while waiting
   * @throws InterruptedException if the operations is interrupted while waiting
   * @throws AssertionError if any assertion fails while waiting
   */
  public void await(long delay, int expectedResumes) throws TimeoutException, InterruptedException {
    await(delay, MILLISECONDS, expectedResumes);
  }

  /**
   * Waits until the {@code delay} has elapsed, {@link #resume()} is called {@code expectedResumes} times, or the test
   * is failed.
   *
   * @param delay Delay to wait for
   * @param timeUnit TimeUnit to delay for
   * @param expectedResumes Number of times {@link #resume()} is expected to be called before the awaiting thread is
   *          resumed
   * @throws TimeoutException if the operation times out while waiting
   * @throws InterruptedException if the operations is interrupted while waiting
   * @throws AssertionError if any assertion fails while waiting
   */
  public void await(long delay, TimeUnit timeUnit, final int expectedResumes) throws TimeoutException, InterruptedException {
    AssertionError ae = null;
    final long ntime0 = System.nanoTime();
    try {
      if (failure.isEmpty() || !failFast) {
        synchronized (this) {
          int remaining = remainingResumes.addAndGet(expectedResumes);
          if (remaining > 0)
            circuit.open();
        }

        if (delay <= 0)
          circuit.await();
        else if (!circuit.await(delay, timeUnit)) {
          int actualResumes = expectedResumes - remainingResumes.get();
          throw new TimeoutException(String.format(TIMEOUT_MESSAGE, expectedResumes, actualResumes, failure.size()));
        }
      }
    } finally {
      final double time = (System.nanoTime() - ntime0) / 1000000.0;
      final String circuitStatus = circuit.toString();
      final int remaining = remainingResumes.get();
      final AssertionError f = failure.poll();
      // reset
      remainingResumes.set(0);
      circuit.open();

      if (f != null || (verify && remaining != 0)) {
        int fqsize = failure.size();

        ae = new AssertionError("[Waiter."+name+".await: "+circuitStatus+"] Expected: "+expectedResumes+
          ", Actual: "+(expectedResumes - remaining)+", Remaining: "+remaining+
          ", Failure: "+fqsize+", Sum A+F: "+(expectedResumes - remaining + fqsize)+
          ", Time: "+ time + " : " + (timeUnit.toNanos(delay)/1000000.0));
        if (f != null)
          ae.initCause(f);

        System.err.println("{{{{{ ***** $$$ Statistics $$$ ****** "+line(ae, null));
        ae.printStackTrace();
        if (f != null)
          ae = f;

        int i = 1;
        AssertionError next = f;
        while (next != null) {
          System.err.println("----- "+i++ +") "+ line(next, null)+ " of " +ae);
          //todo next.printStackTrace();

          next = failure.poll();
          if (next == null) {
            break;
          }
        }
        System.err.println("}}}}} ***** $$$ Statistics $$$ ****** "+line(ae, null));
        assert failure.isEmpty() : "failure isn't empty after cleaning! "+ae;
      }
    }//t
    if (ae != null) {
      Throwable c = ae.getCause();
      if (c != null && ae.getMessage().startsWith("%$")) {
        new IllegalArgumentException("THROW: "+line(c, null) +" from "+ ae, c).printStackTrace();
        throw (AssertionError) sneakyThrow(c);
      }
      if (c instanceof AssertionError && c.getCause()!=null && line(c.getMessage(),"").startsWith("%$")){
        new IllegalArgumentException("THROW: "+line(c.getCause(), null) +" from "+ c+" from "+ ae, c.getCause()).printStackTrace();
        throw (AssertionError) sneakyThrow(c.getCause());
      }
      new IllegalArgumentException("THROW: "+line(ae, null), ae).printStackTrace();
      throw ae;
    }
  }//await

  /**
   * Resumes the waiter when the expected number of {@code #resume()} calls have occurred.
   */
  public synchronized void resume() {
    if (remainingResumes.decrementAndGet() <= 0)
      circuit.close();
  }

  /**
   * Fails the current test.
   *
   * @throws AssertionError Failure
   */
  public <V> V fail() throws AssertionError {
    return setFailure(null, null, null, null, false);
  }

  /**
   * Fails the current test for the given {@code reason}.
   *
   * @throws AssertionError
   */
  public <V> V fail (CharSequence messageReason) throws AssertionError {
    return setFailure( messageReason, null, null, null, false);
  }

  /**
   * Fails the current test with the given {@code reason}, sets the number of expected resumes to 0, and throws the
   * {@code reason} as an {@code AssertionError} in the main test thread and in the current thread.
   *
   * @throws AssertionError wrapping the {@code reason}
   */
  public <V> V fail (Throwable reason) throws AssertionError {
    return setFailure(null, reason, null, null, false);
  }

  protected <V> V setFailure (CharSequence message, Throwable cause, Thread thread, Object code, boolean rethrow){
    if (cause instanceof AssertionError) {
      if (!contains(failure, cause)) {
        failure.offer((AssertionError) cause);
        resume();//!!!
      }
      throw (AssertionError) sneakyThrow(cause); // 99% already "wrapped" with Thread and code
    }
    String m = line(message, "");
    String c = line(cause, "");

    m = m.isEmpty() ? c
        : c.isEmpty() ? m
        : m + " <= " + c;

    String ts = line(thread != null ? thread : Thread.currentThread(), "");
    if (!m.contains(ts)) {
      m = m + "\t# Thread: " + ts;
    }
    String cs = line(code, "");
    if (!m.contains(cs)) {
      m = m + "\t@ " + cs;
    }
    AssertionError ae = new AssertionError((rethrow && cause != null ? "%$": "") + m);
    if (cause != null)
      ae.initCause(cause);

    if (!contains(failure, cause)) {
      failure.offer(ae);
      resume();//!!!
    }

    if (rethrow && cause != null)
      throw (AssertionError) sneakyThrow(cause);
    else
      throw ae;
  }//setFailure

  /**
   * Rethrows the {@code failure} in the main test thread and in the current thread. Differs from
   * {@link #fail(Throwable)} which wraps a failure in an AssertionError before throwing.
   *
   * @throws Throwable the {@code failure}
   */
  public <V> V rethrow (Throwable ex) {
    return setFailure( null, ex, null, null, true);
  }

  public void setUncaughtExceptionHandler () {
    setUncaughtExceptionHandler(Thread.currentThread());
  }

  public void setUncaughtExceptionHandler (Thread t) {
    t.setUncaughtExceptionHandler(this);
  }

  @Override
  public void uncaughtException(Thread t, Throwable e){
    fail("Uncaught Exception! In thread: "+ t, e);
  }

  public <V> V fail (CharSequence message, Throwable cause) throws AssertionError {
    return setFailure(message, cause, null, null, false);
  }

  /**
   * <em>Assert</em> that execution of the supplied code throws
   * an exception of the {@code expectedThrowable} and return the exception.
   *
   * <p>If no exception is thrown, or if an exception of a different type is
   * thrown, this method will fail.
   *
   * <p>If you do not want to perform additional checks on the exception instance,
   * ignore the return value.
   */
  public <T extends Throwable> T assertThrows (Class<T> expectedThrowable, ThrowingAction runnable)
      throws AssertionError, ClassCastException {
    try {
      runnable.exec();
    } catch (Throwable actualThrown) {
      if (!expectedThrowable.isInstance(actualThrown)) { // 1) thrown 2) expected != actual
        String expected = clsName(expectedThrowable);

        Class<? extends Throwable> actualThrowable = actualThrown.getClass();
        String actual = clsName(actualThrowable);

        if (expected.equals(actual)) {
          // There must be multiple class loaders. Add the identity hash code so the message
          // doesn't say "expected: java.lang.String<my.package.MyException> ..."
          expected += "@" + Integer.toHexString(System.identityHashCode(expectedThrowable));
          actual += "@" + Integer.toHexString(System.identityHashCode(actualThrowable));

          return fail(assertionError("[assertThrows] Bad thrown exception type! Expected: "+expected+
              "\n\t^^^ but → "+actual+" = "+line(actualThrown,null)+
              "\t# of("+clsName(actualThrowable)+')'+
              "\n\n@ "+runnable, actualThrown));
        } else {
          return fail(assertionError("[assertThrows] Bad thrown exception type! Expected: "+expected+
              "\n\t^^^ but → "+line(actualThrown,null)+
              "\t# of("+clsName(actualThrowable)+')'+
              "\n\n@ "+runnable, actualThrown));
        }
      }
      return expectedThrowable.cast(actualThrown);
    }
    return fail(assertionError("[assertThrows] No exception was thrown! Expected: "+
        clsName(expectedThrowable)+"\n\n@ "+runnable, null));
  }

  public void run (Runnable code) {
    try {
      code.run();
    } catch (Throwable e) {
      rethrow(e);
    }
  }

  public <T> T call (Callable<T> code) {
    try {
      return code.call();
    } catch (Throwable e) {
      return rethrow(e);
    }
  }

  public void exec (ThrowingAction code) {
    try {
      code.exec();
    } catch (Throwable e) {
      rethrow(e);
    }
  }


  public Runnable runnable (final Runnable code) {
    return new Runnable() {
      @Override public void run(){
        try {
          code.run();
          resume();
        } catch (Throwable e) {
          setFailure(toString(),e,Thread.currentThread(),code,false);
        }
      }
      @Override public String toString(){
        return "Waiter.runnable: "+code;
      }
    };
  }

  public <T> Callable<T> callable (final Callable<? extends T> code) {
    return new Callable<T>() {
      @Override public T call(){
        try {
          T result = code.call();
          resume();
          return result;
        } catch (Throwable e) {
          return setFailure(toString(),e,Thread.currentThread(),code,false);
        }
      }
      @Override public String toString(){
        return "Waiter.callable: "+code;
      }
    };
  }

  public Runnable wrap (final ThrowingAction code) {
    return new Runnable() {
      @Override public void run(){
        try {
          code.exec();
          resume();
        } catch (Throwable e) {
          setFailure(toString(),e,Thread.currentThread(),code,false);
        }
      }
      @Override public String toString(){
        return "Waiter.wrap: "+code;
      }
    };
  }

  public <T> T assertStartsWith (String toStringStartsWith, T actual) {
    if (actual == null)
      return fail(format(toStringStartsWith, actual, "assertStartsWith"));

    if (toStringStartsWith == null || toStringStartsWith.isEmpty())
      return fail("assertStartsWith: Expected toString is empty! "+
        "Actual: "+ line(actual,null)+"\t"+clsName(actual),
          actual instanceof Throwable ? (Throwable) actual : null);

    if (!line(actual,null).startsWith(line(toStringStartsWith,null)))
      return fail(format(toStringStartsWith, actual, "assertStartsWith"),
        actual instanceof Throwable ? (Throwable) actual : null);

    return actual;
  }

  public int getRemainingResumes() { return remainingResumes.get();}

  public ConcurrentLinkedQueue<AssertionError> getFailureQueue() { return failure;}

  public synchronized void reset () {
    failure.clear();
    remainingResumes.set(0);
    circuit.open();
  }

  @Override public String toString(){
    return "[Waiter."+name+": "+circuit+"] Remaining: "+getRemainingResumes()+
        ", Failure: "+failure.size();
  }
}
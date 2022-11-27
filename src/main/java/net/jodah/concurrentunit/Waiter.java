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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static net.jodah.concurrentunit.internal.CUTools.clsName;
import static net.jodah.concurrentunit.internal.CUTools.format;
import static net.jodah.concurrentunit.internal.CUTools.line;
import static net.jodah.concurrentunit.internal.CUTools.sneakyThrow;

/**
 * Waits on a test, carrying out assertions, until being resumed.
 *
 * @author Jonathan Halterman
 */
public class Waiter implements Thread.UncaughtExceptionHandler {
  private static final String TIMEOUT_MESSAGE = "Test timed out while waiting for an expected result, expectedResumes: %d, actualResumes: %d";
  private final AtomicInteger remainingResumes = new AtomicInteger(0);
  private final ReentrantCircuit circuit = new ReentrantCircuit();
  private volatile Throwable failure;

  /**
   * Creates a new Waiter.
   */
  public Waiter() {
    circuit.open();
  }

  /**
   * Asserts that the {@code expected} values equals the {@code actual} value
   *
   * @throws AssertionError when the assertion fails
   */
  public boolean assertEquals(Object expected, Object actual) {
    if (expected == null && actual == null)
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
    await(0, TimeUnit.MILLISECONDS, 1);
  }

  /**
   * Waits until the {@code delay} has elapsed, {@link #resume()} is called, or the test is failed.
   *
   * @param delay Delay to wait in milliseconds
   * @throws TimeoutException if the operation times out while waiting
   * @throws InterruptedException if the operations is interrupted while waiting
   * @throws AssertionError if any assertion fails while waiting
   */
  public void await(long delay) throws TimeoutException, InterruptedException {
    await(delay, TimeUnit.MILLISECONDS, 1);
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
    await(delay, TimeUnit.MILLISECONDS, expectedResumes);
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
  public void await(long delay, TimeUnit timeUnit, int expectedResumes) throws TimeoutException, InterruptedException {
    try {
      if (failure == null) {
        synchronized (this) {
          int remaining = remainingResumes.addAndGet(expectedResumes);
          if (remaining > 0)
            circuit.open();
        }

        if (delay == 0)
          circuit.await();
        else if (!circuit.await(delay, timeUnit)) {
          final int actualResumes = expectedResumes - remainingResumes.get();
          throw new TimeoutException(String.format(TIMEOUT_MESSAGE, expectedResumes, actualResumes));
        }
      }
    } finally {
      remainingResumes.set(0);
      circuit.open();
      if (failure != null) {
        Throwable f = failure;
        failure = null;
        sneakyThrow(f);
      }
    }
  }

  /**
   * Resumes the waiter when the expected number of {@link #resume()} calls have occurred.
   */
  public synchronized void resume() {
    if (remainingResumes.decrementAndGet() <= 0)
      circuit.close();
  }

  /**
   * Fails the current test.
   *
   * @throws AssertionError
   */
  public <V> V fail() throws AssertionError {
    return fail(new AssertionError(Thread.currentThread()));
  }

  /**
   * Fails the current test for the given {@code reason}.
   *
   * @throws AssertionError
   */
  public <V> V fail(String reason) throws AssertionError {
    return fail(new AssertionError(reason));
  }

  /**
   * Fails the current test with the given {@code reason}, sets the number of expected resumes to 0, and throws the
   * {@code reason} as an {@code AssertionError} in the main test thread and in the current thread.
   *
   * @throws AssertionError wrapping the {@code reason}
   */
  public <V> V fail(Throwable reason) throws AssertionError {
    AssertionError ae;
    if (reason instanceof AssertionError)
      ae = (AssertionError) reason;
    else {
      ae = new AssertionError(Thread.currentThread());
      if (reason != null)
        ae.initCause(reason);
    }

    setFailure(ae);
    throw ae;
  }

  protected void setFailure (Throwable newFailure) {
    failure = newFailure;
    circuit.close();
  }

  /**
   * Rethrows the {@code failure} in the main test thread and in the current thread. Differs from
   * {@link #fail(Throwable)} which wraps a failure in an AssertionError before throwing.
   *
   * @throws Throwable the {@code failure}
   */
  public <V> V rethrow(Throwable newFailure) {
    setFailure(newFailure);
    sneakyThrow(newFailure);
    throw new IllegalStateException(newFailure);// make javac happy
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

  public <V> V fail (String message, Throwable cause) throws AssertionError {
    String ct = Thread.currentThread().toString();
    AssertionError ae = new AssertionError(message+ (message.contains(ct) ? "" : "\tThread: "+ct));
    if (cause != null)
      ae.initCause(cause);
    return fail(ae);
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
      throws AssertionError, ClassCastException
  {
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

          return fail("[assertThrows] Bad thrown exception type! Expected: "+expected+
              "\n\t^^^ but → "+actual+" = "+line(actualThrown)+
              "\t# of("+clsName(actualThrowable)+')'+
              "\n\n@ "+runnable, actualThrown);
        } else {
          return fail("[assertThrows] Bad thrown exception type! Expected: "+expected+
              "\n\t^^^ but → "+line(actualThrown)+
              "\t# of("+clsName(actualThrowable)+')'+
              "\n\n@ "+runnable, actualThrown);
        }
      }
      return expectedThrowable.cast(actualThrown);
    }
    return fail("[assertThrows] No exception was thrown! Expected: "+
        clsName(expectedThrowable)+"\n\n@ "+runnable);
  }

  public void run (Runnable code) throws AssertionError {
    try {
      code.run();
    } catch (Throwable e) {
      rethrow(e);
    }
  }

  public <T> T call (Callable<T> code) throws AssertionError {
    try {
      return code.call();
    } catch (Throwable e) {
      return rethrow(e);
    }
  }

  public void exec (ThrowingAction code) throws AssertionError {
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
          rethrow(e);
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
          return rethrow(e);
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
          rethrow(e);
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
        "Actual: "+ line(actual)+"\t"+clsName(actual),
          actual instanceof Throwable ? (Throwable) actual : null);

    if (!line(actual).startsWith(line(toStringStartsWith)))
      return fail(format(toStringStartsWith, actual, "assertStartsWith"),
        actual instanceof Throwable ? (Throwable) actual : null);

    return actual;
  }


  public int getRemainingResumes() {
    return remainingResumes.get();
  }

  public synchronized void reset () {
    failure = null;
    remainingResumes.set(0);
    circuit.open();
  }
}
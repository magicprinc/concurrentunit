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

import net.jodah.concurrentunit.internal.ThrowingAction;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Convenience support class, wrapping a {@link Waiter}.
 *
 * @author Jonathan Halterman
 */
public abstract class ConcurrentTestCase {
  private final Waiter waiter = new Waiter();

  public Waiter withWaiter () {
    return waiter;
  }

  /**
   * @see Waiter#assertEquals(Object, Object)
   */
  public void threadAssertEquals(Object expected, Object actual) {
    waiter.assertEquals(expected, actual);
  }

  /**
   * @see Waiter#assertTrue(boolean)
   */
  public void threadAssertFalse(boolean b) {
    waiter.assertFalse(b);
  }

  /**
   * @see Waiter#assertNotNull(Object)
   */
  public void threadAssertNotNull(Object object) {
    waiter.assertNotNull(object);
  }

  /**
   * @see Waiter#assertNull(Object)
   */
  public void threadAssertNull(Object x) {
    waiter.assertNull(x);
  }

  /**
   * @see Waiter#assertTrue(boolean)
   */
  public void threadAssertTrue(boolean b) {
    waiter.assertTrue(b);
  }

  /**
   * @see Waiter#fail()
   */
  public <V> V threadFail() {
    return threadFail(new AssertionError());
  }

  /**
   * @see Waiter#fail(String)
   */
  public <V> V threadFail(String reason) {
    return threadFail(new AssertionError(reason));
  }

  /**
   * @see Waiter#fail(Throwable)
   */
  public <V> V threadFail(Throwable reason) {
    return waiter.fail(reason);
  }

  /**
   * @see Waiter#rethrow(Throwable)
   */
  public void rethrow(Throwable reason) {
    waiter.rethrow(reason);
  }

  /**
   * @see Waiter#await()
   */
  protected void await() throws TimeoutException, InterruptedException {
    waiter.await();
  }

  /**
   * @see Waiter#await(long)
   */
  protected void await(long delay) throws TimeoutException, InterruptedException {
    waiter.await(delay);
  }

  /**
   * @see Waiter#await(long, int)
   */
  protected void await(long delay, int expectedResumes) throws TimeoutException, InterruptedException {
    waiter.await(delay, expectedResumes);
  }

  /**
   * @see Waiter#await(long, TimeUnit)
   */
  protected void await(long delay, TimeUnit timeUnit) throws TimeoutException, InterruptedException {
    waiter.await(delay, timeUnit);
  }

  /**
   * @see Waiter#await(long, TimeUnit, int)
   */
  protected void await(long delay, TimeUnit timeUnit, int expectedResumes) throws TimeoutException, InterruptedException {
    waiter.await(delay, timeUnit, expectedResumes);
  }

  /**
   * @see Waiter#resume()
   */
  protected void resume() {
    waiter.resume();
  }


  public <V> V threadFail (String message, Throwable cause) throws AssertionError {
    return waiter.fail(message, cause);
  }

  public <T extends Throwable> T assertThrows (Class<T> expectedThrowable, ThrowingAction runnable)
      throws AssertionError, ClassCastException {
    return waiter.assertThrows(expectedThrowable, runnable);
  }

  public void run (Runnable code) throws AssertionError {
    waiter.run(code);
  }

  public <T> T call (Callable<T> code) throws AssertionError {
    return waiter.call(code);
  }

  public void exec (ThrowingAction code) throws AssertionError {
    waiter.exec(code);
  }


  public Runnable runnable (final Runnable code) {
    return waiter.runnable(code);
  }

  public <T> Callable<T> callable (final Callable<? extends T> code) {
    return waiter.callable(code);
  }

  public Runnable wrap (final ThrowingAction code) {
    return waiter.wrap(code);
  }

}
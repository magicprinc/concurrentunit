package net.jodah.concurrentunit.internal;

/**
* FunctionalInterface similar to ThrowingRunnable, CheckedRunnable, etc.
* Only for internal use in Waiter.
 *
* @author a.fink
*/
public interface ThrowingAction {

  void exec () throws Throwable;

}
package net.jodah.concurrentunit.internal;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;

/** Internal tools. Do not use! */
public final class CUTools {

  public static String clsName (Object o) {
    if (o == null)
      return "";
    if (o instanceof String)
      return "";

    if (o instanceof Class) {
      Class<?> klass = (Class<?>) o;

      String cname = klass.getCanonicalName();
      return (cname != null ? cname
          : klass.getName())
          .replace("java.lang.","");
    }
    return '('+ clsName(o.getClass()) +')';
  }

  public static String line(Object o, String defIfNull) {
    if (o == null)
      return defIfNull == null ? "null" : defIfNull;

    if (o instanceof Object[]) {
      return visible(Arrays.deepToString((Object[]) o));

    } else if (o instanceof Throwable) {
      StringBuilder sb = new StringBuilder(o.toString());
      Throwable e = (Throwable) o;
      while (e.getCause()!=null) {
        e = e.getCause();
        sb.append(" <= ").append(e);
      }
      return visible(sb);
    }
    return visible(o);
  }
  private static String visible (Object o) {
    return o.toString().replace("\r", "\\r").replace("\n", "\\n")
        .replace("\t", "\\t").replace("java.lang.", "").trim();
  }


  public static <V> V sneakyThrow(Throwable t) {
    throw (AssertionError) CUTools.<Error>sneakyThrow2(t);
  }

  @SuppressWarnings("unchecked")
  private static <T extends Throwable> T sneakyThrow2(Throwable t) throws T {
    throw (T) t;
  }

  public static String format(Object expected, Object actual, String where) {
    String ecn = clsName(expected);
    String acn = clsName(actual);
    return where+": expected:<" + line(expected,null) + ">"+(ecn.isEmpty() ? "" : ' ' +ecn)+
        ", but was:<" + line(actual,null) + ">"+(acn.isEmpty() ? "" : ' ' +acn);
  }

  /** For tests: one object for three methods. Doesn't call Waiter! */
  public static ThrowingActionImpl of (ThrowingAction action) {
    return new ThrowingActionImpl(action);
  }

  public static class ThrowingActionImpl implements ThrowingAction, Callable<Object>, Runnable {
    private final ThrowingAction actor;

    public ThrowingActionImpl(ThrowingAction actor){
      this.actor = actor;
    }

    @Override public final void run() {
      try {
        exec();
      } catch (Throwable e) {
        sneakyThrow(e);
      }
    }

    @Override public final Object call() throws Exception {
      try {
        exec();
      } catch (Throwable e) {
        sneakyThrow(e);
      }
      return null;
    }

    @Override public void exec() throws Throwable{
      actor.exec();
    }

    @Override public String toString(){
      return "ThrowingAction: "+actor;
    }
  }

  private static boolean contains (AssertionError main, Throwable cause) {
    if (main == cause)
      return true;
    if (cause == null || main == null)
      return false;

    Throwable mcause = main.getCause();

    if (mcause == cause)
      return true;
    if (mcause == cause.getCause())
      return true;
    if (mcause instanceof AssertionError)
      return contains((AssertionError) mcause,cause);
    return false;
  }

  public static boolean contains (Collection<AssertionError> c, Throwable cause) {
    for (AssertionError q : c) {
      if (contains(q, cause)) {
        return true;
      }
    }
    return false;
  }

  public static <T> T assertStartsWith (String toStringStartsWith, T actual) {
    if (actual == null)
      throw assertionError(format(toStringStartsWith, actual, "assertStartsWith"), null);

    if (toStringStartsWith == null || toStringStartsWith.isEmpty())
      throw assertionError("assertStartsWith: Expected toString is empty! "+
              "Actual: "+ line(actual, null)+"\t"+clsName(actual),
          actual instanceof Throwable ? (Throwable) actual : null);

    if (!line(actual, null).startsWith(line(toStringStartsWith, null)))
      throw assertionError(format(toStringStartsWith, actual, "assertStartsWith"),
          actual instanceof Throwable ? (Throwable) actual : null);

    return actual;
  }

  public static AssertionError assertionError(CharSequence message, Throwable cause) {
    AssertionError ae = new AssertionError(message);
    if (cause != null)
      ae.initCause(cause);
    return ae;
  }


  public static <T extends Throwable> T assertThrows (Class<T> expectedThrowable, ThrowingAction runnable)
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

          throw assertionError("[assertThrows] Bad thrown exception type! Expected: "+expected+
              "\n\t^^^ but → "+actual+" = "+line(actualThrown, null)+
              "\t# of("+clsName(actualThrowable)+')'+
              "\n\n@ "+runnable, actualThrown);
        } else {
          throw assertionError("[assertThrows] Bad thrown exception type! Expected: "+expected+
              "\n\t^^^ but → "+line(actualThrown, null)+
              "\t# of("+clsName(actualThrowable)+')'+
              "\n\n@ "+runnable, actualThrown);
        }
      }
      return expectedThrowable.cast(actualThrown);
    }
    throw assertionError("[assertThrows] No exception was thrown! Expected: "+
        clsName(expectedThrowable)+"\n\n@ "+runnable, null);
  }


  @SuppressWarnings("unchecked")
  public static <T,R> R eiter (Callable<T> execute) {
    try {
      return (R) execute.call();
    } catch (Throwable e) {
      return (R) e;
    }
  }
}
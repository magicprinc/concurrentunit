package net.jodah.concurrentunit.internal;

import java.util.Arrays;
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

  public static String line(Object o) {
    if (o == null)
      return "null";

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


  public static void sneakyThrow(Throwable t) {
    CUTools.<Error>sneakyThrow2(t);
  }

  @SuppressWarnings("unchecked")
  private static <T extends Throwable> void sneakyThrow2(Throwable t) throws T {
    throw (T) t;
  }

  public static String format(Object expected, Object actual, String where) {
    String ecn = clsName(expected);
    String acn = clsName(actual);
    return where+": expected:<" + line(expected) + ">"+(ecn.isEmpty() ? "" : ' ' +ecn)+
        ", but was:<" + line(actual) + ">"+(acn.isEmpty() ? "" : ' ' +acn);
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

    public ThrowingActionImpl(){
      actor = null;
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
}
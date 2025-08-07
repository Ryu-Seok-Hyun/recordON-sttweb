// 1) src/main/java/com/sttweb/sttweb/context/BranchContext.java
package com.sttweb.sttweb.context;

public class BranchContext {
  private static final ThreadLocal<Integer> ctx = new ThreadLocal<>();
  public static void set(Integer id)   { ctx.set(id); }
  public static Integer get()          { return ctx.get(); }
  public static void clear()           { ctx.remove(); }
}

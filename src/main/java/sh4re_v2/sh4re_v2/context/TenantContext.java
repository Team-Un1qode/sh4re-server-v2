package sh4re_v2.sh4re_v2.context;

public class TenantContext {
  private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

  public static void setTenantId(String tenantId) {
    CONTEXT.set(tenantId);
  }

  public static String getTenantId() {
    return CONTEXT.get();
  }

  public static void clear() {
    CONTEXT.remove();
  }
}

import com.futurecode.autovideocutter.ClipPlan;

public class ClipPlanSelfTest {
  public static void main(String[] args) {
    check(ClipPlan.count(2 * 60 * 60 * 1000L, 5 * 60 * 1000L) == 24, "2 hours / 5 min");
    check(ClipPlan.count(61_000, 60_000) == 2, "one remainder");
    check(ClipPlan.count(60_000, 60_000) == 1, "exact division");
    check(ClipPlan.count(1, 60_000) == 1, "small video");
    long duration = 7_201_300;
    long interval = 300_000;
    long lastEnd = 0;
    for (int i = 0; i < ClipPlan.count(duration, interval); i++) {
      long[] b = ClipPlan.bounds(duration, interval, i);
      check(b[0] == lastEnd, "gap or overlap at " + i);
      check(b[1] > b[0], "zero length");
      check(b[1] - b[0] <= interval, "segment too long");
      lastEnd = b[1];
    }
    check(lastEnd == duration, "last second lost");
    try { ClipPlan.count(0, 10); throw new RuntimeException("accepted zero duration"); }
    catch (IllegalArgumentException expected) {}
    System.out.println("PASS: splitting math (2h/5m = 24; no gaps or overlaps; final remainder)");
  }
  static void check(boolean b, String s) { if (!b) throw new AssertionError(s); }
}

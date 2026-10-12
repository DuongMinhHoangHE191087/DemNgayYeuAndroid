import shark.*;
import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;
import java.io.File;
import java.util.*;
import kotlin.sequences.SequencesKt;

/**
 * G9 (DOC/65): shark HeapAnalyzer trên một hprof Android — liệt kê Activity, đường tham chiếu mạnh ngắn nhất tới mỗi
 * Activity đã huỷ (mDestroyed/mFinished) với AndroidReferenceMatchers.appDefaults (bỏ weak/soft).
 * Dùng: java G9 <file.hprof> [lớp Activity, mặc định android.app.Activity] [appDefaults|ignoredOnly|none]
 */
public class G9 {
  static String activityClass = "android.app.Activity";

  static boolean flag(HeapInstance inst, String name) {
    HeapField f = inst.get("android.app.Activity", name);
    return f != null && Boolean.TRUE.equals(f.getValue().getAsBoolean());
  }

  static boolean isDeadActivity(HeapObject obj) {
    if (!(obj instanceof HeapInstance)) return false;
    HeapInstance inst = (HeapInstance) obj;
    return inst.instanceOf(activityClass) && (flag(inst, "mDestroyed") || flag(inst, "mFinished"));
  }

  static CloseableHeapGraph open(File hprof) {
    return HprofHeapGraph.Companion.openHeapGraph(hprof, null, EnumSet.allOf(HprofRecordTag.class));
  }

  public static void main(String[] args) throws Exception {
    File hprof = new File(args[0]);
    if (args.length > 1) activityClass = args[1];
    String mode = args.length > 2 ? args[2] : "appDefaults";
    long t0 = System.currentTimeMillis();
    try (CloseableHeapGraph graph = open(hprof)) {
      System.out.println("== Graph: objects=" + graph.getObjectCount() + " instances=" + graph.getInstanceCount()
          + " gcRoots=" + graph.getGcRoots().size());
      int n = 0, dead = 0;
      for (HeapInstance inst : SequencesKt.toList(graph.getInstances())) {
        if (!inst.instanceOf("android.app.Activity")) continue;
        n++;
        if (isDeadActivity(inst)) dead++;
        System.out.println("ACTIVITY " + inst.getInstanceClassName() + " id=0x" + Long.toHexString(inst.getObjectId())
            + " mDestroyed=" + flag(inst, "mDestroyed") + " mFinished=" + flag(inst, "mFinished")
            + " mResumed=" + flag(inst, "mResumed"));
      }
      System.out.println("== Activity instances: " + n + ", dead " + activityClass + ": " + dead);
    }
    List<ReferenceMatcher> matchers = mode.equals("none") ? Collections.emptyList()
        : mode.equals("ignoredOnly") ? AndroidReferenceMatchers.Companion.getIgnoredReferencesOnly()
        : AndroidReferenceMatchers.Companion.getAppDefaults();
    System.out.println("== Matchers mode=" + mode + " count=" + matchers.size());
    HeapAnalyzer analyzer = new HeapAnalyzer(step -> System.err.println("step: " + step.name()));
    FilteringLeakingObjectFinder finder = new FilteringLeakingObjectFinder(Collections.singletonList(G9::isDeadActivity));
    HeapAnalysis analysis;
    try (CloseableHeapGraph graph = open(hprof)) {
      // Workaround 1 (README): dump API 37 có thể thiếu android.os.Build → AndroidBuildMirror ném NPE; dựng tay.
      HeapClass build = graph.findClassByName("android.os.Build");
      System.out.println("== android.os.Build present=" + (build != null));
      if (build == null) graph.getContext().set(AndroidBuildMirror.class.getName(), new AndroidBuildMirror("Google", 37, "emulator"));
      analysis = analyzer.analyze(hprof, graph, finder, matchers, true,
          AndroidObjectInspectors.Companion.getAppDefaults(), MetadataExtractor.Companion.getNO_OP());
    }
    if (analysis instanceof HeapAnalysisFailure) {
      System.out.println("FAILURE");
      ((HeapAnalysisFailure) analysis).getException().printStackTrace(System.out);
      return;
    }
    HeapAnalysisSuccess s = (HeapAnalysisSuccess) analysis;
    System.out.println("== applicationLeaks=" + s.getApplicationLeaks().size() + " libraryLeaks=" + s.getLibraryLeaks().size()
        + " unreachable=" + s.getUnreachableObjects().size());
    int i = 0;
    for (Leak leak : SequencesKt.toList(s.getAllLeaks())) {
      for (LeakTrace lt : leak.getLeakTraces()) {
        i++;
        System.out.println("\n#################### TRACE " + i + " kind=" + leak.getClass().getSimpleName()
            + (leak instanceof LibraryLeak ? " pattern=" + ((LibraryLeak) leak).getPattern() : ""));
        System.out.println("gcRoot=" + lt.getGcRootType() + " retained=" + lt.getRetainedHeapByteSize() + " bytes / "
            + lt.getRetainedObjectCount() + " objects");
        System.out.println(lt.toSimplePathString());
      }
    }
    for (LeakTraceObject o : s.getUnreachableObjects()) System.out.println("UNREACHABLE: " + o);
    System.out.println("== done in " + (System.currentTimeMillis() - t0) + " ms");
  }
}

import shark.*;
import shark.HeapObject.*;
import java.io.File;
import java.util.*;
import kotlin.sequences.SequencesKt;

/**
 * G9: chỉ mục tham chiếu mạnh riêng (bỏ Reference.referent) để phân loại đường giữ Activity đã huỷ và thí nghiệm cắt.
 * Mỗi Activity chết: danh tính (hướng màn hình, chữ TextView của lớp Activity), đường ngắn nhất gốc→Activity với loại
 * từng hop, người giữ trực tiếp, và "reachable?" dưới từng thí nghiệm. Phần WEBVIEWS: mỗi WebView, Activity trong
 * context wrapper của nó và đối tượng định dạng GMA gần nhất giữ nó (AdView/InterstitialAd/AppOpenAd/…).
 * Dùng: java G9Roots <file.hprof> <lớp Activity> [--exp tên=token,token]...
 * token: sdk | demo | gma | jni (bỏ gốc JniGlobal) | cut:<Lớp.field> (bỏ cạnh theo tên, khớp đuôi)
 *        | drop:<lớp> (bỏ mọi đối tượng instanceOf lớp) | dropweb:<kind> (bỏ WebView có chủ gần nhất là kind)
 */
public class G9Roots {
  static final String[][] KINDS = {
    {"AdView", "com.google.android.gms.ads.BaseAdView"},
    {"InterstitialAd", "com.google.android.gms.ads.interstitial.InterstitialAd"},
    {"AppOpenAd", "com.google.android.gms.ads.appopen.AppOpenAd"},
    {"RewardedAd", "com.google.android.gms.ads.rewarded.RewardedAd"},
    {"NativeAd", "com.google.android.gms.ads.nativead.NativeAd"},
    {"NativeAdView", "com.google.android.gms.ads.nativead.NativeAdView"},
  };
  static int[] eSrc = new int[1 << 20], eDst = new int[1 << 20]; static String[] eName = new String[1 << 20];
  static int eCount, n; static int[] fStart, fAdj, rStart, rAdj;
  static String[] cls; static String[] kind; static boolean[] isWeb, isRoot, isJniOnly;
  static long[] oid;

  static void addEdge(int s, int d, String name) {
    if (eCount == eSrc.length) { int m = eSrc.length * 2; eSrc = Arrays.copyOf(eSrc, m); eDst = Arrays.copyOf(eDst, m); eName = Arrays.copyOf(eName, m); }
    eSrc[eCount] = s; eDst[eCount] = d; eName[eCount++] = name;
  }
  static String cat(int i) {
    String c = cls[i]; if (c == null) return "?"; if (c.startsWith("class ")) c = c.substring(6);
    if (c.startsWith("com.app.plugin.demo.")) return "DEMO";
    if (c.startsWith("com.app.plugin.")) return "SDK";
    if (c.startsWith("com.google.android.gms.ads.") || c.startsWith("com.google.android.gms.internal.ads.") || c.startsWith("com.google.android.gms.dynamic.")) return "GMA";
    if (c.startsWith("org.chromium.") || c.startsWith("android.webkit.") || c.startsWith("com.android.webview.")) return "WEBVIEW";
    if (c.startsWith("android.") || c.startsWith("com.android.") || c.startsWith("java.") || c.startsWith("dalvik.") || c.startsWith("libcore.") || c.startsWith("sun.")) return "FRAMEWORK";
    if (c.startsWith("androidx.") || c.startsWith("kotlin")) return "ANDROIDX/KOTLIN";
    return "OTHER";
  }
  static String d(int i) { return cls[i] + "@" + Long.toHexString(oid[i]) + (kind[i] != null ? " <" + kind[i] + ">" : "") + (isWeb[i] ? " <WebView>" : ""); }

  /** BFS từ gốc; blockedSrc: không đi cạnh ra; dropped: không tới được; cutEdge: cạnh bị bỏ. parent (nullable) = cạnh tới. */
  static boolean[] reach(boolean[] blockedSrc, boolean[] dropped, boolean[] cutEdge, boolean noJni, int[] parent) {
    boolean[] r = new boolean[n]; ArrayDeque<Integer> q = new ArrayDeque<>();
    for (int i = 0; i < n; i++) if (isRoot[i] && !(noJni && isJniOnly[i]) && !dropped[i]) { r[i] = true; q.add(i); }
    while (!q.isEmpty()) { int x = q.poll(); if (blockedSrc[x]) continue;
      for (int k = fStart[x]; k < fStart[x + 1]; k++) { int e = fAdj[k]; int y = eDst[e];
        if (r[y] || dropped[y] || cutEdge[e]) continue; r[y] = true; if (parent != null) parent[y] = e; q.add(y); } }
    return r;
  }

  /** Chủ định dạng GMA gần nhất của nút (BFS ngược, chỉ nút reachable). */
  static String owner(int w, boolean[] live, int maxHops) {
    int[] bp = new int[n]; Arrays.fill(bp, -1); int[] dist = new int[n]; boolean[] seen = new boolean[n];
    ArrayDeque<Integer> q = new ArrayDeque<>(); q.add(w); seen[w] = true; List<String> hits = new ArrayList<>(); int best = -1;
    while (!q.isEmpty()) { int x = q.poll(); if (best >= 0 && dist[x] > best) break;
      if (x != w && kind[x] != null) { best = dist[x];
        StringBuilder sb = new StringBuilder(kind[x] + " d=" + dist[x] + ": " + d(x)); int cur = x;
        while (cur != w) { int e = bp[cur]; sb.append(" --").append(shortName(eName[e])).append("--> ").append(cls[eDst[e]]); cur = eDst[e]; }
        hits.add(sb.toString()); continue; }
      if (dist[x] >= maxHops) continue;
      for (int k = rStart[x]; k < rStart[x + 1]; k++) { int e = rAdj[k]; int y = eSrc[e]; if (seen[y] || !live[y]) continue;
        seen[y] = true; bp[y] = e; dist[y] = dist[x] + 1; q.add(y); } }
    return hits.isEmpty() ? "none within " + maxHops + " hops" : String.join("\n        | ", hits);
  }
  static List<Integer> near(int a, boolean[] live, int hops) {
    List<Integer> out = new ArrayList<>(); int[] dist = new int[n]; boolean[] seen = new boolean[n]; ArrayDeque<Integer> q = new ArrayDeque<>(); q.add(a); seen[a] = true;
    while (!q.isEmpty()) { int x = q.poll(); out.add(x); if (dist[x] >= hops) continue;
      for (int k = rStart[x]; k < rStart[x + 1]; k++) { int y = eSrc[rAdj[k]]; if (seen[y] || !live[y] || cls[y].startsWith("class ")) continue; seen[y] = true; dist[y] = dist[x] + 1; q.add(y); } }
    return out;
  }
  static int nearestSdk(int a, boolean[] live, int hops) {
    for (int x : near(a, live, hops)) if (x != a && cat(x).equals("SDK") && !cls[x].startsWith("class ")) return x; return -1;
  }
  static String strings(HeapGraph g, int x) {
    HeapObject o = g.findObjectByIdOrNull(oid[x]); if (!(o instanceof HeapInstance)) return ""; StringBuilder sb = new StringBuilder();
    for (HeapField f : SequencesKt.toList(((HeapInstance) o).readFields())) { HeapObject v = f.getValue().getAsObject();
      if (v instanceof HeapInstance && ((HeapInstance) v).getInstanceClassName().equals("java.lang.String")) sb.append(" ").append(f.getName()).append("=\"").append(((HeapInstance) v).readAsJavaString()).append("\""); }
    return sb.toString();
  }
  static boolean viewTree(String c) { return c != null && (c.startsWith("android.view.") || c.startsWith("android.widget.") || c.startsWith("com.android.internal.policy.")); }
  static String ownerKind(String o) { return o.startsWith("none") ? "none" : o.substring(0, o.indexOf(' ')); }
  static String shortName(String s) { return s.replace("com.google.android.gms.internal.ads.", "").replace("com.app.plugin.", ""); }

  static String text(HeapObject v) {
    if (!(v instanceof HeapInstance)) return null; HeapInstance i = (HeapInstance) v;
    if (i.getInstanceClassName().equals("java.lang.String")) return i.readAsJavaString();
    HeapField f = i.get("android.text.SpannableStringInternal", "mText");
    return f == null ? i.getInstanceClassName() : text(f.getValue().getAsObject());
  }

  public static void main(String[] args) throws Exception {
    File hprof = new File(args[0]); String actClass = args.length > 1 ? args[1] : "android.app.Activity";
    LinkedHashMap<String, String> exps = new LinkedHashMap<>();
    for (String e : new String[]{"sdk=sdk", "demo=demo", "sdk+demo=sdk,demo", "gma=gma", "sdk+demo+nojni=sdk,demo,jni",
        "dropweb:AdView=dropweb:AdView", "dropweb:fullscreen=dropweb:InterstitialAd,dropweb:AppOpenAd,dropweb:RewardedAd"}) exps.put(e.split("=")[0], e.split("=")[1]);
    for (int a = 2; a + 1 < args.length; a += 2) if (args[a].equals("--exp")) exps.put(args[a + 1].split("=")[0], args[a + 1].split("=")[1]);
    long t0 = System.currentTimeMillis();
    try (CloseableHeapGraph g = HprofHeapGraph.Companion.openHeapGraph(hprof, null, EnumSet.allOf(HprofRecordTag.class))) {
      n = g.getObjectCount() + 1; cls = new String[n]; kind = new String[n]; isWeb = new boolean[n]; isRoot = new boolean[n]; isJniOnly = new boolean[n]; oid = new long[n];
      HashMap<String, String> intern = new HashMap<>(); List<Integer> dead = new ArrayList<>(); List<Integer> webs = new ArrayList<>();
      HashMap<Long, Integer> idx = new HashMap<>();
      // Workaround 3 (README): chỉ số lấy từ lần duyệt (không findObjectByIndex); tên lớp cache tại đây (workaround 2).
      for (HeapObject o : SequencesKt.toList(g.getObjects())) idx.put(o.getObjectId(), o.getObjectIndex());
      for (HeapObject o : SequencesKt.toList(g.getObjects())) {
        int s = o.getObjectIndex(); oid[s] = o.getObjectId();
        if (o instanceof HeapInstance) {
          HeapInstance inst = (HeapInstance) o; cls[s] = inst.getInstanceClassName();
          for (String[] k : KINDS) if (inst.instanceOf(k[1])) kind[s] = k[0];
          if (inst.instanceOf("android.webkit.WebView")) { isWeb[s] = true; webs.add(s); }
          if (inst.instanceOf(actClass)) { HeapField f = inst.get("android.app.Activity", "mDestroyed");
            if (f != null && Boolean.TRUE.equals(f.getValue().getAsBoolean())) dead.add(s); }
          boolean isRef = inst.instanceOf("java.lang.ref.Reference");
          for (HeapField f : SequencesKt.toList(inst.readFields())) {
            if (isRef && (f.getName().equals("referent") || f.getName().equals("zombie"))) continue;
            if (f.getName().startsWith("shadow$")) continue;
            Long id = f.getValue().getAsNonNullObjectId(); Integer t = id == null ? null : idx.get(id); if (t == null) continue;
            String nm = f.getDeclaringClass().getName() + "." + f.getName(); addEdge(s, t, intern.computeIfAbsent(nm, x -> x)); }
        } else if (o instanceof HeapClass) {
          HeapClass c = (HeapClass) o; cls[s] = "class " + c.getName(); isRoot[s] = true;
          for (HeapField f : SequencesKt.toList(c.readStaticFields())) { if (f.getName().startsWith("$class$")) continue;
            Long id = f.getValue().getAsNonNullObjectId(); Integer t = id == null ? null : idx.get(id); if (t == null) continue;
            addEdge(s, t, intern.computeIfAbsent("static " + c.getName() + "." + f.getName(), x -> x)); }
        } else if (o instanceof HeapObjectArray) {
          HeapObjectArray arr = (HeapObjectArray) o; cls[s] = arr.getArrayClassName(); long[] ids = arr.readRecord().getElementIds();
          for (int i = 0; i < ids.length; i++) { Integer t = ids[i] == 0 ? null : idx.get(ids[i]); if (t != null) addEdge(s, t, intern.computeIfAbsent("[" + i + "]", x -> x)); }
        } else cls[s] = "prim[]";
      }
      Map<Integer, List<String>> rootTypes = new HashMap<>();
      for (GcRoot r : g.getGcRoots()) { Integer t = idx.get(r.getId()); if (t == null) continue;
        rootTypes.computeIfAbsent(t, x -> new ArrayList<>()).add(r.getClass().getSimpleName()); }
      for (Map.Entry<Integer, List<String>> e : rootTypes.entrySet()) { isRoot[e.getKey()] = true;
        isJniOnly[e.getKey()] = !cls[e.getKey()].startsWith("class ") && e.getValue().stream().allMatch("JniGlobal"::equals); }
      fStart = new int[n + 1]; rStart = new int[n + 1];
      for (int i = 0; i < eCount; i++) { fStart[eSrc[i] + 1]++; rStart[eDst[i] + 1]++; }
      for (int i = 0; i < n; i++) { fStart[i + 1] += fStart[i]; rStart[i + 1] += rStart[i]; }
      fAdj = new int[eCount]; rAdj = new int[eCount]; int[] fp = fStart.clone(), rp = rStart.clone();
      for (int i = 0; i < eCount; i++) { fAdj[fp[eSrc[i]]++] = i; rAdj[rp[eDst[i]]++] = i; }
      System.out.println("== objects=" + n + " strongEdges=" + eCount + " dead " + actClass + "=" + dead.size() + " webviews=" + webs.size());

      boolean[] none = new boolean[n]; boolean[] noCut = new boolean[eCount];
      int[] parent = new int[n]; Arrays.fill(parent, -1);
      boolean[] live = reach(none, none, noCut, false, parent);

      System.out.println("\n== WEBVIEWS (context → Activity, chủ GMA gần nhất)");
      Map<Integer, String> webOwner = new HashMap<>();
      for (int w : webs) { if (!live[w]) continue; String o = owner(w, live, 12); webOwner.put(w, ownerKind(o));
        StringBuilder ctx = new StringBuilder();
        for (int k = fStart[w]; k < fStart[w + 1]; k++) { int e = fAdj[k]; if (!eName[e].equals("android.view.View.mContext")) continue; int c = eDst[e];
          ctx.append(cls[c]); for (int k2 = fStart[c]; k2 < fStart[c + 1]; k2++) { int y = eDst[fAdj[k2]]; if (cls[y] != null && cls[y].equals(actClass)) ctx.append(" .").append(shortName(eName[fAdj[k2]])).append("=").append(d(y)).append(dead.contains(y) ? "(DEAD)" : "(live)"); } }
        System.out.println("  " + d(w) + "\n     mContext " + ctx + "\n     owner " + o); }

      String[] names = exps.keySet().toArray(new String[0]); boolean[][] reachExp = new boolean[names.length][];
      for (int x = 0; x < names.length; x++) {
        boolean[] blocked = new boolean[n], dropped = new boolean[n], cut = new boolean[eCount]; boolean noJni = false;
        for (String tok : exps.get(names[x]).split(",")) {
          if (tok.equals("jni")) noJni = true;
          else if (tok.equals("sdk") || tok.equals("demo") || tok.equals("gma")) { String want = tok.toUpperCase();
            for (int i = 0; i < n; i++) if (cat(i).equals(want) && !dead.contains(i) && !(cls[i] != null && cls[i].equals(actClass))) blocked[i] = true; }
          else if (tok.startsWith("cut:")) { String f = tok.substring(4); for (int e = 0; e < eCount; e++) if (eName[e].endsWith(f)) cut[e] = true; }
          else if (tok.startsWith("drop:")) { String c = tok.substring(5); for (int i = 0; i < n; i++) if (c.equals(cls[i])) dropped[i] = true; }
          else if (tok.startsWith("dropweb:")) { String k = tok.substring(8); for (Map.Entry<Integer, String> e : webOwner.entrySet()) if (e.getValue().equals(k)) dropped[e.getKey()] = true; }
        }
        reachExp[x] = reach(blocked, dropped, cut, noJni, null);
      }

      for (int a : dead) {
        HeapInstance act = (HeapInstance) g.findObjectById(oid[a]); StringBuilder id = new StringBuilder();
        HeapField cfg = act.get("android.app.Activity", "mCurrentConfig");
        if (cfg != null && cfg.getValue().getAsObject() instanceof HeapInstance) { HeapField or = ((HeapInstance) cfg.getValue().getAsObject()).get("android.content.res.Configuration", "orientation");
          id.append(" orientation=").append(or == null ? "?" : (or.getValue().getAsInt() == 2 ? "landscape" : "portrait")); }
        for (HeapField f : SequencesKt.toList(act.readFields())) { HeapObject v = f.getValue().getAsObject();
          if (f.getDeclaringClass().getName().equals(actClass) && v instanceof HeapInstance && ((HeapInstance) v).instanceOf("android.widget.TextView")) {
            HeapField t = ((HeapInstance) v).get("android.widget.TextView", "mText"); id.append(" ").append(f.getName()).append("=\"").append(t == null ? "?" : text(t.getValue().getAsObject())).append("\""); } }
        System.out.println("\n==================== DEAD " + d(a) + id);
        StringBuilder path = new StringBuilder(); List<String> hops = new ArrayList<>(); int cur = a; Set<String> cats = new LinkedHashSet<>(); List<String> sdkHops = new ArrayList<>(); String viaWeb = null;
        while (parent[cur] != -1) { int e = parent[cur]; int s = eSrc[e]; hops.add(0, "  " + String.format("%-15s", cat(s)) + d(s) + ((cat(s).equals("SDK") || cat(s).equals("DEMO")) && !cls[s].startsWith("class ") ? strings(g, s) : "") + "\n      --" + eName[e] + "-->");
          if (cat(s).equals("SDK")) sdkHops.add(0, shortName(eName[e])); if (isWeb[s] && viaWeb == null) viaWeb = webOwner.get(s); cats.add(cat(s)); cur = s; }
        System.out.println("-- shortest strong path from ROOT" + rootTypes.getOrDefault(cur, List.of("CLASS")) + ":"); for (String h : hops) System.out.println(h); System.out.println("  → " + d(a));
        String klass = !sdkHops.isEmpty() ? "A (SDK: " + String.join(" → ", sdkHops) + ")"
            : "AdView".equals(viaWeb) || hops.stream().anyMatch(h -> h.contains("<AdView>")) ? "B (rect/banner AdView)"
            : cats.contains("GMA") ? "C (GMA, WebView owner=" + viaWeb + ")" : cats.contains("DEMO") ? "D (demo)" : "FRAMEWORK/OTHER";
        System.out.println("-- CLASS(shortest path) = " + klass);
        System.out.print("-- direct referrers:"); for (int k = rStart[a]; k < rStart[a + 1]; k++) { int e = rAdj[k]; int s = eSrc[e]; if (live[s] && !viewTree(cls[s]))
          System.out.print("\n     " + String.format("%-15s", cat(s)) + d(s) + " ." + shortName(eName[e])); } System.out.println();
        System.out.println("-- GMA ad objects that reach it (reverse ≤ 6 hops) → String fields, nearest SDK holder:");
        for (int x : near(a, live, 6)) { if (kind[x] == null) continue; System.out.println("     " + d(x) + strings(g, x)); int h = nearestSdk(x, live, 8);
          System.out.println("        SDK holder: " + (h < 0 ? "none within 8 hops (GMA-internal only)" : d(h) + strings(g, h))); }
        StringBuilder r = new StringBuilder("-- reachable: all=" + live[a]); for (int x = 0; x < names.length; x++) r.append("  [").append(names[x]).append("]=").append(reachExp[x][a]);
        System.out.println(r);
      }
    }
    System.out.println("\n== done " + (System.currentTimeMillis() - t0) + " ms");
  }
}

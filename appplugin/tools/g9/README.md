# tools/g9 — phân tích heap G9 (DOC/65) bằng shark 2.14

`bash tools/g9_heap.sh <file.hprof> [lớp Activity] [--exp tên=token,...]...` biên dịch `G9.java` + `G9Roots.java` bằng
`javac` của JBR (`JAVA_HOME`, mặc định JBR của Android Studio) vào `$TMPDIR/g9-classes` rồi chạy cả hai:

- **G9**: `HeapAnalyzer` của shark, `AndroidReferenceMatchers.appDefaults` (bỏ weak/soft), đường ngắn nhất từ gốc GC
  tới mỗi Activity `mDestroyed`/`mFinished` của lớp đã cho.
- **G9Roots**: chỉ mục tham chiếu mạnh riêng (field, static, phần tử mảng; bỏ `Reference.referent`; mọi lớp là gốc).
  In bảng WebView (Activity trong context wrapper, định dạng GMA gần nhất giữ nó); với mỗi Activity chết: hướng màn
  hình + chữ các TextView của lớp Activity (nhận diện), đường ngắn nhất với loại từng hop và phân loại, người giữ trực
  tiếp, quảng cáo GMA giữ nó (chuỗi field, `AdsEcmp.adsId` của SDK giữ quảng cáo đó), và `reachable` dưới mỗi thí
  nghiệm cắt. Mặc định: `sdk`, `demo`, `sdk+demo`, `gma`, `sdk+demo+nojni`, `dropweb:AdView`, `dropweb:fullscreen`.
- Token `--exp`: `sdk|demo|gma` (không đi cạnh ra của đối tượng thuộc nhóm), `jni` (bỏ gốc chỉ-JniGlobal),
  `cut:<Lớp.field>` (bỏ cạnh, khớp đuôi tên, vd `cut:ads.AdsPlacementConfig.cbLoad`), `drop:<lớp>` (bỏ mọi đối tượng
  đúng lớp), `dropweb:<kind>` (bỏ WebView có chủ gần nhất là `AdView|InterstitialAd|AppOpenAd|RewardedAd|NativeAd`).
- Phân loại theo đường ngắn nhất: **A** có hop `com.app.plugin.*` (ghi `Lớp.field`), **B** qua `AdView`
  (banner/rect), **C** GMA còn lại (toàn màn hình/OMID, #18), **D** chỉ demo. Một Activity có thể bị giữ bởi nhiều
  đường: kết luận theo thí nghiệm cắt (vd `[gma]=true` + `[gma+cbLoad]=false` = GMA **và** `cbLoad` cùng giữ).

Classpath (Gradle cache `~/.gradle/caches/modules-2/files-2.1`, không tải mạng): `shark`, `shark-graph`,
`shark-hprof`, `shark-android`, `shark-log` 2.14 + `okio-jvm` 3.9.1 + `kotlin-stdlib` 2.x. Script báo thiếu jar nào.

Lấy dump: bản **debug** (`am dumpheap -g` từ chối bản không debuggable), `adb shell am dumpheap -g <pid>
/data/local/tmp/x.hprof`, chờ ≥ 10 s rồi dump lần hai (GC chắc), `adb pull`, xoá file trên máy. Hprof không commit
(`.gitignore` có `*.hprof`).

Ba workaround cho dump emulator API 37 (1.4.0, `.superpowers/sdd/g9-heap-analysis.md` "Tooling notes"):

1. `AndroidBuildMirror`: dump không có `android.os.Build` → `HeapAnalyzer` ném NPE với `appDefaults`. `G9` tự mở
   graph, đặt `graph.context[AndroidBuildMirror] = AndroidBuildMirror("Google", 37, "emulator")` khi thiếu lớp và
   dùng overload `analyze(File, HeapGraph, …)`. (Dump 2026-09-28 có lớp `Build`; nhánh này để dump khác.)
2. `HeapGraph.findClassByName` trả null cho mọi lớp ở dump 1.4.0: tên lớp lấy khi duyệt `graph.objects`
   (`getInstanceClassName`, `HeapClass.getName`), `instanceOf(String)` theo cây lớp — không tra theo tên.
3. `objectIndex` không dùng với `findObjectByIndex` (khác không gian chỉ số): `G9Roots` dựng map `objectId → index`
   từ chính lần duyệt, dùng index làm chỉ số mảng, tra ngược bằng `findObjectById`.

Chạy ~15 s cho dump 60 MB (570k đối tượng, 840k cạnh); `-Xmx6g`.

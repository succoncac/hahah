# PROJECT_NOTES.md

## Mục đích
AppForge là ứng dụng Android quản lý ứng dụng đặc quyền (qua Shizuku, Root `su`, hoặc ADB TCP `127.0.0.1:5555`) cho phép gỡ bỏ hoàn toàn ứng dụng (kể cả app hệ thống), khôi phục lại, ẩn/hiện ứng dụng (trừ Cài đặt), cấp quyền AppOps/Runtime và ép ứng dụng bên thứ 3 làm mặc định hệ thống.

## Công nghệ
- Kotlin + Jetpack Compose + Material Design 3 (Edge-to-Edge, Adaptive `NavigationBar`/`NavigationRail`)
- Shizuku SDK `13.1.5` (`dev.rikka.shizuku:api`, `provider`) + AIDL `IShellService` UserService
- Root `su -c` executor & Pure-Kotlin ADB over TCP 5555 (`AdbTcpClient`)
- Room (`AppDatabase`, `RemovedAppEntity`) lưu lịch sử gỡ & đường dẫn sao lưu APK
- DataStore Preferences (`UserPreferencesRepository`) lưu chế độ quyền, tự sao lưu APK, giao diện
- Font cục bộ (`Be Vietnam Pro`, `Space Grotesk`, `JetBrains Mono`) qua `res/font/`

## Cấu trúc file & vai trò
- `app/src/main/aidl/com/example/IShellService.aidl`: Giao diện AIDL cho Shizuku UserService (thực thi shell & stream cài đặt APK qua `ParcelFileDescriptor`).
- `app/src/main/java/com/example/privilege/ShellUserService.kt`: Dịch vụ chạy trong tiến trình đặc quyền của Shizuku.
- `app/src/main/java/com/example/privilege/AdbTcpClient.kt`: Client ADB TCP `127.0.0.1:5555` hỗ trợ xác thực khóa RSA.
- `app/src/main/java/com/example/privilege/PrivilegeManager.kt`: Lớp hợp nhất 3 chế độ quyền (`SHIZUKU`, `ROOT`, `ADB_TCP`) và ghi nhật ký lệnh.
- `app/src/main/java/com/example/data/AppDatabase.kt`, `UserPreferencesRepository.kt`: Lưu trữ cục bộ bằng Room & DataStore.
- `app/src/main/java/com/example/manager/AppInfoModels.kt`, `AppRepository.kt`: Quét gói (`MATCH_UNINSTALLED_PACKAGES`), sao lưu APK, gỡ bỏ hoàn toàn (`pm uninstall --user 0`), khôi phục (`cmd package install-existing` / `pm install`), ẩn/hiện (`pm disable-user` / `pm enable`), ép mặc định (`cmd role`, `set-home-activity`, `ime`, `set-app-links`), cấp quyền (`pm grant`, `appops`).
- `app/src/main/java/com/example/ui/MainViewModel.kt`: Quản lý state, bộ lọc, tìm kiếm, chọn nhiều và thực thi hàng loạt.
- `app/src/main/java/com/example/ui/screens/*.kt`, `MainActivity.kt`: Giao diện 4 tab (Ứng dụng, Khôi phục, Mặc định, Quyền) và `AppDetailBottomSheet`.
- `.github/workflows/build-apk.yml`: GitHub Actions tự động giải mã `debug.keystore.base64`, chạy `gradle assembleDebug` (Gradle 9.3.1, JDK 21) và phát hành file `.apk` lên GitHub Releases + Artifacts khi push lên `main`/`master`.

## Quy ước & Ràng buộc an toàn
- `com.android.settings` (Cài đặt) được khóa bảo vệ tuyệt đối, không cho phép ẩn hoặc xóa.
- Gói của chính ứng dụng (`com.aistudio.appforge.vkmrqp`) không thể tự xóa hoặc tự ẩn.
- Giao diện người dùng hoàn toàn bằng tiếng Việt, hỗ trợ Dark/Light theme.

## Lỗi đã sửa & Tối ưu
- Gỡ bỏ & Ẩn triệt để → Chạy `am force-stop` + `pm disable-user --user 0` trước khi `pm uninstall --user 0` (và fallback `pm hide`/`pm suspend` nếu ROM chặn gỡ) để app biến mất hoàn toàn khỏi màn hình chính và không tự chạy lại; khi khôi phục chạy kèm `pm unhide` + `pm unsuspend` + `pm enable`.
- Ép mặc định đa phiên bản Android → Bổ sung `pm set-default-browser`, `telecom set-default-dialer`, `settings put secure sms_default_application` cùng `cmd role` và `set-home-activity`.
- Bố cục responsive không tràn/hở → Ô tìm kiếm full-width, badge dùng `FlowRow`, `PrivilegeStatusPill` giới hạn chiều rộng, `ScaffoldDefaults.contentWindowInsets`, kiểm tra binary `su` trước khi chạy process.
- Lỗi Shizuku báo chưa cấp quyền/kết nối sau khi đã cấp quyền → Bổ sung transaction ID `16777114` cho `destroy()` trong `IShellService.aidl`, thêm `constructor(Context)` + `@Keep` trong `ShellUserService` & `proguard-rules.pro`, dùng `Mutex` chống race-condition khi `bindUserService` trên Main thread, thêm `ON_RESUME` tự đồng bộ trạng thái khi quay lại từ app Shizuku, bổ sung fallback `Shizuku.newProcess` và hiển thị đúng trạng thái trên `PrivilegeStatusPill`.
- Bảo vệ trình duyệt/WebView & mở link trên máy ảo → Thêm `<queries>` cho `ACTION_VIEW` (`http`/`https`) trong `AndroidManifest.xml`; bảo vệ `com.android.webview` & `com.google.android.webview` trong `CRITICAL_CORE_PACKAGES`; chỉ ẩn trình duyệt đối thủ khi app đích thực sự là trình duyệt; thêm nút "Mở ứng dụng" + báo lỗi cụ thể trong `AppDetailBottomSheet` và hộp thoại hướng dẫn cài Shizuku offline khi thiết bị không có trình duyệt trong `PrivilegeScreen`.

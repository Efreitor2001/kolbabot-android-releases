# Models use explicit JSONObject fields; no reflection-based serialization.
# WorkManager supplies consumer rules for ListenableWorker names/constructors.
# Firebase supplies ComponentRegistrar and generated configuration resource rules.
# Huawei Push/AAID/OpenDevice AARs supply their own entity consumer rules.
# Activities and messaging services are retained by their manifest declarations.
# Do not add package-wide -keep/-dontwarn rules or disable R8 optimization here.

# Huawei OS extensions are absent from the public Android SDK. Network Kit
# probes them with ReflectionUtils.checkCompatible or catches Throwable and
# falls back to standard Android/java.io implementations on other devices.
-dontwarn android.telephony.HwTelephonyManager
-dontwarn com.huawei.android.os.BuildEx$VERSION
-dontwarn com.huawei.libcore.io.ExternalStorageFile
-dontwarn com.huawei.libcore.io.ExternalStorageFileInputStream
-dontwarn com.huawei.libcore.io.ExternalStorageFileOutputStream
-dontwarn com.huawei.libcore.io.ExternalStorageRandomAccessFile

# The optional full HiAnalytics SDK is not used by this application. HMS
# HianalyticsExist probes its presence with Class.forName; HMSBIInitializer and
# HiAnalyticsUtils use the bundled hatool path when it is absent. Network Kit's
# HianalyticsHelper also catches its absence before using the HMS fallback.
-dontwarn com.huawei.hianalytics.process.HiAnalyticsConfig
-dontwarn com.huawei.hianalytics.process.HiAnalyticsConfig$Builder
-dontwarn com.huawei.hianalytics.process.HiAnalyticsInstance
-dontwarn com.huawei.hianalytics.process.HiAnalyticsInstance$Builder
-dontwarn com.huawei.hianalytics.process.HiAnalyticsManager
-dontwarn com.huawei.hianalytics.util.HiAnalyticTools

# These private transaction types are serialized by Gson for process recovery.
-keep class cn.ksuser.auth.sdk.PendingTransaction { *; }
-keep class cn.ksuser.auth.sdk.NativeCreated { *; }
-keep class cn.ksuser.auth.sdk.KsuserTokenSet { *; }
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

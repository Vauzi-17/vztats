#include <jni.h>
#include "adrenotools/include/adrenotools/driver.h"

// Returns JNI_TRUE only when /dev/kgsl-3d0 was opened and the KGSL
// SETPROPERTY(PWRCTRL) ioctl was accepted by the kernel. This lets the Kotlin
// layer distinguish "applied" from "silently ignored / unsupported device".

extern "C" JNIEXPORT jboolean JNICALL
Java_com_fartopblu_adrenoturbomode_core_NativeBridge_enableTurbo(JNIEnv*, jobject) {
    return adrenotools_set_turbo(true) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_fartopblu_adrenoturbomode_core_NativeBridge_disableTurbo(JNIEnv*, jobject) {
    return adrenotools_set_turbo(false) ? JNI_TRUE : JNI_FALSE;
}

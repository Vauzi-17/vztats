#include <jni.h>
#include "adrenotools/include/adrenotools/driver.h"


extern "C" JNIEXPORT void JNICALL
Java_com_fartopblu_adrenoturbomode_MainActivity_EnableTurbo(JNIEnv*, jobject) {
    adrenotools_set_turbo(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_fartopblu_adrenoturbomode_MainActivity_DisableTurbo(JNIEnv*, jobject) {
    adrenotools_set_turbo(false);
}
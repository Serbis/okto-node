/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class ru_serbis_okto_node_hardware_NativeApi__ */

#ifndef _Included_ru_serbis_okto_node_hardware_NativeApi__
#define _Included_ru_serbis_okto_node_hardware_NativeApi__
#ifdef __cplusplus
extern "C" {
#endif


/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialOpen
 * Signature: ([BI)I
 */
JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialOpen
  (JNIEnv *, jobject, jbyteArray, jint);

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialFlush
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialFlush
  (JNIEnv *, jobject, jint);

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialClose
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialClose
  (JNIEnv *, jobject, jint);

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialPutchar
 * Signature: (IB)V
 */
JNIEXPORT void JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialPutchar
  (JNIEnv *, jobject, jint, jbyte);

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialPuts
 * Signature: (I[B)V
 */
JNIEXPORT void JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialPuts
  (JNIEnv *, jobject, jint, jbyteArray);

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialDataAvail
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialDataAvail
  (JNIEnv *, jobject, jint);

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialGetchar
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialGetchar
  (JNIEnv *, jobject, jint);

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialReadWsdPacket
 * Signature: (II)[B
 */
JNIEXPORT jbyteArray JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialReadExbPacket
  (JNIEnv *, jobject, jint, jint);

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    unixDomainConnect
 * Signature: ([B)I
 */
JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_unixDomainConnect
  (JNIEnv *, jobject, jbyteArray);

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    unixDomainReadChar
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_unixDomainReadChar
  (JNIEnv *, jobject, jint);

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    unixDomainWrite
 * Signature: (I[B)I
 */
JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_unixDomainWrite
  (JNIEnv *, jobject, jint, jbyteArray);

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    unixDomainClose
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_unixDomainClose
  (JNIEnv *, jobject, jint);

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    unixDomainReadWsdPacket
 * Signature: (II)[B
 */
JNIEXPORT jbyteArray JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_unixDomainReadWsdPacket
  (JNIEnv *, jobject, jint, jint);

#ifdef __cplusplus
}
#endif
#endif
